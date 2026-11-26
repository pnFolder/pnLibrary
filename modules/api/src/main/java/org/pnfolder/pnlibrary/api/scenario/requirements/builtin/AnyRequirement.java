package org.pnfolder.pnlibrary.api.scenario.requirements.builtin;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.scenario.requirements.Requirement;
import org.pnfolder.pnlibrary.api.scenario.requirements.RequirementContext;
import org.pnfolder.pnlibrary.api.scenario.requirements.Verdict;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Met when at least one requirement of {@code requirements} is met; an empty list is never met. */
public final class AnyRequirement implements Requirement {
    private final List<Requirement> requirements;

    /** Creates it with every setting at its default. */
    public AnyRequirement() {
        this(Collections.<Requirement>emptyList());
    }

    /** Creates it with every setting given. */
    public AnyRequirement(@NotNull List<Requirement> requirements) {
        this.requirements = requirements;
    }

    /** The requirements of which one must hold. */
    @NotNull
    public List<Requirement> getRequirements() {
        return requirements;
    }

    @NotNull
    @Override
    public Verdict test(@NotNull RequirementContext context) {
        List<String> reasons = new ArrayList<>();
        for (Requirement requirement : requirements) {
            Verdict verdict = requirement.test(context);
            if (verdict instanceof Verdict.Unmet) {
                reasons.add(((Verdict.Unmet) verdict).getReason());
            } else {
                return verdict;
            }
        }
        return new Verdict.Unmet(reasons.isEmpty() ? "nothing to choose from" : String.join(" / ", reasons));
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof AnyRequirement)) {
            return false;
        }
        AnyRequirement that = (AnyRequirement) other;
        return Objects.equals(requirements, that.requirements);
    }

    @Override
    public int hashCode() {
        return Objects.hash(requirements);
    }

    @NotNull
    @Override
    public String toString() {
        return "AnyRequirement(requirements=" + requirements + ")";
    }
}
