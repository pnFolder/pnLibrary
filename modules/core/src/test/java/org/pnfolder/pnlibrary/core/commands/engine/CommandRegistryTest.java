package org.pnfolder.pnlibrary.core.commands.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.commands.CommandContext;
import org.pnfolder.pnlibrary.api.commands.CommandRegistration;
import org.pnfolder.pnlibrary.api.commands.definition.ArgumentType;
import org.pnfolder.pnlibrary.api.commands.definition.CommandDefinition;

class CommandRegistryTest {
    static CommandContext context(TestCommandSender sender, String... arguments) {
        return new CommandContext(sender, Arrays.asList(arguments), "hello", "");
    }

    static <T> CompletableFuture<T> failed(Throwable error) {
        CompletableFuture<T> future = new CompletableFuture<>();
        future.completeExceptionally(error);
        return future;
    }

    @Test
    @DisplayName("diagnostic snapshots stay coherent during concurrent registration")
    void diagnosticSnapshotsStayCoherentDuringConcurrentRegistration() throws Exception {
        RecordingCommands nativeCommands = new RecordingCommands();
        final CommandRegistry service = new CommandRegistry(nativeCommands, new TestPlatform(nativeCommands));
        final Object owner = new Object();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        final CountDownLatch start = new CountDownLatch(1);
        try {
            Future<?> writer = executor.submit(() -> {
                start.await();
                for (int index = 0; index < 500; index++) {
                    service.register(owner, CommandDefinition.command("temporary", root -> { })).close();
                }
                return null;
            });
            Future<?> reader = executor.submit(() -> {
                start.await();
                for (int index = 0; index < 1000; index++) {
                    Map<String, Object> snapshot = service.diagnosticSnapshot();
                    assertEquals((Object) ((List<?>) snapshot.get("registeredNames")).size(), snapshot.get("registeredCount"));
                }
                return null;
            });
            start.countDown();
            writer.get(10, TimeUnit.SECONDS);
            reader.get(10, TimeUnit.SECONDS);
            assertEquals(0, service.diagnosticSnapshot().get("registeredCount"));
        } finally {
            executor.shutdownNow();
            service.close();
        }
    }

    @Test
    @DisplayName("a failed asynchronous command logs its own exception, not the cause inside it")
    void aFailedAsynchronousCommandLogsItsOwnExceptionNotTheCauseInsideIt() {
        RecordingCommands nativeCommands = new RecordingCommands();
        TestPlatform platform = new TestPlatform(nativeCommands);
        CommandRegistry service = new CommandRegistry(platform.getCommandAdapter(), platform);
        final IllegalStateException failure = new IllegalStateException("command failed", new RuntimeException("root cause"));
        service.register(new Object(), CommandDefinition.command("hello",
                root -> root.executesAsync(context -> CommandRegistryTest.<Void>failed(failure))));

        nativeCommands.dispatcher.execute(nativeCommands.command, context(new TestCommandSender())).toCompletableFuture().join();

        assertEquals(Collections.singletonList(failure), platform.errors);
    }

    @Test
    @DisplayName("builder overload registers a command without manual definition construction")
    void builderOverloadRegistersACommandWithoutManualDefinitionConstruction() {
        RecordingCommands nativeCommands = new RecordingCommands();
        TestPlatform platform = new TestPlatform(nativeCommands);
        CommandRegistry service = new CommandRegistry(platform.getCommandAdapter(), platform);

        CommandRegistration registration = service.register(new Object(), "hello", root -> root.executes(context -> { }));

        assertEquals("hello", registration.getCommand().getName());
        assertFalse(registration.isClosed());
        registration.close();
        assertTrue(registration.isClosed());
    }

    @Test
    @DisplayName("service dispatches a tree leaf and sanitizes predicate failures")
    void serviceDispatchesATreeLeafAndSanitizesPredicateFailures() {
        RecordingCommands nativeCommands = new RecordingCommands();
        TestPlatform platform = new TestPlatform(nativeCommands);
        CommandRegistry service = new CommandRegistry(platform.getCommandAdapter(), platform);
        final String[] selected = new String[1];
        service.register(new Object(), CommandDefinition.command("group", root -> root.argument("group", ArgumentType.string(), group -> {
            group.literal("show", show -> show.executes(context -> selected[0] = context.<String>get("group")));
            group.literal("broken", broken -> {
                broken.availableIf(context -> {
                    throw new IllegalStateException("private predicate");
                });
                broken.executes(context -> { });
            });
        })));
        TestCommandSender sender = new TestCommandSender();

        nativeCommands.dispatcher.execute(nativeCommands.command,
                new CommandContext(sender, Arrays.asList("builders", "show"), "group", "show")).toCompletableFuture().join();
        nativeCommands.dispatcher.execute(nativeCommands.command,
                new CommandContext(sender, Arrays.asList("builders", "broken"), "group", "broken")).toCompletableFuture().join();

        assertEquals("builders", selected[0]);
        assertEquals(Arrays.asList("Не удалось выполнить команду."), sender.texts());
        boolean logged = false;
        for (Throwable error : platform.errors) {
            logged |= "private predicate".equals(error.getMessage());
        }
        assertTrue(logged);
    }

