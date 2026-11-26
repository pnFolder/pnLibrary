package org.pnfolder.pnlibrary.api.scenario.requirements.builtin;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.scenario.requirements.Requirement;
import org.pnfolder.pnlibrary.api.scenario.requirements.RequirementContext;
import org.pnfolder.pnlibrary.api.scenario.requirements.Verdict;

import java.util.Objects;

/** Met when the requirement a plugin defined under {@code name} (see {@code ActionService.defineRequirement}) is met. */
public final class RefRequirement implements Requirement {
    private final String name;

    /** Creates it with every setting at its default. */
    public RefRequirement() {
        this("");
    }

    /** Creates it with every setting given. */
    public RefRequirement(@NotNull String name) {
        this.name = Objects.requireNonNull(name, "name");
    }

    /** Name given when the requirement was defined. */
    @NotNull
    public String getName() {
        return name;
    }

    @NotNull
    @Override
    public Verdict test(@NotNull RequirementContext context) {
        Requirement target = context.requirement(name);
        if (target == null) {
            return new Verdict.Unmet("requirement '" + name + "' is not defined");
        }
        return target.test(context);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof RefRequirement)) {
            return false;
        }
        RefRequirement that = (RefRequirement) other;
        return Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    @NotNull
    @Override
    public String toString() {
        return "RefRequirement(name=" + name + ")";
    }
}
