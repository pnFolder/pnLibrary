package org.pnfolder.pnlibrary.api.scenario.requirements.builtin;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.scenario.requirements.Requirement;
import org.pnfolder.pnlibrary.api.scenario.requirements.RequirementContext;
import org.pnfolder.pnlibrary.api.scenario.requirements.Verdict;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Met when every requirement of {@code requirements} is met; the first one that is not decides the reason. */
public final class AllRequirement implements Requirement {
    private final List<Requirement> requirements;

    /** Creates it with every setting at its default. */
    public AllRequirement() {
        this(Collections.<Requirement>emptyList());
    }

    /** Creates it with every setting given. */
    public AllRequirement(@NotNull List<Requirement> requirements) {
        this.requirements = requirements;
    }

    /** The requirements that all must hold. */
    @NotNull
    public List<Requirement> getRequirements() {
        return requirements;
    }

    @NotNull
    @Override
    public Verdict test(@NotNull RequirementContext context) {
        for (Requirement requirement : requirements) {
            Verdict verdict = requirement.test(context);
            if (verdict instanceof Verdict.Unmet) {
                return verdict;
            }
        }
        return Verdict.Met.INSTANCE;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof AllRequirement)) {
            return false;
        }
        AllRequirement that = (AllRequirement) other;
        return Objects.equals(requirements, that.requirements);
    }

    @Override
    public int hashCode() {
        return Objects.hash(requirements);
    }

    @NotNull
    @Override
    public String toString() {
        return "AllRequirement(requirements=" + requirements + ")";
    }
}