    @Test
    @DisplayName("authorized execution and suggestions reach the portable definition")
    void authorizedExecutionAndSuggestionsReachThePortableDefinition() {
        RecordingCommands nativeCommands = new RecordingCommands();
        CommandRegistry service = new CommandRegistry(new TestPlatform(nativeCommands).getCommandAdapter(), new TestPlatform(nativeCommands));
        final boolean[] executed = new boolean[1];
        service.register(new Object(), CommandDefinition.command("hello", root -> {
            root.permission("example.hello");
            root.executes(context -> executed[0] = true);
            root.suggests(context -> Arrays.asList("world"));
        }));
        CommandContext context = context(new TestCommandSender(new HashSet<>(Arrays.asList("example.hello"))));

        nativeCommands.dispatcher.execute(nativeCommands.command, context).toCompletableFuture().join();
        List<String> suggestions = nativeCommands.dispatcher.suggest(nativeCommands.command, context).toCompletableFuture().join();

        assertTrue(executed[0]);
        assertEquals(Arrays.asList("world"), suggestions);
    }

    @Test
    @DisplayName("permission denial blocks execution and suggestions while configured console bypass works")
    void permissionDenialBlocksExecutionAndSuggestionsWhileConfiguredConsoleBypassWorks() {
        RecordingCommands nativeCommands = new RecordingCommands();
        CommandRegistry service = new CommandRegistry(new TestPlatform(nativeCommands).getCommandAdapter(), new TestPlatform(nativeCommands));
        final int[] executions = new int[1];
        service.register(new Object(), CommandDefinition.command("hello", root -> {
            root.permission("example.hello");
            root.consoleBypassesPermission();
            root.executes(context -> executions[0]++);
            root.suggests(context -> Arrays.asList("secret"));
        }));
        TestCommandSender denied = new TestCommandSender();

        nativeCommands.dispatcher.execute(nativeCommands.command, context(denied)).toCompletableFuture().join();
        List<String> deniedSuggestions = nativeCommands.dispatcher.suggest(nativeCommands.command, context(denied)).toCompletableFuture().join();
        nativeCommands.dispatcher.execute(nativeCommands.command, context(new TestCommandSender(true))).toCompletableFuture().join();

        assertEquals(1, executions[0]);
        assertTrue(deniedSuggestions.isEmpty());
        assertEquals(Arrays.asList("Недостаточно прав для этой команды."), denied.texts());
    }

    @Test
    @DisplayName("a command can replace the default no-permission message with its own text")
    void aCommandCanReplaceTheDefaultNoPermissionMessageWithItsOwnText() {
        RecordingCommands nativeCommands = new RecordingCommands();
        CommandRegistry service = new CommandRegistry(new TestPlatform(nativeCommands).getCommandAdapter(), new TestPlatform(nativeCommands));
        service.register(new Object(), CommandDefinition.command("hello", root -> {
            root.permission("example.hello");
            root.noPermissionMessage("Нужна покупка доступа: /shop buy hello");
            root.executes(context -> { });
        }));
        TestCommandSender denied = new TestCommandSender();

        nativeCommands.dispatcher.execute(nativeCommands.command, context(denied)).toCompletableFuture().join();

        assertEquals(Arrays.asList("Нужна покупка доступа: /shop buy hello"), denied.texts());
    }

    @Test
    @DisplayName("name and alias collisions fail before native registration")
    void nameAndAliasCollisionsFailBeforeNativeRegistration() {
        final RecordingCommands nativeCommands = new RecordingCommands();
        final CommandRegistry service = new CommandRegistry(new TestPlatform(nativeCommands).getCommandAdapter(), new TestPlatform(nativeCommands));
        service.register(new Object(), CommandDefinition.command("first", root -> root.aliases("shared")));

        assertThrows(IllegalArgumentException.class, () -> service.register(new Object(), CommandDefinition.command("SHARED", root -> { })));
        assertThrows(IllegalArgumentException.class,
                () -> service.register(new Object(), CommandDefinition.command("second", root -> root.aliases("FIRST"))));
        assertEquals(1, nativeCommands.registerCalls);
    }

