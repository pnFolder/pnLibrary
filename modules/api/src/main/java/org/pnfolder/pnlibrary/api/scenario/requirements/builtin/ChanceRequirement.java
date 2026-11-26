package org.pnfolder.pnlibrary.api.scenario.requirements.builtin;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.scenario.requirements.Requirement;
import org.pnfolder.pnlibrary.api.scenario.requirements.RequirementContext;
import org.pnfolder.pnlibrary.api.scenario.requirements.Verdict;

import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Met by chance.
 *
 * @throws IllegalArgumentException when the probability is outside {@code 0.0..1.0}
 */
public final class ChanceRequirement implements Requirement {
    private final double probability;

    /** Creates it with every setting at its default. */
    public ChanceRequirement() {
        this(0.5);
    }

    /** Creates it with every setting given. */
    public ChanceRequirement(double probability) {
        if (!(probability >= 0.0 && probability <= 1.0)) {
            throw new IllegalArgumentException("Chance must be from 0.0 to 1.0");
        }
        this.probability = probability;
    }

    /** From {@code 0.0} (never) to {@code 1.0} (always). */
    public double getProbability() {
        return probability;
    }

    @NotNull
    @Override
    public Verdict test(@NotNull RequirementContext context) {
        return Verdict.of(ThreadLocalRandom.current().nextDouble() < probability, "bad luck");
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ChanceRequirement)) {
            return false;
        }
        ChanceRequirement that = (ChanceRequirement) other;
        return Double.compare(probability, that.probability) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(probability);
    }

    @NotNull
    @Override
    public String toString() {
        return "ChanceRequirement(probability=" + probability + ")";
    }
}
