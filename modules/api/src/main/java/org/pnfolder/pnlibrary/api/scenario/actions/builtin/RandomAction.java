package org.pnfolder.pnlibrary.api.scenario.actions.builtin;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.scenario.actions.Action;
import org.pnfolder.pnlibrary.api.scenario.actions.ActionContext;
import org.pnfolder.pnlibrary.api.scenario.actions.ActionResult;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Runs one of {@code options} chosen by chance; a bigger {@code weight} is chosen more often.
 *
 * <pre>{@code
 * - type: random
 *   options:
 *     - { weight: 70, do: [ { type: message, messages: ['Common'] } ] }
 *     - { weight: 30, do: [ { type: message, messages: ['Rare'] } ] }
 * }</pre>
 */
public final class RandomAction implements Action {
    private final List<RandomOption> options;

    /** Creates the action with every setting at its default. */
    public RandomAction() {
        this(Collections.<RandomOption>emptyList());
    }

    /** Creates it with every setting given. */
    public RandomAction(@NotNull List<RandomOption> options) {
        this.options = options;
    }

    /** The choices; with none, nothing happens. */
    @NotNull
    public List<RandomOption> getOptions() {
        return options;
    }

    @NotNull
    @Override
    public ActionResult run(@NotNull ActionContext context) {
        if (options.isEmpty()) {
            return ActionResult.Done.INSTANCE;
        }
        double total = 0;
        for (RandomOption option : options) {
            total += option.getWeight();
        }
        double roll = ThreadLocalRandom.current().nextDouble() * total;
        for (RandomOption option : options) {
            roll -= option.getWeight();
            if (roll < 0) {
                return context.runList(option.getSteps());
            }
        }
        return context.runList(options.get(options.size() - 1).getSteps());
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof RandomAction)) {
            return false;
        }
        RandomAction that = (RandomAction) other;
        return Objects.equals(options, that.options);
    }

    @Override
    public int hashCode() {
        return Objects.hash(options);
    }

    @NotNull
    @Override
    public String toString() {
        return "RandomAction(options=" + options + ")";
    }
}
