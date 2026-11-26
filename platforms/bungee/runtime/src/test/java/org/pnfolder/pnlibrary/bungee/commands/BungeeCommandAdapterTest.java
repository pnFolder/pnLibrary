package org.pnfolder.pnlibrary.bungee.commands;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.pointer.Pointers;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.md_5.bungee.api.CommandSender;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.pnfolder.pnlibrary.api.commands.definition.CommandDefinition.command;

/** Checks command registration, execution, tab completion and cleanup on BungeeCord. */
class BungeeCommandAdapterTest {

    @Test
    void nativeCallbacksTranslateSenderExecutionAndSuggestions() {
        RecordingRegistrar registrar = new RecordingRegistrar();
        List<Component> messages = new ArrayList<>();
        List<Component> actionBars = new ArrayList<>();
        CommandSender nativeSender = sender("ProxyConsole", Collections.singleton("example.use"));
        BungeeCommandAdapter adapter = new BungeeCommandAdapter(registrar,
                sender -> audience(sender, sender == nativeSender, messages, actionBars));
        RecordingDispatcher dispatcher = new RecordingDispatcher();

        adapter.register(new Object(), command("hello", builder -> builder.aliases("hi")), dispatcher);
        registrar.execute.execute(nativeSender, "hello", new String[] {"one"});
        List<String> suggestions = registrar.suggest.suggest(nativeSender, "hello", new String[] {"w"})
                .toCompletableFuture().join();
        dispatcher.execution.getSender().sendMessage(Component.text("done"));
        dispatcher.execution.getSender().sendActionBar(Component.text("status"));

        assertEquals("hello", dispatcher.execution.getInvokedAlias());
        assertEquals(Collections.singletonList("one"), dispatcher.execution.getArguments());
        assertEquals("w", dispatcher.suggestion.getCurrentInput());
        assertEquals(Collections.singletonList("world"), suggestions);
        assertTrue(AudienceExtensions.isConsole(dispatcher.execution.getSender()));
        assertTrue(AudienceExtensions.hasPermission(dispatcher.execution.getSender(), "example.use"));
        assertEquals(Collections.singletonList(Component.text("done")), messages);
        assertEquals(Collections.singletonList(Component.text("status")), actionBars);
    }

    @Test
    void closingRegistrationAndAdapterUnregistersEachNativeCommandOnce() {
        RecordingRegistrar registrar = new RecordingRegistrar();
        BungeeCommandAdapter adapter = new BungeeCommandAdapter(registrar,
                sender -> audience(sender, false, new ArrayList<>(), new ArrayList<>()));

        PlatformCommandRegistration first = adapter.register(new Object(), command("first", builder -> { }),
                new RecordingDispatcher());
        adapter.register(new Object(), command("second", builder -> { }), new RecordingDispatcher());
        first.close();
        first.close();
        adapter.close();

        assertEquals(2, registrar.unregisterCalls);
    }

    private static final class RecordingDispatcher implements PlatformCommandDispatcher {
        CommandContext execution;
        CommandContext suggestion;

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
            return CompletableFuture.completedFuture(Collections.singletonList("world"));
        }
    }

    private static final class RecordingRegistrar implements NativeCommandRegistrar<CommandSender> {
        NativeCommandExecutor<CommandSender> execute;
        NativeCommandSuggester<CommandSender> suggest;
        int unregisterCalls;

        @NotNull
        @Override
        public PlatformCommandRegistration register(
                @NotNull Object owner,
                @NotNull CommandDefinition command,
                @NotNull NativeCommandExecutor<CommandSender> execute,
                @NotNull NativeCommandSuggester<CommandSender> suggest) {
            this.execute = execute;
            this.suggest = suggest;
            return () -> unregisterCalls++;
        }
    }

    private static CommandSender sender(String name, Set<String> permissions) {
        Set<String> granted = new HashSet<>(permissions);
        return (CommandSender) Proxy.newProxyInstance(CommandSender.class.getClassLoader(),
                new Class<?>[] {CommandSender.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getName":
                            return name;
                        case "hasPermission":
                            return granted.contains(args[0]);
                        default:
                            return defaultValue(method.getReturnType());
                    }
                });
    }

    private static Audience audience(CommandSender sender, boolean console, List<Component> messages,
                                     List<Component> actionBars) {
        return new Audience() {
            @NotNull
            @Override
            public Pointers pointers() {
                return AudienceExtensions.senderPointers(sender.getName(), console ? null : UUID.randomUUID(),
                        sender::hasPermission);
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
