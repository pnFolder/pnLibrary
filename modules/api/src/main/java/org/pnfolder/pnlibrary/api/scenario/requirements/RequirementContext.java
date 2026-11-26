package org.pnfolder.pnlibrary.api.scenario.requirements;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.audience.OnlinePlayer;
import org.pnfolder.pnlibrary.api.logging.PnLogger;
import org.pnfolder.pnlibrary.api.scenario.expression.ExpressionFunction;

/**
 * What a {@link Requirement} may look at: the player, the values of the invocation, placeholders and services.
 *
 * <p>{@code ActionContext} implements it, so the same requirement works inside an action list and on its own.
 */
public interface RequirementContext {
    /** The player being judged. */
    @NotNull
    OnlinePlayer getPlayer();

    /** Logger for requirements that cannot be evaluated. */
    @NotNull
    PnLogger getLogger();

    /**
     * Reads {@code expression} as a value: one {@code [value]}, {@code {placeholder}} or {@code %external%}, text
     * that contains them, or a bare name (a context value first, then a pnLibrary placeholder). Returns
     * {@code null} when nothing matches.
     */
    @Nullable
    Object read(@NotNull String expression);

    /** Returns {@code expression} with every placeholder and value replaced; unknown names stay as written. */
    @NotNull
    String text(@NotNull String expression);

    /** Returns the context value called {@code name}, or {@code null}. */
    @Nullable
    Object value(@NotNull String name);

    /** Returns the requirement a plugin defined under {@code name} with {@code ActionService.defineRequirement}, or {@code null}. */
    @Nullable
    default Requirement requirement(@NotNull String name) {
        return null;
    }

    /** Returns the expression function a plugin defined under {@code name} with {@code ActionService.defineFunction}, or {@code null}. */
    @Nullable
    default ExpressionFunction function(@NotNull String name) {
        return null;
    }

    /** Returns the service of {@code type} available to this invocation, such as the currency service, or {@code null}. */
    @Nullable
    <T> T service(@NotNull Class<T> type);
}
