package org.pnfolder.pnlibrary.core.menu.style;

import java.util.Objects;
import org.jetbrains.annotations.NotNull;

/** One step size of a number: the label on the button and the amount in the unit the field keeps. */
public final class NumberStep {
    private final String label;
    private final double amount;

    public NumberStep(@NotNull String label, double amount) {
        this.label = Objects.requireNonNull(label, "label");
        this.amount = amount;
    }

    public @NotNull String getLabel() {
        return label;
    }

    public double getAmount() {
        return amount;
    }
}
