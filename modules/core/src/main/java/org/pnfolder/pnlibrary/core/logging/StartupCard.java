package org.pnfolder.pnlibrary.core.logging;

import net.kyori.adventure.text.format.NamedTextColor;
import org.pnfolder.pnlibrary.api.logging.LogLevel;
import org.pnfolder.pnlibrary.api.runtime.PnLibraryIdentity;
import org.pnfolder.pnlibrary.core.text.ConsoleStyle;
import org.pnfolder.pnlibrary.spi.platform.diagnostics.PlatformSnapshot;
import org.pnfolder.pnlibrary.spi.platform.plugin.PluginSnapshot;

/**
 * The look of one startup card: colours, the product facts and the blocks printed in order. It keeps its own
 * established console look: yellow brand, gray labels, dark rules.
 */
final class StartupCard {
    private static final String RULE = "          " + repeat('\u2500', 47);

    private final StartupBox box;
    private final BoxEnvironment env;
    private final BoxKind kind;
    private final PluginSnapshot ownerMetadata;
    private final String accent = ConsoleStyle.color(NamedTextColor.YELLOW);
    private final String success = ConsoleStyle.color(NamedTextColor.GREEN);
    private final String warning = ConsoleStyle.color(NamedTextColor.GOLD);
    private final String danger = ConsoleStyle.color(NamedTextColor.RED);
    private final String text = ConsoleStyle.color(NamedTextColor.WHITE);
    private final String hint = ConsoleStyle.color(NamedTextColor.GRAY);
    private final String soft = ConsoleStyle.color(NamedTextColor.DARK_GRAY);
    private final String bold = ConsoleStyle.BOLD;
    private final String stateColor;
    private final String product;

    StartupCard(StartupBox box) {
        this.box = box;
        this.env = box.environment();
        this.kind = box.kind();
        this.ownerMetadata = env.ownership.ownerMetadata(box.owner());
        this.stateColor = kind == BoxKind.ENABLED ? success : kind == BoxKind.DISABLED ? danger : accent;
        this.product = productName();
    }

    private String productName() {
        if (box.explicitName() != null) {
            return box.explicitName();
        }
        if (ownerMetadata != null) {
            return ownerMetadata.getName();
        }
        return box.title().split("\\s+", 2)[0];
    }

    private void line(String value) {
        env.logging.console(box.owner(), value);
    }

    void header() {
        String stateLabel = kind == BoxKind.ENABLED ? "ПЛАГИН ВКЛЮЧЁН"
            : kind == BoxKind.DISABLED ? "ПЛАГИН ВЫКЛЮЧЁН" : "СООБЩЕНИЕ";
        PlatformSnapshot environment = env.diagnostics.snapshot();
        line("");
        line(accent + "          ━━━━━━━━━━━ " + bold
            + "PNFOLDER PLUGIN" + accent + " ━━━━━━━━━━━");
        line(stateColor + " /\\_/\\");
        line(stateColor + "( " + face() + " )     " + text + bold + product + " " + soft + "› " + accent + "pnFolder");
        line(stateColor + " > ^ <      " + stateColor + "● " + bold + stateLabel);
        line("");
        line(soft + "            ┌ " + hint + "Версия       " + text + version());
        line(soft + "            ├ " + hint + "Автор        " + text + authors());
        line(soft + "            ├ " + hint + "Ядро         " + text + environment.getName() + " "
            + environment.getVersion());
        line(soft + "            ├ " + hint + "Java         " + text + environment.getJava().getVersion());
        line(soft + "            └ " + hint + "Библиотека   " + text + "pnLibrary " + soft + "• " + hint
            + env.identity.getId());
        line("");
    }

    private String version() {
        if (box.explicitVersion() != null) {
            return box.explicitVersion();
        }
        return ownerMetadata != null ? ownerMetadata.getVersion() : "неизвестна";
    }

