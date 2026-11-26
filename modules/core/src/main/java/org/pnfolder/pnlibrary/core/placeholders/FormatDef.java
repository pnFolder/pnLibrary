package org.pnfolder.pnlibrary.core.placeholders;

import org.jetbrains.annotations.NotNull;

/** A format: takes a value of {@code input}. An empty owner is the library. */
public final class FormatDef {
    private final String owner;
    private final String name;
    private final Class<?> input;
    private final FormatApply apply;

    public FormatDef(@NotNull String owner, @NotNull String name, @NotNull Class<?> input, @NotNull FormatApply apply) {
        this.owner = owner;
        this.name = name;
        this.input = input;
        this.apply = apply;
    }

    public @NotNull String getOwner() {
        return owner;
    }

    public @NotNull String getName() {
        return name;
    }

    public @NotNull Class<?> getInput() {
        return input;
    }

    public @NotNull FormatApply getApply() {
        return apply;
    }
}
