package org.pnfolder.pnlibrary.api.updates.backup;

import java.nio.file.Path;
import org.jetbrains.annotations.NotNull;

/**
 * A copy the plugin makes itself, for data the library cannot copy as a file, such as a MySQL database.
 * Before an update the library calls {@link #getExport()} with the file to write; the file is kept with the backup set
 * and is NOT restored by a rollback: the administrator loads it back by hand.
 */
public final class BackupExport {
    private final BackupKind kind;
    private final String name;
    private final String fileName;
    private final Writer export;

    /**
     * Creates the export.
     *
     * @param kind what the exported file holds
     * @param name short name the menu and commands show
     * @param fileName name of the exported file inside the set, without folders
     * @param export writes the data to the file it is given
     */
    public BackupExport(@NotNull BackupKind kind, @NotNull String name, @NotNull String fileName,
                        @NotNull Writer export) {
        new BackupLabel(kind, name);
        if (!isPlainFileName(fileName)) {
            throw new IllegalArgumentException("Export file name must be a plain file name");
        }
        this.kind = kind;
        this.name = name;
        this.fileName = fileName;
        this.export = export;
    }

    private static boolean isPlainFileName(String value) {
        if (value.trim().isEmpty() || value.equals(".") || value.equals("..")) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '/' || c == '\\' || Character.isISOControl(c)) {
                return false;
            }
        }
        return true;
    }

    /** What the exported file holds. */
    @NotNull
    public BackupKind getKind() {
        return kind;
    }

    /** Short name the menu and commands show. */
    @NotNull
    public String getName() {
        return name;
    }

    /** Name of the exported file inside the set, without folders. */
    @NotNull
    public String getFileName() {
        return fileName;
    }

    /** Writes the data to the file it is given. */
    @NotNull
    public Writer getExport() {
        return export;
    }

    /** Writes one exported copy. */
    @FunctionalInterface
    public interface Writer {
        /** Writes the data to {@code file}. */
        void write(@NotNull Path file) throws Exception;
    }
}
