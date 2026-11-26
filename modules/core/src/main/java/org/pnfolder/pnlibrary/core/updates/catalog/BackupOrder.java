package org.pnfolder.pnlibrary.core.updates.catalog;

import org.jetbrains.annotations.NotNull;

/** How the list of rollback sets is ordered. */
public enum BackupOrder {
    /** Newest first. */
    NEWEST("new", "сначала новые"),
    /** Oldest first; sets without a recorded date come last. */
    OLDEST("old", "сначала старые"),
    /** By plugin name, newest first inside one plugin. */
    PLUGIN("plugin", "по плагину");

    private final String word;
    private final String title;

    BackupOrder(String word, String title) {
        this.word = word;
        this.title = title;
    }

    /** The word written in the {@code sort} command. */
    public @NotNull String getWord() {
        return word;
    }

    /** The name shown on the page. */
    public @NotNull String getTitle() {
        return title;
    }
}