    private String authors() {
        String authors = ownerMetadata == null ? "" : String.join(", ", ownerMetadata.getAuthors());
        return authors.trim().isEmpty() ? "неизвестны" : authors;
    }

    void details() {
        String sectionTitle = kind == BoxKind.ENABLED ? "СОСТОЯНИЕ СИСТЕМЫ"
            : kind == BoxKind.DISABLED ? "ЗАВЕРШЕНИЕ РАБОТЫ" : "ПОДРОБНОСТИ";
        line(soft + "          ─────────── " + text + bold
            + sectionTitle + soft + " ───────────");
        line("");
        int longestLabel = 0;
        for (BoxRow row : box.rows()) {
            longestLabel = Math.max(longestLabel, row.label.length());
        }
        for (BoxRow row : box.rows()) {
            line(soft + "          " + symbolOf(row.status) + " " + text + pad(row.label, longestLabel) + "  "
                + colorOf(row.status) + bold + "[ " + row.status + " ]");
            if (!row.detail.trim().isEmpty()) {
                line(soft + "            └ " + hint + row.detail);
            }
            if (row.error != null) {
                env.capture.log(box.owner(), LogLevel.ERROR, box.title() + " — " + row.label, row.error);
            }
        }
        line("");
        line(soft + RULE);
    }

    void result() {
        String resultText = kind == BoxKind.ENABLED ? product + " успешно включён"
            : kind == BoxKind.DISABLED ? product + " корректно выключен" : product + " — готово";
        String resultDetail = kind == BoxKind.ENABLED
            ? "Все основные системы готовы. Плагин работает в штатном режиме."
            : kind == BoxKind.DISABLED ? "Все зарегистрированные ресурсы освобождены. До следующего запуска."
            : "Все строки сообщения выведены одним блоком.";
        String resultSymbol = kind == BoxKind.ENABLED ? "✓" : kind == BoxKind.DISABLED ? "■" : "◆";
        line(stateColor + "          " + resultSymbol + " " + text + bold + resultText);
        line(hint + "            " + resultDetail);
        line("");
    }

    void support() {
        line(accent + "          ✦ " + bold + "PNFOLDER SUPPORT " + soft + "• " + text + "НА СВЯЗИ");
        line(text + "            Есть вопрос, ошибка, идея или просто нужна помощь?");
        line(hint + "            Пишите по любому поводу — разберёмся, подскажем и поможем.");
        line(hint + "            Нужен новый плагин? Обсудим бесплатную или заказную разработку.");
        line(hint + "            Questions, bugs or ideas? Write to us any time: we are always glad to help.");
        line(accent + "            Discord " + soft + "› " + text + ConsoleStyle.UNDERLINE + PnLibraryIdentity.SUPPORT_URL);
        line(soft + RULE);
        line("");
    }

    private String face() {
        if (kind == BoxKind.DISABLED) {
            return "-.-";
        }
        if (hasStatus("FAIL")) {
            return "x.x";
        }
        return hasStatus("WARN") ? "o.o" : "^.^";
    }

    private boolean hasStatus(String status) {
        for (BoxRow row : box.rows()) {
            if (row.status.equals(status)) {
                return true;
            }
        }
        return false;
    }

    private String colorOf(String status) {
        switch (status) {
            case "OK":
                return success;
            case "WARN":
                return warning;
            case "FAIL":
                return danger;
            default:
                return hint;
        }
    }

    private static String symbolOf(String status) {
        switch (status) {
            case "OK":
                return "◆";
            case "WARN":
                return "▲";
            case "FAIL":
                return "✕";
            default:
                return "◇";
        }
    }

    private static String repeat(char symbol, int count) {
        StringBuilder text = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            text.append(symbol);
        }
        return text.toString();
    }

    private static String pad(String value, int width) {
        StringBuilder padded = new StringBuilder(value);
        while (padded.length() < width) {
            padded.append(' ');
        }
        return padded.toString();
    }
}
