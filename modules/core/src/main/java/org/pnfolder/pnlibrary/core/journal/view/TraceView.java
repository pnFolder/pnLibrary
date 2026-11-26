package org.pnfolder.pnlibrary.core.journal.view;

import java.util.Objects;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/** A colored stack trace and whether some of its lines were folded or cut away. */
public final class TraceView {
    private final List<Component> lines;
    private final boolean trimmed;

    TraceView(@NotNull List<Component> lines, boolean trimmed) {
        this.lines = Objects.requireNonNull(lines, "lines");
        this.trimmed = trimmed;
    }

    public @NotNull List<Component> getLines() {
        return lines;
    }

    public boolean isTrimmed() {
        return trimmed;
    }
}
