package org.pnfolder.pnlibrary.updates.history.backup;

import java.util.Locale;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.updates.backup.BackupKind;
import org.pnfolder.pnlibrary.api.updates.backup.UpdateBackupItem;

/** How a saved file is shown to an administrator, such as {@code example: База данных «Игроки» · 12.5 МБ}. */
public final class BackupItemText {
    private static final long KB = 1024L;
    private static final long MB = 1024L * 1024;

    private BackupItemText() {
    }

    /** The name of a kind of saved file. */
    @NotNull
    public static String kind(@NotNull BackupKind kind) {
        switch (kind) {
            case CONFIGURATION:
                return "Конфигурация";
            case DATABASE:
                return "База данных";
            case PLAYER_DATA:
                return "Данные игроков";
            default:
                return "Файл";
        }
    }

    /** {@code bytes} as bytes, KB or MB. */
    @NotNull
    public static String size(long bytes) {
        if (bytes >= MB) {
            return String.format(Locale.ROOT, "%.1f МБ", (double) bytes / MB);
        }
        if (bytes >= KB) {
            return String.format(Locale.ROOT, "%.1f КБ", (double) bytes / KB);
        }
        return bytes + " Б";
    }

    /** One line about {@code item}: product, kind, name and size. */
    @NotNull
    public static String describe(@NotNull UpdateBackupItem item) {
        String kind = kind(item.getKind());
        String title = isBlank(item.getName()) ? kind : kind + " «" + item.getName() + "»";
        return item.getProduct() + ": " + title + " · " + size(item.getBytes());
    }

    private static boolean isBlank(String text) {
        for (int i = 0; i < text.length(); i++) {
            if (!Character.isWhitespace(text.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
