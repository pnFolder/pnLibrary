package org.pnfolder.pnlibrary.bukkit.currency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.pointer.Pointers;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.audience.AudienceExtensions;
import org.pnfolder.pnlibrary.api.commands.CommandContext;
import org.pnfolder.pnlibrary.api.commands.definition.CommandDefinition;
import org.pnfolder.pnlibrary.api.commands.definition.CommandNode;
import org.pnfolder.pnlibrary.api.commands.definition.CommandNodeKind;
import org.pnfolder.pnlibrary.api.currency.Currency;
import org.pnfolder.pnlibrary.api.currency.CurrencyProvider;
import org.pnfolder.pnlibrary.api.currency.CurrencyProviderRegistry;
import org.pnfolder.pnlibrary.api.currency.CurrencyRegistration;
import org.pnfolder.pnlibrary.api.plugin.PluginId;
import org.pnfolder.pnlibrary.bukkit.commands.BukkitCommandSender;
import org.pnfolder.pnlibrary.bukkit.tasks.ServerScheduler;

class BukkitCurrencyCommandTest {
    @Test
    void currencyDefinitionExposesArbitraryContinuationThroughPortableTree() {
        CommandDefinition definition = new CurrencyCommandExecutor(new EmptyCurrencies(),
            new ServerScheduler(proxy(Plugin.class))).definition();

        assertEquals("money", definition.getName());
        List<String> first = new ArrayList<>();
        for (CommandNode child : definition.getRoot().getChildren().subList(0, 3)) {
            first.add(child.getName());
        }
        assertEquals(Arrays.asList("list", "confirm", "cancel"), first);
        CommandNode currency = null;
        for (CommandNode child : definition.getRoot().getChildren()) {
            if (child.getKind() == CommandNodeKind.ARGUMENT) {
                currency = child;
            }
        }
        assertEquals("currency", currency.getName());
        assertEquals(Arrays.asList("balance", "add", "take", "set", "reset", "pay", "history"),
            currency.getChildren().stream().map(CommandNode::getName).collect(Collectors.toList()));
        CommandNode add = currency.getChildren().stream().filter(node -> node.getName().equals("add")).findFirst().get();
        assertTrue(add.getChildren().get(0).getChildren().get(0).isExecutable());
        assertEquals("player", add.getChildren().get(0).getName());
        assertEquals("amount", add.getChildren().get(0).getChildren().get(0).getName());
    }

    @Test
    void helpIsARussianPageOfCommands() throws Exception {
        List<String> messages = run(true, "help");

        assertEquals("", messages.get(0));
        assertEquals("Валюты  помощь", messages.get(1));
        assertTrue(messages.stream().map(String::trim).collect(Collectors.toList()).contains("<<Команды>>"), messages.toString());
        assertTrue(messages.contains("    /pn money list - список валют"), messages.toString());
        assertTrue(messages.stream().anyMatch(line -> line.contains("/pn money <валюта> pay <игрок> <сумма> - перевести игроку")));
    }

    @Test
    void anUnknownCurrencyAndAnEmptyListAreNotices() throws Exception {
        assertEquals(Collections.singletonList("  ! Валюта не найдена или недоступна: test:gems"), run(true, "test:gems"));
        assertEquals(Collections.singletonList("  * Сейчас нет доступных валют."), run(true, "list"));
    }

    @Test
    void missingPermissionNamesThePermission() throws Exception {
        assertEquals(Arrays.asList("  ! Недостаточно прав.", "    Нужно право pnlibrary.currency.list"), run(false, "list"));
    }

    /** Runs {@code /pn money} from the console and returns the replies as plain text. */
    private List<String> run(boolean permitted, String... arguments) throws Exception {
        ConsoleAudience console = new ConsoleAudience(permitted);
        BukkitCommandSender sender = new BukkitCommandSender(proxy(org.bukkit.command.CommandSender.class), console);
        new CurrencyCommandExecutor(new EmptyCurrencies(), new ServerScheduler(proxy(Plugin.class))).definition()
            .getExecution()
            .execute(new CommandContext(sender, Arrays.asList(arguments), "money", arguments[arguments.length - 1]))
            .toCompletableFuture().join();
        List<String> lines = new ArrayList<>();
        for (Component message : console.messages) {
            lines.add(LegacyComponentSerializer.legacySection().serialize(message).replaceAll("§.", ""));
        }
        return lines;
    }

    private static final class ConsoleAudience implements Audience {
        private final boolean permitted;
        private final List<Component> messages = new ArrayList<>();

        ConsoleAudience(boolean permitted) {
            this.permitted = permitted;
        }

        @NotNull
        @Override
        public Pointers pointers() {
            return AudienceExtensions.senderPointers("CONSOLE", permission -> permitted);
        }

        @Override
        public void sendMessage(@NotNull Component text) {
            messages.add(text);
        }

        @Override
        public void sendActionBar(@NotNull Component text) {
            sendMessage(text);
        }

        @Override
        public void playSound(@NotNull Sound sound) {
        }
    }

    private static final class EmptyCurrencies implements CurrencyProviderRegistry {
        @NotNull
        @Override
        public CurrencyRegistration register(@NotNull PluginId owner, @NotNull String name, @NotNull CurrencyProvider provider) {
            throw new IllegalStateException("not used");
        }

        @Override
        public Currency get(@NotNull String reference) {
            return null;
        }

        @NotNull
        @Override
        public List<Currency> all() {
            return Collections.emptyList();
        }
    }

    private static <T> T proxy(Class<T> type) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, (proxy, method, args) -> {
            switch (method.getName()) {
                case "toString":
                    return type.getSimpleName();
                case "hashCode":
                    return System.identityHashCode(proxy);
                case "equals":
                    return false;
                default:
                    if (method.getReturnType() == Boolean.TYPE) {
                        return false;
                    }
                    if (method.getReturnType() == Integer.TYPE) {
                        return 0;
                    }
                    return method.getReturnType() == Long.TYPE ? (Object) 0L : null;
            }
        }));
    }
}
