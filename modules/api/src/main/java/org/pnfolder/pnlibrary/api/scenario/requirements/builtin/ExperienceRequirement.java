package org.pnfolder.pnlibrary.api.scenario.requirements.builtin;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.scenario.requirements.Requirement;
import org.pnfolder.pnlibrary.api.scenario.requirements.RequirementContext;
import org.pnfolder.pnlibrary.api.scenario.requirements.Verdict;

import java.util.Objects;

/** Met when the player has at least {@code amount} experience points, or levels with {@code level}. */
public final class ExperienceRequirement implements Requirement {
    private final int amount;
    private final boolean level;

    /** Creates it with every setting at its default. */
    public ExperienceRequirement() {
        this(0, false);
    }

    /** Creates it with the first 1 setting; the rest keep their defaults. */
    public ExperienceRequirement(int amount) {
        this(amount, false);
    }

    /** Creates it with every setting given. */
    public ExperienceRequirement(int amount, boolean level) {
        if (amount < 0) {
            throw new IllegalArgumentException("Required experience must not be negative");
        }
        this.amount = amount;
        this.level = level;
    }

    /** Required experience, not negative. */
    public int getAmount() {
        return amount;
    }

    /** Whether {@code amount} counts levels instead of points. */
    public boolean isLevel() {
        return level;
    }

    @NotNull
    @Override
    public Verdict test(@NotNull RequirementContext context) {
        Double have = level ? context.getPlayer().experienceLevel() : context.getPlayer().experiencePoints();
        if (have == null) {
            return new Verdict.Unmet("experience is not available here");
        }
        return Verdict.of(have >= amount, "need " + amount + " " + (level ? "levels" : "experience"));
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ExperienceRequirement)) {
            return false;
        }
        ExperienceRequirement that = (ExperienceRequirement) other;
        return amount == that.amount
                && level == that.level;
    }

    @Override
    public int hashCode() {
        return Objects.hash(amount, level);
    }

    @NotNull
    @Override
    public String toString() {
        return "ExperienceRequirement(amount=" + amount + ", level=" + level + ")";
    }
}