    @Test
    @DisplayName("failed native registration rolls reservations back")
    void failedNativeRegistrationRollsReservationsBack() {
        final RecordingCommands nativeCommands = new RecordingCommands();
        nativeCommands.failNextRegistration = true;
        final CommandRegistry service = new CommandRegistry(new TestPlatform(nativeCommands).getCommandAdapter(), new TestPlatform(nativeCommands));

        assertThrows(IllegalStateException.class,
                () -> service.register(new Object(), CommandDefinition.command("hello", root -> root.aliases("hi"))));

        service.register(new Object(), CommandDefinition.command("HI", root -> { }));
        assertEquals(2, nativeCommands.registerCalls);
    }

    @Test
    @DisplayName("handler failures are logged and sanitized")
    void handlerFailuresAreLoggedAndSanitized() {
        RecordingCommands nativeCommands = new RecordingCommands();
        TestPlatform platform = new TestPlatform(nativeCommands);
        CommandRegistry service = new CommandRegistry(platform.getCommandAdapter(), platform);
        service.register(new Object(), CommandDefinition.command("hello", root -> {
            root.executesAsync(context -> CommandRegistryTest.<Void>failed(new IllegalStateException("private detail")));
            root.suggestsAsync(context -> CommandRegistryTest.<List<String>>failed(new IllegalArgumentException("private suggestion")));
        }));
        TestCommandSender sender = new TestCommandSender();

        nativeCommands.dispatcher.execute(nativeCommands.command, context(sender)).toCompletableFuture().join();
        List<String> suggestions = nativeCommands.dispatcher.suggest(nativeCommands.command, context(sender)).toCompletableFuture().join();

        assertEquals(Arrays.asList("Не удалось выполнить команду."), sender.texts());
        assertTrue(suggestions.isEmpty());
        assertEquals(2, platform.errors.size());
        List<String> messages = new ArrayList<>();
        for (Throwable error : platform.errors) {
            messages.add(error.getMessage());
        }
        assertTrue(messages.contains("private detail"));
        assertTrue(messages.contains("private suggestion"));
    }

    @Test
    @DisplayName("registration owner and service closing unregister natively once")
    void registrationOwnerAndServiceClosingUnregisterNativelyOnce() {
        final RecordingCommands nativeCommands = new RecordingCommands();
        final CommandRegistry service = new CommandRegistry(new TestPlatform(nativeCommands).getCommandAdapter(), new TestPlatform(nativeCommands));
        Object firstOwner = new Object();
        CommandRegistration registration = service.register(firstOwner, CommandDefinition.command("first", root -> { }));
        service.register(firstOwner, CommandDefinition.command("second", root -> { }));
        service.register(new Object(), CommandDefinition.command("third", root -> { }));

        registration.close();
        registration.close();
        service.unregisterOwner(firstOwner);
        assertEquals(new HashSet<>(Arrays.asList("first", "second")), nativeCommands.unregisteredNames);
        assertFalse(nativeCommands.unregisteredNames.contains("third"));
        service.close();
        service.close();

        assertTrue(registration.isClosed());
        assertEquals(3, nativeCommands.unregisterCalls);
        assertEquals(1, nativeCommands.closeCalls);
        assertThrows(IllegalStateException.class, () -> service.register(new Object(), CommandDefinition.command("late", root -> { })));
    }

    @Test
    @DisplayName("one native close failure does not leak remaining registrations")
    void oneNativeCloseFailureDoesNotLeakRemainingRegistrations() {
        RecordingCommands nativeCommands = new RecordingCommands();
        nativeCommands.failingCloseName = "second";
        TestPlatform platform = new TestPlatform(nativeCommands);
        CommandRegistry service = new CommandRegistry(platform.getCommandAdapter(), platform);
        service.register(new Object(), CommandDefinition.command("first", root -> { }));
        service.register(new Object(), CommandDefinition.command("second", root -> { }));
        service.register(new Object(), CommandDefinition.command("third", root -> { }));

        service.close();

        assertEquals(new HashSet<>(Arrays.asList("first", "second", "third")), nativeCommands.unregisteredNames);
        assertEquals(1, nativeCommands.closeCalls);
        boolean logged = false;
        for (Throwable error : platform.errors) {
            logged |= "close failed".equals(error.getMessage());
        }
        assertTrue(logged);
    }
}
