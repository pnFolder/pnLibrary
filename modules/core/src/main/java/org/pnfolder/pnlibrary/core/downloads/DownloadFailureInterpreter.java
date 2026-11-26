package org.pnfolder.pnlibrary.core.downloads;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;

/** Turns download failures into Russian explanations for the console card. */
public final class DownloadFailureInterpreter {
    private static final Pattern PLUGIN_MISMATCH = Pattern.compile("скачанный JAR объявляет (.+), ожидался (.+)");
    private static final Pattern VERSION_MISMATCH =
        Pattern.compile("версия скачанного (.+) ([^ ]+) не соответствует требованию (.+)");

    private DownloadFailureInterpreter() {
    }

    /** Explains {@code error} to the administrator; an unknown failure keeps its text as the reason. */
    public static @NotNull DownloadFailureDetails explain(@NotNull Throwable error) {
        String raw = error.getMessage() == null ? "" : error.getMessage().trim();
        Matcher plugin = PLUGIN_MISMATCH.matcher(raw);
        if (plugin.matches()) {
            return pluginMismatch(plugin.group(1), plugin.group(2));
        }
        Matcher version = VERSION_MISMATCH.matcher(raw);
        if (version.matches()) {
            return versionMismatch(version.group(1), version.group(2), version.group(3));
        }
        String reason = raw.trim().isEmpty() ? "Не удалось подготовить файл зависимости" : raw;
        return new DownloadFailureDetails(
            "не удалось подготовить файл",
            reason,
            "Скачай зависимость вручную и перезапусти сервер",
            "Передай разработчику полный текст причины: " + reason);
    }

    private static DownloadFailureDetails pluginMismatch(String actual, String expected) {
        return new DownloadFailureDetails(
            "плагин не прошёл проверку",
            "В скачанном файле указано имя " + actual + ", а ожидалось " + expected,
            "Скачай плагин " + expected + " самостоятельно и перезапусти сервер",
            "Проверь идентификатор зависимости " + expected + " и ссылку на JAR. Сообщи разработчику, что ссылка скачивает "
                + actual);
    }

    private static DownloadFailureDetails versionMismatch(String plugin, String actual, String required) {
        return new DownloadFailureDetails(
            "версия плагина не подходит",
            "Установлена версия " + actual + ", а требуется " + required + " или выше",
            "Скачай " + plugin + " версии " + required + " или выше и перезапусти сервер",
            "Проверь минимальную версию " + required + " и ссылку на релиз. Сообщи разработчику, что ссылка отдаёт версию "
                + actual);
    }
}
