package org.pnfolder.pnlibrary.bukkit.currency;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.pnfolder.pnlibrary.api.audience.AudienceExtensions;
import org.pnfolder.pnlibrary.api.chat.ChatKit;
import org.pnfolder.pnlibrary.api.chat.ChatReader;
import org.pnfolder.pnlibrary.api.chat.notice.ChatNotice;
import org.pnfolder.pnlibrary.api.chat.notice.ChatTone;
import org.pnfolder.pnlibrary.api.commands.CommandContext;
import org.pnfolder.pnlibrary.api.commands.definition.ArgumentType;
import org.pnfolder.pnlibrary.api.commands.definition.CommandDefinition;
import org.pnfolder.pnlibrary.api.commands.definition.CommandNodeBuilder;
import org.pnfolder.pnlibrary.api.currency.Currency;
import org.pnfolder.pnlibrary.api.currency.CurrencyProviderRegistry;
import org.pnfolder.pnlibrary.bukkit.commands.BukkitCommandSender;
import org.pnfolder.pnlibrary.bukkit.tasks.ServerScheduler;

/**
 * Builds and executes administrator-facing portable currency commands.
 *
 * <p>Mutations honor provider capabilities and configured console-confirmation requirements. Asynchronous operation
 * results are delivered on the server thread. Replies use the shared chat style; the configured templates of a
 * currency keep their own legacy colors after the notice mark.
 */
public final class CurrencyCommandExecutor {
    /** Operations that also work without a player name (they act on the sender). */
    private static final Set<String> WITHOUT_PLAYER_ALLOWED = new HashSet<>(Arrays.asList("balance", "history"));
    /** Operations that take an amount after the player. */
    private static final Set<String> WITH_AMOUNT = new HashSet<>(Arrays.asList("add", "take", "set", "pay"));
    /** The order is the order of the tab suggestions. */
    private static final List<String> OPERATIONS = Arrays.asList("balance", "add", "take", "set", "reset", "pay", "history");

    private final CurrencyProviderRegistry currencies;
    private final CurrencyConfirmationStore confirmations = new CurrencyConfirmationStore();
    private final CurrencyOperations operations;

    public CurrencyCommandExecutor(CurrencyProviderRegistry currencies, ServerScheduler scheduler) {
        this.currencies = currencies;
        this.operations = new CurrencyOperations(currencies, confirmations, scheduler);
    }

    public CommandDefinition definition() {
        return CommandDefinition.command("money", root -> {
            root.executes(this::executePortable);
            root.literal("list", list -> {
                list.permission("pnlibrary.currency.list");
                list.executes(this::executePortable);
            });
            root.literal("confirm", confirm -> {
                confirm.availableIf(context -> AudienceExtensions.isConsole(context.getSender()));
                confirm.argument("code", ArgumentType.string(), code -> code.executes(this::executePortable));
            });
            root.literal("cancel", cancel -> {
                cancel.availableIf(context -> AudienceExtensions.isConsole(context.getSender()));
                cancel.argument("code", ArgumentType.string(), code -> code.executes(this::executePortable));
            });
            root.argument("currency", ArgumentType.string(), this::currencyNode);
        });
    }

    private void currencyNode(CommandNodeBuilder currency) {
        currency.suggests(context -> currencyKeys());
        for (String operation : OPERATIONS) {
            currency.literal(operation, node -> {
                node.availableIf(context -> operationAllowed(context, operation));
                if (WITHOUT_PLAYER_ALLOWED.contains(operation)) {
                    node.executes(this::executePortable);
                }
                node.argument("player", ArgumentType.string(), player -> {
                    player.suggests(context -> onlinePlayers());
                    if (WITH_AMOUNT.contains(operation)) {
                        player.argument("amount", ArgumentType.decimal(), amount -> amount.executes(this::executePortable));
                    } else {
                        player.executes(this::executePortable);
                    }
                });
            });
        }
    }

