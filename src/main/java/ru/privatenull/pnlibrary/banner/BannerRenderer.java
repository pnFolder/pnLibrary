package ru.privatenull.pnlibrary.banner;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.ChatColor;
import org.bukkit.command.ConsoleCommandSender;

/** Формирует весь консольный вывод для {@link PluginBanner}. */
final class BannerRenderer {

    private static final String INDENT = "          ";
    private static final int CAT_WIDTH = 7;

    private final PluginBanner.Data data;

    BannerRenderer(PluginBanner.Data data) {
        this.data = data;
    }

    PluginBanner.Data data() {
        return data;
    }

    void enable() {
        PluginBanner.Status status = data.overallStatus();
        ConsoleCommandSender console = console();

        header(console, face(status), status.color(), true);
        command(console, "start");
        blank(console);
        entries(console);
        blank(console);
        result(console, status);
        blank(console);
    }

    void disable() {
        PluginBanner.Status status = data.overallStatus();
        ConsoleCommandSender console = console();

        header(console, face(status), status.color(), true);
        command(console, "stop");
        blank(console);
        entries(console);
        blank(console);
        shutdownResult(console, status);
        blank(console);
    }

    void error(String message) {
        ConsoleCommandSender console = console();

        header(console, "x.x", ChatColor.RED, false);
        command(console, "start");
        blank(console);
        send(console, ChatColor.RED + INDENT + "✕ " + ChatColor.WHITE + "Критическая ошибка");
        send(console, ChatColor.DARK_GRAY + "            └ " + ChatColor.RED + message);
        blank(console);
    }

    void update(String latestVersion, String releaseUrl) {
        ConsoleCommandSender console = console();

        header(console, "O.O", ChatColor.AQUA, false);
        command(console, "update");
        blank(console);
        send(console, versionLine("Текущая версия", 4, ChatColor.RED, version()));
        send(console, versionLine("Новая версия", 6, ChatColor.GREEN, latestVersion));
        blank(console);
        send(console, ChatColor.AQUA + INDENT + "↑ " + ChatColor.WHITE + "Доступно обновление");
        send(console, ChatColor.DARK_GRAY + "            └ " + ChatColor.AQUA + releaseUrl);
        blank(console);
    }

    void upToDate() {
        ConsoleCommandSender console = console();
        header(console, "^.^", ChatColor.GREEN, false);
        command(console, "update");
        send(console, ChatColor.DARK_GRAY + INDENT + "> "
                + ChatColor.WHITE + "Обновления    "
                + ChatColor.GREEN + "[ OK ]"
                + ChatColor.GRAY + " • Установлена актуальная версия");
        blank(console);
    }

    void updateDownloaded(String latestVersion, String stagedFile) {
        ConsoleCommandSender console = console();
        header(console, "^.^", ChatColor.GREEN, false);
        command(console, "update");
        blank(console);
        send(console, ChatColor.DARK_GRAY + INDENT + "> "
                + ChatColor.WHITE + "Автообновление    "
                + ChatColor.GREEN + "[ OK ]");
        send(console, ChatColor.DARK_GRAY + "            └ " + ChatColor.GRAY
                + "Версия " + normalizeVersion(latestVersion)
                + " сохранена как " + stagedFile);
        send(console, ChatColor.DARK_GRAY + "              " + ChatColor.GRAY
                + "Будет установлена после перезапуска сервера");
        blank(console);
    }

    void updateDownloadFailure(String reason) {
        ConsoleCommandSender console = console();
        header(console, "o.o", ChatColor.GOLD, false);
        command(console, "update");
        blank(console);
        send(console, ChatColor.DARK_GRAY + INDENT + "> "
                + ChatColor.WHITE + "Автообновление    "
                + ChatColor.GOLD + "[ WARN ]");
        send(console, ChatColor.DARK_GRAY + "            └ " + ChatColor.GRAY + reason);
        blank(console);
    }

    void updateCheckFailure(String reason) {
        ConsoleCommandSender console = console();
        header(console, "o.o", ChatColor.GOLD, false);
        command(console, "update");
        blank(console);
        send(console, ChatColor.DARK_GRAY + INDENT + "> "
                + ChatColor.WHITE + "Проверка обновлений    "
                + ChatColor.GOLD + "[ WARN ]");
        send(console, ChatColor.DARK_GRAY + "            └ " + ChatColor.GRAY + reason);
        blank(console);
    }

