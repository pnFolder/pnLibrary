package org.pnfolder.pnlibrary.api.scenario.actions.builtin;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.config.ConfigKey;
import org.pnfolder.pnlibrary.api.scenario.actions.Action;
import org.pnfolder.pnlibrary.api.scenario.actions.ActionContext;
import org.pnfolder.pnlibrary.api.scenario.actions.ActionResult;
import org.pnfolder.pnlibrary.api.scenario.requirements.Requirement;
import org.pnfolder.pnlibrary.api.scenario.requirements.Verdict;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Runs {@code thenActions} when {@code requirement} is met, otherwise {@code elseActions}; the list goes on
 * afterwards. A failure inside the chosen branch fails the whole scenario.
 *
 * <pre>{@code
 * - type: if
 *   when: { type: expression, expression: "level() >= 10" }
 *   then: [ { type: message, messages: ['Veteran price'] } ]
 *   else: [ { type: message, messages: ['Normal price'] } ]
 * }</pre>
 */
public final class IfAction implements Action {
    @ConfigKey("when")
    private final Requirement requirement;
    @ConfigKey("then")
    private final List<Action> thenActions;
    @ConfigKey("else")
    private final List<Action> elseActions;

    /** Creates the action with every setting at its default. */
    public IfAction() {
        this(null, Collections.emptyList(), Collections.emptyList());
    }

    /** Creates it with the first 1 setting; the rest keep their defaults. */
    public IfAction(@Nullable Requirement requirement) {
        this(requirement, Collections.emptyList(), Collections.emptyList());
    }

    /** Creates it with the first 2 settings; the rest keep their defaults. */
    public IfAction(@Nullable Requirement requirement, @NotNull List<Action> thenActions) {
        this(requirement, thenActions, Collections.emptyList());
    }

    /** Creates it with every setting given. */
    public IfAction(@Nullable Requirement requirement, @NotNull List<Action> thenActions, @NotNull List<Action> elseActions) {
        this.requirement = requirement;
        this.thenActions = Objects.requireNonNull(thenActions, "thenActions");
        this.elseActions = elseActions;
    }

    /** The question; a missing one is never met. */
    @Nullable
    public Requirement getRequirement() {
        return requirement;
    }

    /** Run when the requirement is met. */
    @NotNull
    public List<Action> getThenActions() {
        return thenActions;
    }

    /** Run when it is not. */
    @NotNull
    public List<Action> getElseActions() {
        return elseActions;
    }

    @NotNull
    @Override
    public ActionResult run(@NotNull ActionContext context) {
        Verdict verdict = requirement != null ? requirement.test(context) : null;
        boolean met = verdict != null && verdict.isMet();
        return context.runList(met ? thenActions : elseActions);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof IfAction)) {
            return false;
        }
        IfAction that = (IfAction) other;
        return Objects.equals(requirement, that.requirement)
                && Objects.equals(thenActions, that.thenActions)
                && Objects.equals(elseActions, that.elseActions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(requirement, thenActions, elseActions);
    }

    @NotNull
    @Override
    public String toString() {
        return "IfAction(requirement=" + requirement + ", thenActions=" + thenActions + ", elseActions=" + elseActions + ")";
    }
}
