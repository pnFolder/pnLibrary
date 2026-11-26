package org.pnfolder.pnlibrary.velocity.commands;

import com.velocitypowered.api.command.CommandSource;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.pointer.Pointers;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.audience.AudienceExtensions;
import org.pnfolder.pnlibrary.api.commands.CommandContext;
import org.pnfolder.pnlibrary.api.commands.definition.CommandDefinition;
import org.pnfolder.pnlibrary.spi.commands.NativeCommandExecutor;
import org.pnfolder.pnlibrary.spi.commands.NativeCommandRegistrar;
import org.pnfolder.pnlibrary.spi.commands.NativeCommandSuggester;
import org.pnfolder.pnlibrary.spi.commands.PlatformCommandDispatcher;
import org.pnfolder.pnlibrary.spi.commands.PlatformCommandRegistration;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.pnfolder.pnlibrary.api.commands.definition.CommandDefinition.command;

/** Checks command registration, execution, tab completion and cleanup on Velocity. */
class VelocityCommandAdapterTest {

    @Test
    void executionAndAsynchronousSuggestionsAreTransportedWithoutBlocking() {
        RecordingRegistrar registrar = new RecordingRegistrar();
        List<Component> sent = new ArrayList<>();
        List<Component> actionBars = new ArrayList<>();
        CommandSource source = source("VelocityConsole", Collections.singleton("example.use"), sent);
        VelocityCommandAdapter adapter = new VelocityCommandAdapter(registrar,
                nativeSource -> audience(nativeSource, nativeSource == source, sent, actionBars));
        CompletableFuture<List<String>> pending = new CompletableFuture<>();
        RecordingDispatcher dispatcher = new RecordingDispatcher(pending);
        Object owner = new Object();

        adapter.register(owner, command("hello", builder -> builder.aliases("hi")), dispatcher);
        assertSame(owner, registrar.owner);
        registrar.execute.execute(source, "hi", new String[] {"one"});
        CompletionStage<List<String>> returned = registrar.suggest.suggest(source, "hi", new String[] {"w"});
        assertFalse(returned.toCompletableFuture().isDone());
        pending.complete(Collections.singletonList("world"));

        assertEquals(Collections.singletonList("world"), returned.toCompletableFuture().join());
        assertEquals("hi", dispatcher.execution.getInvokedAlias());
        assertEquals(Collections.singletonList("one"), dispatcher.execution.getArguments());
        assertEquals("w", dispatcher.suggestion.getCurrentInput());
        assertTrue(AudienceExtensions.isConsole(dispatcher.execution.getSender()));
        assertTrue(AudienceExtensions.hasPermission(dispatcher.execution.getSender(), "example.use"));
        dispatcher.execution.getSender().sendMessage(Component.text("done"));
        dispatcher.execution.getSender().sendActionBar(Component.text("status"));
        assertEquals(Collections.singletonList(Component.text("done")), sent);
        assertEquals(Collections.singletonList(Component.text("status")), actionBars);
    }

    @Test
    void adapterClosesEveryNativeRegistrationExactlyOnce() {
        RecordingRegistrar registrar = new RecordingRegistrar();
        VelocityCommandAdapter adapter = new VelocityCommandAdapter(registrar,
                nativeSource -> audience(nativeSource, false, new ArrayList<Component>(), new ArrayList<Component>()));

        PlatformCommandRegistration first = adapter.register(new Object(), command("first", builder -> { }),
                new RecordingDispatcher(CompletableFuture.completedFuture(Collections.<String>emptyList())));
        adapter.register(new Object(), command("second", builder -> { }),
                new RecordingDispatcher(CompletableFuture.completedFuture(Collections.<String>emptyList())));
        first.close();
        first.close();
        adapter.close();

        assertEquals(2, registrar.unregisterCalls);
    }

    private static final class RecordingDispatcher implements PlatformCommandDispatcher {
        private final CompletableFuture<List<String>> suggestions;
        CommandContext execution;
        CommandContext suggestion;

        RecordingDispatcher(CompletableFuture<List<String>> suggestions) {
            this.suggestions = suggestions;
        }

        @NotNull
        @Override
        public CompletionStage<Void> execute(@NotNull CommandDefinition command, @NotNull CommandContext context) {
            execution = context;
            return CompletableFuture.completedFuture(null);
        }

        @NotNull
        @Override
        public CompletionStage<List<String>> suggest(@NotNull CommandDefinition command,
                                                     @NotNull CommandContext context) {
            suggestion = context;
            return suggestions;
        }
    }

    private static final class RecordingRegistrar implements NativeCommandRegistrar<CommandSource> {
        Object owner;
        NativeCommandExecutor<CommandSource> execute;
        NativeCommandSuggester<CommandSource> suggest;
        int unregisterCalls;

        @NotNull
        @Override
        public PlatformCommandRegistration register(
                @NotNull Object owner,
                @NotNull CommandDefinition command,
                @NotNull NativeCommandExecutor<CommandSource> execute,
                @NotNull NativeCommandSuggester<CommandSource> suggest) {
            this.owner = owner;
            this.execute = execute;
            this.suggest = suggest;
            return () -> unregisterCalls++;
        }
    }

    private static CommandSource source(String name, Set<String> permissions, List<Component> messages) {
        Set<String> granted = new HashSet<>(permissions);
        return (CommandSource) Proxy.newProxyInstance(CommandSource.class.getClassLoader(),
                new Class<?>[] {CommandSource.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "toString":
                            return name;
                        case "hasPermission":
                            return granted.contains(args[0]);
                        case "sendMessage":
                            messages.add((Component) args[0]);
                            return null;
                        default:
                            return defaultValue(method.getReturnType());
                    }
                });
    }

    private static Audience audience(CommandSource source, boolean console, List<Component> messages,
                                     List<Component> actionBars) {
        return new Audience() {
            @NotNull
            @Override
            public Pointers pointers() {
                return AudienceExtensions.senderPointers(source.toString(), console ? null : UUID.randomUUID(),
                        source::hasPermission);
            }

            @Override
            public void sendMessage(@NotNull Component text) {
                messages.add(text);
            }

            @Override
            public void sendActionBar(@NotNull Component text) {
                actionBars.add(text);
            }

            @Override
            public void playSound(@NotNull Sound sound) {
            }
        };
    }

    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) {
            return false;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        return null;
    }
}
