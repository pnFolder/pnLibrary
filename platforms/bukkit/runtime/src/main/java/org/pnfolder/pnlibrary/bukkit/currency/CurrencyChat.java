package org.pnfolder.pnlibrary.bukkit.currency;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;
import org.pnfolder.pnlibrary.api.chat.ChatKit;
import org.pnfolder.pnlibrary.api.chat.ChatPalette;
import org.pnfolder.pnlibrary.api.chat.ChatReader;
import org.pnfolder.pnlibrary.api.chat.CommandHint;
import org.pnfolder.pnlibrary.api.chat.notice.ChatNotice;
import org.pnfolder.pnlibrary.api.chat.notice.ChatTone;
import org.pnfolder.pnlibrary.api.currency.Currency;
import org.pnfolder.pnlibrary.api.currency.CurrencyCommandSettings;
import org.pnfolder.pnlibrary.api.currency.CurrencyResult;
import org.pnfolder.pnlibrary.api.currency.CurrencyTransactionType;
import org.pnfolder.pnlibrary.bukkit.commands.BukkitCommandSender;

/** What {@code /pn money} says: notices, the configured templates of a currency, the help page. */
final class CurrencyChat {
    static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());
    private static final CurrencyCommandSettings DEFAULT_MESSAGES = new CurrencyCommandSettings();
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private CurrencyChat() {
    }

    static CurrencyCommandSettings settings(Currency currency) {
        CurrencyCommandSettings configured = currency.extension(CurrencyCommandSettings.class);
        return configured != null ? configured : DEFAULT_MESSAGES;
    }

    static ChatKit kit(BukkitCommandSender sender) {
        return new ChatKit(ChatReader.of(sender));
    }

    static LegacyComponentSerializer legacy() {
        return LEGACY;
    }

    /**
     * A configured template of {@code currency} after the notice mark of {@code tone}. The template keeps its own
     * legacy colors; the mark is a sibling, so uncolored template text does not take the tone's color.
     */
    static void configured(BukkitCommandSender sender, Currency currency, ChatTone tone, String template) {
        configured(sender, currency, tone, template, Collections.emptyMap());
    }

    static void configured(BukkitCommandSender sender, Currency currency, ChatTone tone, String template,
                           Map<String, String> values) {
        String prefix = settings(currency).getPrefix().replace("{currency}", currency.getDescriptor().getDisplayName());
        String body = template;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            body = body.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        Component message = LEGACY.deserialize(ChatColor.translateAlternateColorCodes('&', prefix + body));
        sender.sendMessage(Component.empty().append(kit(sender).mark(tone)).append(message));
    }

    static boolean notify(BukkitCommandSender sender, ChatTone tone, String text) {
        return notify(sender, tone, text, null);
    }

    static boolean notify(BukkitCommandSender sender, ChatTone tone, String text, String detail) {
        for (Component line : kit(sender).notice(new ChatNotice(tone, text, detail))) {
            sender.sendMessage(line);
        }
        return true;
    }

    static boolean error(BukkitCommandSender sender, String text) {
        return error(sender, text, null);
    }

    static boolean error(BukkitCommandSender sender, String text, String detail) {
        return notify(sender, ChatTone.FAILURE, text, detail);
    }

    static void showResult(BukkitCommandSender sender, Currency currency, CurrencyResult result, BigDecimal amount) {
        CurrencyCommandSettings settings = settings(currency);
        if (result.isSuccess()) {
            configured(sender, currency, ChatTone.SUCCESS, settings.getSuccess(),
                Collections.singletonMap("amount", currency.format(amount)));
        } else {
            String error = result.getMessage() != null ? result.getMessage()
                : result.getReason() != null ? result.getReason().name() : result.getStatus().name();
            configured(sender, currency, ChatTone.FAILURE, settings.getFailure(), Collections.singletonMap("error", error));
        }
    }

    static String typeLabel(CurrencyTransactionType type) {
        switch (type) {
            case CREDIT:
                return "начисление";
            case DEBIT:
                return "списание";
            case TRANSFER:
                return "перевод";
            case SET_BALANCE:
                return "установка баланса";
            default:
                return "сброс";
        }
    }

    static boolean usage(BukkitCommandSender sender, Currency currency) {
        return notify(sender, ChatTone.WARNING, "Укажите действие.",
            "/pn money " + currency.getKey() + " <balance|add|take|set|reset|pay|history>");
    }

    static boolean help(BukkitCommandSender sender) {
        ChatKit kit = kit(sender);
        List<Component> hints = kit.commandHints(
            new CommandHint("/pn money list", "список валют"),
            new CommandHint("/pn money <валюта> balance [игрок]", "баланс"),
            new CommandHint("/pn money <валюта> pay <игрок> <сумма>", "перевести игроку"),
            new CommandHint("/pn money <валюта> history [игрок]", "последние операции"),
            new CommandHint("/pn money <валюта> add <игрок> <сумма>", "начислить"),
            new CommandHint("/pn money <валюта> take <игрок> <сумма>", "списать"),
            new CommandHint("/pn money <валюта> set <игрок> <сумма>", "установить баланс"),
            new CommandHint("/pn money <валюта> reset <игрок>", "обнулить баланс"),
            new CommandHint("/pn money confirm <код>", "подтвердить операцию из консоли"),
            new CommandHint("/pn money cancel <код>", "отменить операцию из консоли"));
        Component intro = kit.note(kit.getReader().isInteractive()
            ? "Нажмите на команду, чтобы вставить её в чат." : "Доступные действия зависят от прав.");
        List<Component> page = new java.util.ArrayList<>();
        page.add(kit.header("Валюты", kit.text("помощь", ChatPalette.LABEL)));
        page.add(intro);
        page.add(kit.blank());
        page.addAll(hints);
        for (Component line : kit.framed(page)) {
            sender.sendMessage(line);
        }
        return true;
    }
}
