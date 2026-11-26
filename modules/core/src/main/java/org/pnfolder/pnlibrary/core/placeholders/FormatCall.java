package org.pnfolder.pnlibrary.core.placeholders;

import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** One {@code |name:argument,argument} of an address. */
public final class FormatCall {
    private final String name;
    private final List<String> arguments;
    private final String owner;

    public FormatCall(@NotNull String name, @NotNull List<String> arguments) {
        this(name, arguments, null);
    }

    public FormatCall(@NotNull String name, @NotNull List<String> arguments, @Nullable String owner) {
        this.name = name;
        this.arguments = Collections.unmodifiableList(arguments);
        this.owner = owner;
    }

    public @NotNull String getName() {
        return name;
    }

    public @NotNull List<String> getArguments() {
        return arguments;
    }

    public @Nullable String getOwner() {
        return owner;
    }
}
