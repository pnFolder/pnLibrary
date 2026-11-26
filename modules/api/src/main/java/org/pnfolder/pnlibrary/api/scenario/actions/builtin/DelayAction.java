package org.pnfolder.pnlibrary.api.scenario.actions.builtin;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.config.ConfigKey;
import org.pnfolder.pnlibrary.api.scenario.actions.Action;
import org.pnfolder.pnlibrary.api.scenario.actions.ActionContext;
import org.pnfolder.pnlibrary.api.scenario.actions.ActionResult;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Runs {@code steps} after {@code after} without waiting; the scenario goes on at once.
 *
 * <p>The delayed part is its own scenario: a failure there is logged and undoes only its own steps.
 */
public final class DelayAction implements Action {
    private final Duration after;
    @ConfigKey("then")
    private final List<Action> steps;

    /** Creates the action with every setting at its default. */
    public DelayAction() {
        this(Duration.ofSeconds(1), Collections.emptyList());
    }

    /** Creates it with the first 1 setting; the rest keep their defaults. */
    public DelayAction(@NotNull Duration after) {
        this(after, Collections.emptyList());
    }

    /** Creates it with every setting given. */
    public DelayAction(@NotNull Duration after, @NotNull List<Action> steps) {
        if (after.isNegative()) {
            throw new IllegalArgumentException("Delay must not be negative");
        }
        this.after = after;
        this.steps = steps;
    }

    /** The pause. */
    @NotNull
    public Duration getAfter() {
        return after;
    }

    /** What runs afterwards. */
    @NotNull
    public List<Action> getSteps() {
        return steps;
    }

    @NotNull
    @Override
    public ActionResult run(@NotNull ActionContext context) {
        context.runLater(after, steps);
        return ActionResult.Done.INSTANCE;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof DelayAction)) {
            return false;
        }
        DelayAction that = (DelayAction) other;
        return Objects.equals(after, that.after)
                && Objects.equals(steps, that.steps);
    }

    @Override
    public int hashCode() {
        return Objects.hash(after, steps);
    }

    @NotNull
    @Override
    public String toString() {
        return "DelayAction(after=" + after + ", steps=" + steps + ")";
    }
}
