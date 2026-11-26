package org.pnfolder.pnlibrary.bukkit.currency;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.pnfolder.pnlibrary.api.audience.AudienceExtensions;
import org.pnfolder.pnlibrary.api.chat.ChatKit;
import org.pnfolder.pnlibrary.api.chat.ChatReader;
import org.pnfolder.pnlibrary.api.chat.notice.ChatNotice;
import org.pnfolder.pnlibrary.api.chat.notice.ChatTone;
import org.pnfolder.pnlibrary.api.currency.Currency;
import org.pnfolder.pnlibrary.api.currency.CurrencyAccount;
import org.pnfolder.pnlibrary.api.currency.CurrencyActor;
import org.pnfolder.pnlibrary.api.currency.CurrencyConfirmationMode;
import org.pnfolder.pnlibrary.api.currency.CurrencyHistoryQuery;
import org.pnfolder.pnlibrary.api.currency.CurrencyLedger;
import org.pnfolder.pnlibrary.api.currency.CurrencyProviderRegistry;
import org.pnfolder.pnlibrary.api.currency.CurrencyRejectReason;
import org.pnfolder.pnlibrary.api.currency.CurrencyResult;
import org.pnfolder.pnlibrary.api.currency.CurrencyTransaction;
import org.pnfolder.pnlibrary.api.currency.CurrencyTransactionRequest;
import org.pnfolder.pnlibrary.api.currency.CurrencyTransactionType;
import org.pnfolder.pnlibrary.bukkit.commands.BukkitCommandSender;
import org.pnfolder.pnlibrary.bukkit.tasks.ServerScheduler;

/** What each {@code /pn money} action does to the currency and what it answers: balances, changes, payments, history, confirmation. */
final class CurrencyOperations {
    private final CurrencyProviderRegistry currencies;
    private final CurrencyConfirmationStore confirmations;
    private final ServerScheduler scheduler;

    CurrencyOperations(CurrencyProviderRegistry currencies, CurrencyConfirmationStore confirmations,
                       ServerScheduler scheduler) {
        this.currencies = currencies;
        this.confirmations = confirmations;
        this.scheduler = scheduler;
    }

    /** Every available currency: its name, then the key used in commands. */
    boolean list(BukkitCommandSender sender) {
        List<Currency> all = currencies.all();
        if (all.isEmpty()) {
            return CurrencyChat.notify(sender, ChatTone.INFO, "Сейчас нет доступных валют.");
        }
        CurrencyPages.list(sender, all);
        return true;
    }

    boolean balance(BukkitCommandSender sender, Currency currency, List<String> args) {
        UUID target;
        if (args.isEmpty()) {
            target = sender.getNative() instanceof Player ? ((Player) sender.getNative()).getUniqueId() : null;
        } else {
            target = playerId(args.get(0));
        }
        if (target == null) {
            return CurrencyChat.error(sender, "Укажите игрока.");
        }
        complete(sender, currency.balance(target), amount -> CurrencyChat.configured(sender, currency, ChatTone.INFO,
            CurrencyChat.settings(currency).getBalance(), Collections.singletonMap("balance", currency.format(amount))));
        return true;
    }

    boolean mutate(BukkitCommandSender sender, Currency currency, List<String> args, String operation,
                   BiFunction<UUID, BigDecimal, CompletionStage<CurrencyResult>> call) {
        if (args.size() < 2) {
            return CurrencyChat.error(sender, "Использование: /pn money " + currency.getKey() + " " + operation
                + " <игрок> <сумма>");
        }
        UUID target = playerId(args.get(0));
        if (target == null) {
            return CurrencyChat.error(sender, "Игрок не найден: " + args.get(0));
        }
        BigDecimal amount = decimal(args.get(1));
        if (amount == null) {
            return CurrencyChat.error(sender, "Неверная сумма: " + args.get(1));
        }
        CurrencyLedger ledger = currency.extension(CurrencyLedger.class);
        CurrencyTransactionType type = operation.equals("add") ? CurrencyTransactionType.CREDIT
            : operation.equals("take") ? CurrencyTransactionType.DEBIT : CurrencyTransactionType.SET_BALANCE;
        Supplier<CompletionStage<CurrencyResult>> execute = () -> {
            if (ledger == null) {
                return call.apply(target, amount);
            }
            boolean debit = type == CurrencyTransactionType.DEBIT;
            return ledger.transact(new CurrencyTransactionRequest(type, amount,
                debit ? new CurrencyAccount(target) : null, debit ? null : new CurrencyAccount(target),
                actor(sender), "pnlibrary.command", "Command /pn money " + operation, approval(sender), null))
                .thenApply(CurrencyOperations::transactionResult);
        };
        return queueOrRun(sender, currency, operation + " " + args.get(0) + " " + amount.toPlainString(), execute,
            result -> CurrencyChat.showResult(sender, currency, result, amount));
    }

