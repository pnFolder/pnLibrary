package org.pnfolder.pnlibrary.core.menu.screens;

import org.jetbrains.annotations.NotNull;

/** Plugin list filter in the updates screen. */
public enum Filter {
    ALL("Плагины"),
    UPDATES("Обновления"),
    PROBLEMS("Проблемы");

    private final String title;

    Filter(String title) {
        this.title = title;
    }

    public @NotNull String getTitle() {
        return title;
    }
}
