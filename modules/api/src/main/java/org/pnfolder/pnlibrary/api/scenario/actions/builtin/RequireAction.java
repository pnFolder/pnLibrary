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
 * Ends the scenario quietly when {@code requirement} is not met, after running {@code deny}.
 *
 * <p>Inside {@code deny} the reason is available as {@code [reason]}.
 *
 * <pre>{@code
 * - type: require
 *   when: { type: permission, permission: shop.use }
 *   deny: [ { type: message, messages: ['&c[reason]'] } ]
 * }</pre>
 */
public final class RequireAction implements Action {
    @ConfigKey("when")
    private final Requirement requirement;
    private final List<Action> deny;

    /** Creates the action with every setting at its default. */
    public RequireAction() {
        this(null, Collections.<Action>emptyList());
    }

    /** Creates it with the first 1 setting; the rest keep their defaults. */
    public RequireAction(@Nullable Requirement requirement) {
        this(requirement, Collections.<Action>emptyList());
    }

    /** Creates it with every setting given. */
    public RequireAction(@Nullable Requirement requirement, @NotNull List<Action> deny) {
        this.requirement = requirement;
        this.deny = deny;
    }

    /** What must hold; a missing one is never met. */
    @Nullable
    public Requirement getRequirement() {
        return requirement;
    }

    /** Actions run when it does not. */
    @NotNull
    public List<Action> getDeny() {
        return deny;
    }

    @NotNull
    @Override
    public ActionResult run(@NotNull ActionContext context) {
        Verdict verdict = requirement != null ? requirement.test(context) : new Verdict.Unmet("no requirement is configured");
        if (!(verdict instanceof Verdict.Unmet)) {
            return ActionResult.Done.INSTANCE;
        }
        String reason = ((Verdict.Unmet) verdict).getReason();
        context.setValue("reason", reason);
        ActionResult denied = context.runList(deny);
        return denied instanceof ActionResult.Failed ? denied : new ActionResult.Stopped(reason);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof RequireAction)) {
            return false;
        }
        RequireAction that = (RequireAction) other;
        return Objects.equals(requirement, that.requirement)
                && Objects.equals(deny, that.deny);
    }

    @Override
    public int hashCode() {
        return Objects.hash(requirement, deny);
    }

    @NotNull
    @Override
    public String toString() {
        return "RequireAction(requirement=" + requirement + ", deny=" + deny + ")";
    }
}
