package org.pnfolder.pnlibrary.api.scenario.requirements.builtin;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.scenario.requirements.Requirement;
import org.pnfolder.pnlibrary.api.scenario.requirements.RequirementContext;
import org.pnfolder.pnlibrary.api.scenario.requirements.Verdict;

import java.util.Objects;

/** Met when {@code requirement} is not. */
public final class NotRequirement implements Requirement {
    private final Requirement requirement;

    /** Creates it with every setting at its default. */
    public NotRequirement() {
        this(null);
    }

    /** Creates it with every setting given. */
    public NotRequirement(@Nullable Requirement requirement) {
        this.requirement = requirement;
    }

    /** The requirement to negate; none is never met. */
    @Nullable
    public Requirement getRequirement() {
        return requirement;
    }

    @NotNull
    @Override
    public Verdict test(@NotNull RequirementContext context) {
        if (requirement == null) {
            return new Verdict.Unmet("nothing to negate");
        }
        return requirement.test(context).isMet() ? new Verdict.Unmet("not allowed") : Verdict.Met.INSTANCE;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof NotRequirement)) {
            return false;
        }
        NotRequirement that = (NotRequirement) other;
        return Objects.equals(requirement, that.requirement);
    }

    @Override
    public int hashCode() {
        return Objects.hash(requirement);
    }

    @NotNull
    @Override
    public String toString() {
        return "NotRequirement(requirement=" + requirement + ")";
    }
}
