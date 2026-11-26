package org.pnfolder.pnlibrary.api.updates.backup;

import org.jetbrains.annotations.NotNull;

/** The kind and the display name of one backed-up file. A blank name means the library shows the kind. */
public final class BackupLabel {
    private final BackupKind kind;
    private final String name;

    /** Creates a label whose name is blank, so the library shows the {@code kind}. */
    public BackupLabel(@NotNull BackupKind kind) {
        this(kind, "");
    }

    /** Creates a label; {@code name} is at most 64 characters without control characters. */
    public BackupLabel(@NotNull BackupKind kind, @NotNull String name) {
        if (name.length() > 64) {
            throw new IllegalArgumentException("Backup name must be at most 64 characters without control characters");
        }
        for (int i = 0; i < name.length(); i++) {
            if (Character.isISOControl(name.charAt(i))) {
                throw new IllegalArgumentException(
                    "Backup name must be at most 64 characters without control characters");
            }
        }
        this.kind = kind;
        this.name = name;
    }

    /** What the file holds. */
    @NotNull
    public BackupKind getKind() {
        return kind;
    }

    /** Display name; blank when the library shows the kind. */
    @NotNull
    public String getName() {
        return name;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof BackupLabel)) {
            return false;
        }
        BackupLabel that = (BackupLabel) other;
        return kind == that.kind && name.equals(that.name);
    }

    @Override
    public int hashCode() {
        return 31 * kind.hashCode() + name.hashCode();
    }

    @Override
    public String toString() {
        return "BackupLabel(kind=" + kind + ", name=" + name + ")";
    }
}
