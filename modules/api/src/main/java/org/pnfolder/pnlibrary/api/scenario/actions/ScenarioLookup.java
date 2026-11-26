package org.pnfolder.pnlibrary.api.scenario.actions;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.scenario.expression.ExpressionFunction;
import org.pnfolder.pnlibrary.api.scenario.requirements.Requirement;

/**
 * Finds the actions, requirements and functions that plugins defined by name, for {@code call}, {@code ref} and
 * expressions.
 *
 * <p>{@link ActionService} keeps the definitions of one module and passes them to every context it creates.
 */
public interface ScenarioLookup {
    /** A lookup that knows nothing. */
    ScenarioLookup NONE = new ScenarioLookup() {
    };

    /** The action defined under {@code name}, or {@code null}. */
    @Nullable
    default Action action(@NotNull String name) {
        return null;
    }

    /** The requirement defined under {@code name}, or {@code null}. */
    @Nullable
    default Requirement requirement(@NotNull String name) {
        return null;
    }

    /** The expression function defined under {@code name}, or {@code null}. */
    @Nullable
    default ExpressionFunction function(@NotNull String name) {
        return null;
    }
}
