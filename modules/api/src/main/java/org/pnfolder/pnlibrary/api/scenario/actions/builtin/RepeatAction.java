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
 * Runs {@code steps} {@code times} times, {@code every} apart. The first run is immediate and part of the scenario;
 * later runs are separate scenarios.
 */
public final class RepeatAction implements Action {
    /** The most runs one repeat may schedule. */
    public static final int MAX_TIMES = 1000;

    private final int times;
    private final Duration every;
    @ConfigKey("then")
    private final List<Action> steps;

    /** Creates the action with every setting at its default. */
    public RepeatAction() {
        this(1, Duration.ZERO, Collections.<Action>emptyList());
    }

    /** Creates it with the first 1 setting; the rest keep their defaults. */
    public RepeatAction(int times) {
        this(times, Duration.ZERO, Collections.<Action>emptyList());
    }

    /** Creates it with the first 2 settings; the rest keep their defaults. */
    public RepeatAction(int times, @NotNull Duration every) {
        this(times, every, Collections.<Action>emptyList());
    }

    /** Creates it with every setting given. */
    public RepeatAction(int times, @NotNull Duration every, @NotNull List<Action> steps) {
        if (times < 1 || times > MAX_TIMES) {
            throw new IllegalArgumentException("Repeat times must be from 1 to " + MAX_TIMES);
        }
        if (every.isNegative()) {
            throw new IllegalArgumentException("Repeat interval must not be negative");
        }
        this.times = times;
        this.every = every;
        this.steps = steps;
    }

    /** How many runs, from 1 to {@link #MAX_TIMES}. */
    public int getTimes() {
        return times;
    }

    /** Pause between runs. */
    @NotNull
    public Duration getEvery() {
        return every;
    }

    /** What runs each time. */
    @NotNull
    public List<Action> getSteps() {
        return steps;
    }

    @NotNull
    @Override
    public ActionResult run(@NotNull ActionContext context) {
        for (int run = 1; run < times; run++) {
            context.runLater(every.multipliedBy(run), steps);
        }
        return context.runList(steps);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof RepeatAction)) {
            return false;
        }
        RepeatAction that = (RepeatAction) other;
        return times == that.times
                && Objects.equals(every, that.every)
                && Objects.equals(steps, that.steps);
    }

    @Override
    public int hashCode() {
        return Objects.hash(times, every, steps);
    }

    @NotNull
    @Override
    public String toString() {
        return "RepeatAction(times=" + times + ", every=" + every + ", steps=" + steps + ")";
    }
}
