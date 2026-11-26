package org.pnfolder.pnlibrary.core.menu.style;

import java.time.Duration;
import org.jetbrains.annotations.NotNull;

/** A length of pause the menu offers for updates. */
public final class HoldOption {
    private final String label;
    private final Duration duration;

    public HoldOption(@NotNull String label, @NotNull Duration duration) {
        this.label = label;
        this.duration = duration;
    }

    public @NotNull String getLabel() {
        return label;
    }

    public @NotNull Duration getDuration() {
        return duration;
    }
}