    private void header(
            ConsoleCommandSender console,
            String face,
            ChatColor faceColor,
            boolean includeAuthors
        ) {
        blank(console);
        cat(console, faceColor, "/\\_/\\", field("Плагин", ChatColor.AQUA, pluginName()));
        cat(console, faceColor, "( " + face + " )", field("Версия", ChatColor.WHITE, version()));
        cat(console, faceColor, "> ^ <", field("Проект", ChatColor.AQUA, data.developer()));
        if (includeAuthors) {
            send(console, ChatColor.DARK_GRAY + INDENT + field("Автор", ChatColor.WHITE, authors()));
        }
        blank(console);
    }

    private void command(ConsoleCommandSender console, String command) {
        send(console, ChatColor.DARK_GRAY + INDENT + "$ " + ChatColor.WHITE + pluginName()
                + ChatColor.GRAY + " --" + command);
    }

    private void cat(ConsoleCommandSender console, ChatColor color, String art, String text) {
        send(console, color + padRight(art, CAT_WIDTH) + "   " + text);
    }

    private String field(String label, ChatColor valueColor, String value) {
        return ChatColor.GRAY + padRight(label, 6) + ": " + valueColor + value;
    }

    private void entries(ConsoleCommandSender console) {
        int longestName = data.entries().keySet().stream()
                .mapToInt(String::length)
                .max()
                .orElse(0);

        for (Map.Entry<String, PluginBanner.Entry> item : data.entries().entrySet()) {
            String component = item.getKey();
            PluginBanner.Entry entry = item.getValue();
            String padding = " ".repeat(Math.max(1, longestName - component.length() + 5));

            send(console, ChatColor.DARK_GRAY + INDENT + "> " + ChatColor.WHITE + component
                    + padding + entry.status().color() + "[ " + entry.status().displayName() + " ]");
            if (entry.details() != null) {
                send(console, ChatColor.DARK_GRAY + "            └ " + ChatColor.GRAY + entry.details());
            }
        }
    }

    private void result(ConsoleCommandSender console, PluginBanner.Status status) {
        String line = switch (status) {
            case OK -> ChatColor.GREEN + INDENT + "✓ " + ChatColor.WHITE + "Плагин готов к работе";
            case WARN -> ChatColor.GOLD + INDENT + "⚠ " + ChatColor.WHITE + "Запущен с предупреждениями";
            case FAIL -> ChatColor.RED + INDENT + "✕ " + ChatColor.WHITE + "Запуск завершён с ошибками";
            case SKIP -> ChatColor.GRAY + INDENT + "○ " + ChatColor.WHITE + "Запуск завершён";
        };
        send(console, line);
    }

    private void shutdownResult(ConsoleCommandSender console, PluginBanner.Status status) {
        String line = switch (status) {
            case OK -> ChatColor.GREEN + INDENT + "✓ " + ChatColor.WHITE + "Плагин корректно остановлен";
            case WARN -> ChatColor.GOLD + INDENT + "⚠ " + ChatColor.WHITE + "Остановлен с предупреждениями";
            case FAIL -> ChatColor.RED + INDENT + "✕ " + ChatColor.WHITE + "При завершении возникли ошибки";
            case SKIP -> ChatColor.GRAY + INDENT + "○ " + ChatColor.WHITE + "Завершение пропущено";
        };
        send(console, line);
    }

    private String authors() {
        List<String> authors = data.plugin().getPluginMeta().getAuthors();
        if (authors.isEmpty()) return "не указан";
        return String.join(", ", authors);
    }

    private String versionLine(String label, int spaces, ChatColor color, String value) {
        return ChatColor.DARK_GRAY + INDENT + "> " + ChatColor.WHITE + label
                + ChatColor.GRAY + " ".repeat(spaces) + "[ " + color
                + normalizeVersion(value) + ChatColor.GRAY + " ]";
    }

    private String pluginName() {
        return data.plugin().getPluginMeta().getName().toLowerCase(Locale.ROOT);
    }

    private String version() {
        return data.plugin().getPluginMeta().getVersion();
    }

    private ConsoleCommandSender console() {
        return data.plugin().getServer().getConsoleSender();
    }

    private static String face(PluginBanner.Status status) {
        return switch (status) {
            case OK -> "^.^";
            case WARN -> "o.o";
            case FAIL -> "x.x";
            case SKIP -> "-.-";
        };
    }

    private static void blank(ConsoleCommandSender console) {
        send(console, "");
    }

    private static void send(ConsoleCommandSender console, String message) {
        console.sendMessage(message);
    }

    private static String padRight(String value, int width) {
        if (value.length() >= width) return value;
        return value + " ".repeat(width - value.length());
    }

    private static String normalizeVersion(String version) {
        if (version == null) return "";
        String normalized = version.trim();
        return normalized.startsWith("v") || normalized.startsWith("V")
                ? normalized.substring(1)
                : normalized;
    }
}
