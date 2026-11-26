package org.pnfolder.pnlibrary.core.journal.view;

import java.util.Objects;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;

/** One trace line as colored text, and whether it is a frame of the JDK, the server or a library. */
public final class TraceLine {
    private final Component component;
    private final boolean framework;

    TraceLine(@NotNull Component component, boolean framework) {
        this.component = Objects.requireNonNull(component, "component");
        this.framework = framework;
    }

    public @NotNull Component getComponent() {
        return component;
    }

    public boolean isFramework() {
        return framework;
    }
}