    private List<String> currencyKeys() {
        List<String> keys = new ArrayList<>();
        for (Currency currency : currencies.all()) {
            keys.add(currency.getKey().toString());
        }
        return keys;
    }

    private void executePortable(CommandContext context) {
        if (!(context.getSender() instanceof BukkitCommandSender)) {
            for (net.kyori.adventure.text.Component line : new ChatKit(ChatReader.of(context.getSender()))
                .notice(new ChatNotice(ChatTone.FAILURE, "Команда недоступна этому отправителю."))) {
                context.getSender().sendMessage(line);
            }
            return;
        }
        execute((BukkitCommandSender) context.getSender(), context.getArguments());
    }

    private boolean operationAllowed(CommandContext context, String operation) {
        Object name = context.getParsedValues().get("currency");
        if (!(name instanceof String)) {
            return false;
        }
        Currency resolved = currencies.get((String) name);
        if (resolved == null || !CurrencyChat.settings(resolved).isEnabled()) {
            return false;
        }
        return AudienceExtensions.hasPermission(context.getSender(), permission(resolved, operation));
    }

    private static List<String> onlinePlayers() {
        List<String> names = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            names.add(player.getName());
        }
        return names;
    }

    private boolean execute(BukkitCommandSender sender, List<String> args) {
        confirmations.discardExpired();
        if (args.isEmpty() || args.get(0).equalsIgnoreCase("help")) {
            return CurrencyChat.help(sender);
        }
        String first = args.get(0);
        if (first.equalsIgnoreCase("confirm")) {
            return operations.confirm(sender, args.size() > 1 ? args.get(1) : null);
        }
        if (first.equalsIgnoreCase("cancel")) {
            return operations.cancel(sender, args.size() > 1 ? args.get(1) : null);
        }
        if (first.equalsIgnoreCase("list")) {
            return !allowed(sender, "pnlibrary.currency.list") || operations.list(sender);
        }
        Currency currency = currencies.get(first);
        if (currency == null) {
            return CurrencyChat.error(sender, "Валюта не найдена или недоступна: " + first);
        }
        return executeFor(sender, currency, args);
    }

    private boolean executeFor(BukkitCommandSender sender, Currency currency, List<String> args) {
        if (!CurrencyChat.settings(currency).isEnabled()) {
            return CurrencyChat.error(sender, "Команды валюты " + currency.getKey() + " отключены.");
        }
        if (args.size() < 2) {
            return CurrencyChat.usage(sender, currency);
        }
        String operation = args.get(1).toLowerCase(Locale.ROOT);
        if (!allowed(sender, permission(currency, operation))) {
            return true;
        }
        return operate(sender, currency, operation, args.subList(2, args.size()));
    }

    private boolean operate(BukkitCommandSender sender, Currency currency, String operation, List<String> rest) {
        switch (operation) {
            case "balance":
                return operations.balance(sender, currency, rest);
            case "add":
                return operations.mutate(sender, currency, rest, "add", currency::deposit);
            case "take":
                return operations.mutate(sender, currency, rest, "take", currency::withdraw);
            case "set":
                return operations.mutate(sender, currency, rest, "set", currency::setBalance);
            case "reset":
                return operations.reset(sender, currency, rest);
            case "pay":
                return operations.pay(sender, currency, rest);
            case "history":
                return operations.history(sender, currency, rest);
            default:
                return CurrencyChat.usage(sender, currency);
        }
    }

    private boolean allowed(BukkitCommandSender sender, String permission) {
        if (AudienceExtensions.hasPermission(sender, permission)) {
            return true;
        }
        CurrencyChat.error(sender, "Недостаточно прав.", "Нужно право " + permission);
        return false;
    }

    private static String permission(Currency currency, String operation) {
        String prefix = CurrencyChat.settings(currency).getPermissionPrefix();
        if (prefix == null) {
            prefix = "pnlibrary.currency."
                + currency.getKey().toString().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", ".");
        }
        return prefix + "." + operation;
    }
}
