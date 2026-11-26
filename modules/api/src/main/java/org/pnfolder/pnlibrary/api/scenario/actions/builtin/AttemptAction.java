package org.pnfolder.pnlibrary.api.scenario.actions.builtin;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.config.ConfigKey;
import org.pnfolder.pnlibrary.api.scenario.actions.Action;
import org.pnfolder.pnlibrary.api.scenario.actions.ActionContext;
import org.pnfolder.pnlibrary.api.scenario.actions.ActionResult;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Runs {@code steps} as a unit that may fail. When a step fails, everything that unit did is undone (a charge is
 * given back), then the first of {@code handlers} that takes the failure's code runs, or {@code fail} when none
 * does. The scenario then ends, or goes on when {@code resume} is set.
 *
 * <p>Inside a handler the cause is available as {@code [error]}, its code as {@code [error_code]}.
 *
 * <pre>{@code
 * - type: attempt
 *   do:
 *     - { type: charge, currency: money, amount: 100 }
 *     - { type: command, command: 'give [player] diamond 1' }
 *   on:
 *     - codes: [currency.insufficient-funds]
 *       do: [ { type: message, messages: ['&cYou need 100 coins'] } ]
 *   fail:
 *     - { type: message, messages: ['&cCould not buy: [error]'] }
 * }</pre>
 */
public final class AttemptAction implements Action {
    @ConfigKey("do")
    private final List<Action> steps;
    @ConfigKey("on")
    private final List<ErrorHandler> handlers;
    private final List<Action> fail;
    private final boolean resume;
    private final boolean undo;

    /** Creates the action with every setting at its default. */
    public AttemptAction() {
        this(Collections.<Action>emptyList(), Collections.<ErrorHandler>emptyList(), Collections.<Action>emptyList(), false, true);
    }

    /** Creates it with the first 1 setting; the rest keep their defaults. */
    public AttemptAction(@NotNull List<Action> steps) {
        this(steps, Collections.<ErrorHandler>emptyList(), Collections.<Action>emptyList(), false, true);
    }

    /** Creates it with the first 2 settings; the rest keep their defaults. */
    public AttemptAction(@NotNull List<Action> steps, @NotNull List<ErrorHandler> handlers) {
        this(steps, handlers, Collections.<Action>emptyList(), false, true);
    }

    /** Creates it with the first 3 settings; the rest keep their defaults. */
    public AttemptAction(@NotNull List<Action> steps, @NotNull List<ErrorHandler> handlers, @NotNull List<Action> fail) {
        this(steps, handlers, fail, false, true);
    }

    /** Creates it with the first 4 settings; the rest keep their defaults. */
    public AttemptAction(@NotNull List<Action> steps, @NotNull List<ErrorHandler> handlers, @NotNull List<Action> fail, boolean resume) {
        this(steps, handlers, fail, resume, true);
    }

    /** Creates it with every setting given. */
    public AttemptAction(@NotNull List<Action> steps, @NotNull List<ErrorHandler> handlers, @NotNull List<Action> fail, boolean resume, boolean undo) {
        this.steps = steps;
        this.handlers = handlers;
        this.fail = fail;
        this.resume = resume;
        this.undo = undo;
    }

    /** The actions that may fail. */
    @NotNull
    public List<Action> getSteps() {
        return steps;
    }

    /** Reactions by failure code, the first match wins. */
    @NotNull
    public List<ErrorHandler> getHandlers() {
        return handlers;
    }

    /** Actions run for a failure no handler takes. */
    @NotNull
    public List<Action> getFail() {
        return fail;
    }

    /** Whether the rest of the list runs after a handled failure. */
    public boolean isResume() {
        return resume;
    }

    /** Whether the steps are undone on a failure; with {@code false} what they did stays, and an outer failure does not undo it either. */
    public boolean isUndo() {
        return undo;
    }

    private List<Action> reactionTo(String code) {
        for (ErrorHandler handler : handlers) {
            if (handler.matches(code)) {
                return handler.getSteps();
            }
        }
        return fail;
    }

    @NotNull
    @Override
    public ActionResult run(@NotNull ActionContext context) {
        int mark = context.undoMark();
        ActionResult result = context.runList(steps);
        if (!(result instanceof ActionResult.Failed)) {
            return result;
        }
        ActionResult.Failed failed = (ActionResult.Failed) result;
        if (undo) {
            context.rollbackTo(mark);
        } else {
            context.discardUndo(mark);
        }
        context.setValue("error", failed.getMessage()).setValue("error_code", failed.getCode());
        ActionResult handled = context.runList(reactionTo(failed.getCode()));
        if (handled instanceof ActionResult.Failed) {
            return handled;
        }
        return resume ? ActionResult.Done.INSTANCE : new ActionResult.Stopped(failed.getMessage());
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof AttemptAction)) {
            return false;
        }
        AttemptAction that = (AttemptAction) other;
        return Objects.equals(steps, that.steps)
                && Objects.equals(handlers, that.handlers)
                && Objects.equals(fail, that.fail)
                && resume == that.resume
                && undo == that.undo;
    }

    @Override
    public int hashCode() {
        return Objects.hash(steps, handlers, fail, resume, undo);
    }

    @NotNull
    @Override
    public String toString() {
        return "AttemptAction(steps=" + steps + ", handlers=" + handlers + ", fail=" + fail + ", resume=" + resume + ", undo=" + undo + ")";
    }
}