    boolean reset(BukkitCommandSender sender, Currency currency, List<String> args) {
        UUID target = args.isEmpty() ? null : playerId(args.get(0));
        if (target == null) {
            return CurrencyChat.error(sender, "Укажите игрока.");
        }
        CurrencyLedger ledger = currency.extension(CurrencyLedger.class);
        Supplier<CompletionStage<CurrencyResult>> execute = () -> {
            if (ledger == null) {
                return currency.reset(target);
            }
            return ledger.transact(new CurrencyTransactionRequest(CurrencyTransactionType.RESET, BigDecimal.ZERO, null,
                new CurrencyAccount(target), actor(sender), "pnlibrary.command", "Command /pn money reset",
                approval(sender), null)).thenApply(CurrencyOperations::transactionResult);
        };
        return queueOrRun(sender, currency, "reset " + args.get(0), execute,
            result -> CurrencyChat.showResult(sender, currency, result, BigDecimal.ZERO));
    }

    boolean pay(BukkitCommandSender sender, Currency currency, List<String> args) {
        if (!(sender.getNative() instanceof Player)) {
            return CurrencyChat.error(sender, "Переводить валюту может только игрок.");
        }
        Player source = (Player) sender.getNative();
        if (args.size() < 2) {
            return CurrencyChat.error(sender, "Использование: /pn money " + currency.getKey() + " pay <игрок> <сумма>");
        }
        UUID target = playerId(args.get(0));
        if (target == null) {
            return CurrencyChat.error(sender, "Игрок не найден: " + args.get(0));
        }
        BigDecimal amount = decimal(args.get(1));
        if (amount == null) {
            return CurrencyChat.error(sender, "Неверная сумма: " + args.get(1));
        }
        CurrencyLedger ledger = currency.extension(CurrencyLedger.class);
        CompletionStage<CurrencyResult> stage = ledger == null
            ? currency.transfer(source.getUniqueId(), target, amount)
            : ledger.transact(new CurrencyTransactionRequest(CurrencyTransactionType.TRANSFER, amount,
                new CurrencyAccount(source.getUniqueId()), new CurrencyAccount(target),
                CurrencyActor.player(source.getUniqueId()), "pnlibrary.command", "Player payment",
                Collections.emptyMap(), null)).thenApply(CurrencyOperations::transactionResult);
        complete(sender, stage, result -> CurrencyChat.showResult(sender, currency, result, amount));
        return true;
    }

    /** The last ten transactions: time, kind, amount, who requested it and why. */
    boolean history(BukkitCommandSender sender, Currency currency, List<String> args) {
        CurrencyLedger ledger = currency.extension(CurrencyLedger.class);
        if (ledger == null) {
            return CurrencyChat.error(sender, "Валюта " + currency.getKey() + " не ведёт историю операций.");
        }
        UUID account = args.isEmpty() ? null : playerId(args.get(0));
        complete(sender, ledger.history(CurrencyHistoryQuery.builder().account(account).limit(10).build()),
            page -> CurrencyPages.history(sender, currency, page));
        return true;
    }

    private static CurrencyResult transactionResult(CurrencyTransaction transaction) {
        switch (transaction.getStatus()) {
            case COMMITTED:
                return CurrencyResult.success(
                    transaction.getSourceBalanceBefore() != null ? transaction.getSourceBalanceBefore() : transaction.getTargetBalanceBefore(),
                    transaction.getSourceBalanceAfter() != null ? transaction.getSourceBalanceAfter() : transaction.getTargetBalanceAfter());
            case REJECTED:
                return CurrencyResult.rejected(CurrencyRejectReason.PROVIDER_REJECTED, transaction.getFailure());
            default:
                return CurrencyResult.failed(new IllegalStateException(
                    transaction.getFailure() != null ? transaction.getFailure() : "Транзакция не выполнена"));
        }
    }

    private static Map<String, String> approval(BukkitCommandSender sender) {
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("requestedBy", AudienceExtensions.name(sender));
        metadata.put("approval", "console");
        return metadata;
    }

