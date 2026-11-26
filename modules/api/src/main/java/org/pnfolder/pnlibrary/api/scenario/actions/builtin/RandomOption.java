package org.pnfolder.pnlibrary.api.scenario.actions.builtin;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.config.ConfigKey;
import org.pnfolder.pnlibrary.api.scenario.actions.Action;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** One choice of a {@link RandomAction}. */
public final class RandomOption {
    private final double weight;
    @ConfigKey("do")
    private final List<Action> steps;

    /** Creates it with every setting at its default. */
    public RandomOption() {
        this(1.0, Collections.<Action>emptyList());
    }

    /** Creates it with the first 1 setting; the rest keep their defaults. */
    public RandomOption(double weight) {
        this(weight, Collections.<Action>emptyList());
    }

    /** Creates it with every setting given. */
    public RandomOption(double weight, @NotNull List<Action> steps) {
        if (!(weight > 0)) {
            throw new IllegalArgumentException("A weight must be positive");
        }
        this.weight = weight;
        this.steps = steps;
    }

    /** How often this choice comes up compared with the others; positive. */
    public double getWeight() {
        return weight;
    }

    /** What runs when it is chosen. */
    @NotNull
    public List<Action> getSteps() {
        return steps;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof RandomOption)) {
            return false;
        }
        RandomOption that = (RandomOption) other;
        return Double.compare(weight, that.weight) == 0
                && Objects.equals(steps, that.steps);
    }

    @Override
    public int hashCode() {
        return Objects.hash(weight, steps);
    }

    @NotNull
    @Override
    public String toString() {
        return "RandomOption(weight=" + weight + ", steps=" + steps + ")";
    }
}
