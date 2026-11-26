package org.pnfolder.pnlibrary.bukkit.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.pointer.Pointers;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
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

class BukkitCommandAdapterTest {
    @Test
    void nativeCallbacksForwardExecutionAliasArgumentsAndSuggestions() {
        RecordingRegistrar registrar = new RecordingRegistrar();
        List<Component> sent = new ArrayList<>();
        List<Component> actionBars = new ArrayList<>();
        BukkitCommandAdapter adapter = new BukkitCommandAdapter(registrar, source -> audience(source, sent, actionBars));
        CommandDefinition definition = CommandDefinition.command("hello", root -> root.aliases("hi"));
        RecordingDispatcher dispatcher = new RecordingDispatcher();
        adapter.register(new Object(), definition, dispatcher);
        CommandSender sender = sender("Alice", Collections.singleton("example.use"));

        registrar.execute.execute(sender, "hi", new String[] {"one", "two"});
        List<String> suggestions = registrar.suggest.suggest(sender, "hi", new String[] {"wo"}).toCompletableFuture().join();
        dispatcher.lastContext.getSender().sendMessage(Component.text("done"));
        dispatcher.lastContext.getSender().sendActionBar(Component.text("status"));

        assertEquals("hi", dispatcher.executionContext.getInvokedAlias());
        assertEquals(Arrays.asList("one", "two"), dispatcher.executionContext.getArguments());
        assertEquals("wo", dispatcher.lastContext.getCurrentInput());
        assertEquals(Collections.singletonList("world"), suggestions);
        assertEquals(Collections.singletonList(Component.text("done")), sent);
        assertEquals(Collections.singletonList(Component.text("status")), actionBars);
        Sound bell = Sound.sound(Key.key("minecraft:block.note_block.bell"), Sound.Source.MASTER, 1f, 1f);
        dispatcher.lastContext.getSender().playSound(bell);
        assertEquals("Alice", AudienceExtensions.name(dispatcher.executionContext.getSender()));
        assertEquals(true, AudienceExtensions.hasPermission(dispatcher.executionContext.getSender(), "example.use"));
    }

    @Test
    void registrationAndAdapterCloseUnregisterEachNativeCommandOnce() throws Exception {
        RecordingRegistrar registrar = new RecordingRegistrar();
        BukkitCommandAdapter adapter = new BukkitCommandAdapter(registrar,
            source -> audience(source, new ArrayList<>(), new ArrayList<>()));
        PlatformCommandRegistration first = adapter.register(new Object(), CommandDefinition.command("first", root -> { }),
            new RecordingDispatcher());
        adapter.register(new Object(), CommandDefinition.command("second", root -> { }), new RecordingDispatcher());

        first.close();
        first.close();
        adapter.close();
        adapter.close();

        assertEquals(2, registrar.unregisterCalls);
    }

    private static final class RecordingDispatcher implements PlatformCommandDispatcher {
        private CommandContext executionContext;
        private CommandContext lastContext;

        @NotNull
        @Override
        public CompletionStage<Void> execute(@NotNull CommandDefinition command, @NotNull CommandContext context) {
            executionContext = context;
            return CompletableFuture.completedFuture(null);
        }

        @NotNull
        @Override
        public CompletionStage<List<String>> suggest(@NotNull CommandDefinition command, @NotNull CommandContext context) {
            lastContext = context;
            return CompletableFuture.completedFuture(Collections.singletonList("world"));
        }
    }

    private static final class RecordingRegistrar implements NativeCommandRegistrar<CommandSender> {
        private NativeCommandExecutor<CommandSender> execute;
        private NativeCommandSuggester<CommandSender> suggest;
        private int unregisterCalls;

        @NotNull
        @Override
        public PlatformCommandRegistration register(@NotNull Object owner, @NotNull CommandDefinition command,
                                                    @NotNull NativeCommandExecutor<CommandSender> execute,
                                                    @NotNull NativeCommandSuggester<CommandSender> suggest) {
            this.execute = execute;
            this.suggest = suggest;
            return () -> unregisterCalls++;
        }
    }

    private static CommandSender sender(String name, Set<String> permissions) {
        return (CommandSender) Proxy.newProxyInstance(CommandSender.class.getClassLoader(),
            new Class<?>[] {CommandSender.class}, (proxy, method, args) -> {
                switch (method.getName()) {
                    case "getName":
                        return name;
                    case "hasPermission":
                        return args != null && permissions.contains(args[0]);
                    default:
                        if (method.getReturnType() == Boolean.TYPE) {
                            return false;
                        }
                        if (method.getReturnType() == Integer.TYPE) {
                            return 0;
                        }
                        return method.getReturnType() == Long.TYPE ? (Object) 0L : null;
                }
            });
    }

    private static Audience audience(CommandSender sender, List<Component> messages, List<Component> actionBars) {
        return new Audience() {
            @NotNull
            @Override
            public Pointers pointers() {
                return AudienceExtensions.senderPointers(sender.getName(), UUID.randomUUID(), sender::hasPermission);
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
}