    private static CurrencyActor actor(BukkitCommandSender sender) {
        return sender.getNative() instanceof Player
            ? CurrencyActor.player(((Player) sender.getNative()).getUniqueId()) : CurrencyActor.server();
    }

    private <T> void complete(BukkitCommandSender sender, CompletionStage<T> stage, Consumer<T> success) {
        stage.whenComplete((value, failure) -> scheduler.nextTick(() -> {
            if (failure == null) {
                success.accept(value);
            } else {
                Throwable cause = failure.getCause();
                CurrencyChat.error(sender, "Операция с валютой не выполнена.",
                    cause != null ? cause.getMessage() : failure.getMessage());
            }
        }));
    }

    private boolean queueOrRun(BukkitCommandSender sender, Currency currency, String description,
                               Supplier<CompletionStage<CurrencyResult>> execute, Consumer<CurrencyResult> completed) {
        org.pnfolder.pnlibrary.api.currency.CurrencyCommandSettings settings = CurrencyChat.settings(currency);
        if (settings.getConfirmationMode() == CurrencyConfirmationMode.NONE) {
            complete(sender, execute.get(), completed);
            return true;
        }
        CurrencyConfirmationStore.PendingOperation operation = confirmations.add(
            (int) settings.getConfirmationTimeout().getSeconds(), currency.getKey().toString(), description,
            AudienceExtensions.name(sender), execute, completed);
        if (operation == null) {
            return CurrencyChat.error(sender, "Слишком много операций с валютой ждут подтверждения.");
        }
        announceToConsole(sender, currency, description, settings.getConfirmationTimeout().getSeconds(), operation.token);
        return CurrencyChat.notify(sender, ChatTone.PROGRESS, "Операция ждёт подтверждения в консоли.",
            "Код: " + operation.token);
    }

    private static void announceToConsole(BukkitCommandSender sender, Currency currency, String description,
                                          long seconds, String token) {
        ChatKit kit = new ChatKit(ChatReader.CONSOLE);
        String request = currency.getKey() + " · " + description + " · запросил " + AudienceExtensions.name(sender);
        String confirm = "Подтвердите в течение " + seconds + " с: /pn money confirm " + token;
        List<Component> lines = new ArrayList<>(kit.notice(
            new ChatNotice(ChatTone.WARNING, "Операция с валютой ждёт подтверждения: " + token, request)));
        lines.addAll(kit.notice(new ChatNotice(ChatTone.INFO, confirm)));
        for (Component line : lines) {
            Bukkit.getConsoleSender().sendMessage(CurrencyChat.legacy().serialize(line));
        }
    }

    boolean confirm(BukkitCommandSender sender, String token) {
        if (!(sender.getNative() instanceof ConsoleCommandSender)) {
            return CurrencyChat.error(sender, "Подтверждать операции с валютой может только консоль сервера.");
        }
        if (token == null) {
            return CurrencyChat.error(sender, "Использование: /pn money confirm <код>");
        }
        CurrencyConfirmationStore.PendingOperation operation = confirmations.take(token);
        if (operation == null) {
            return CurrencyChat.error(sender, "Код подтверждения неверен или устарел.");
        }
        if (operation.expiresAt < System.currentTimeMillis()) {
            return CurrencyChat.error(sender, "Срок действия кода подтверждения истёк.");
        }
        CurrencyChat.notify(sender, ChatTone.SUCCESS, "Подтверждено: " + operation.currency + " · " + operation.description,
            "Запросил " + operation.requestedBy);
        complete(sender, operation.execute.get(), operation.completed);
        return true;
    }

    boolean cancel(BukkitCommandSender sender, String token) {
        if (!(sender.getNative() instanceof ConsoleCommandSender)) {
            return CurrencyChat.error(sender, "Отменять операции с валютой может только консоль сервера.");
        }
        if (token == null || confirmations.take(token) == null) {
            return CurrencyChat.error(sender, "Код подтверждения неверен или устарел.");
        }
        return CurrencyChat.notify(sender, ChatTone.SUCCESS,
            "Операция с валютой отменена: " + token.toUpperCase(java.util.Locale.ROOT));
    }

    private static BigDecimal decimal(String text) {
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException error) {
            return null;
        }
    }

    private static UUID playerId(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online.getUniqueId();
        }
        for (OfflinePlayer offline : Bukkit.getOfflinePlayers()) {
            if (offline.getName() != null && offline.getName().equalsIgnoreCase(name)) {
                return offline.getUniqueId();
            }
        }
        return null;
    }
}
