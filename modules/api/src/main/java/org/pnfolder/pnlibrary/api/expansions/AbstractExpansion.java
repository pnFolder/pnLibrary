package org.pnfolder.pnlibrary.api.expansions;

import org.jetbrains.annotations.NotNull;

/**
 * Base class that implements the {@link Expansion} state machine, so subclasses only acquire and release resources.
 *
 * <pre>{@code
 * final class WeatherExpansion extends AbstractExpansion {
 *     WeatherExpansion() { super("weather"); }
 *     protected void onEnable() { } // acquire resources
 *     protected void onDisable() { } // release them
 * }
 * }</pre>
 */
public abstract class AbstractExpansion implements Expansion {
    private final String id;
    private volatile ExpansionState state = ExpansionState.DISABLED;

    protected AbstractExpansion(@NotNull String id) {
        this.id = id;
    }

    @NotNull
    @Override
    public final String getId() {
        return id;
    }

    @NotNull
    @Override
    public final ExpansionState getState() {
        return state;
    }

    /** Acquires the resources of this expansion; an exception marks the expansion {@link ExpansionState#FAILED}. */
    protected abstract void onEnable();

    /** Releases everything {@link #onEnable} acquired. Called only for an enabled expansion. */
    protected abstract void onDisable();

    /** Releases what outlives {@link #onDisable}; called once when the expansion is closed for good, after {@link #onDisable} if it was enabled. */
    protected void onClose() {
    }

    @Override
    public final synchronized void enable() {
        if (state == ExpansionState.ENABLED || state == ExpansionState.CLOSED) {
            return;
        }
        try {
            onEnable();
            state = ExpansionState.ENABLED;
        } catch (Throwable error) {
            state = ExpansionState.FAILED;
            throw error;
        }
    }

    @Override
    public final synchronized void disable() {
        release(ExpansionState.DISABLED);
    }

    @Override
    public final synchronized void close() {
        release(ExpansionState.CLOSED);
    }

    private void release(ExpansionState target) {
        if (state == ExpansionState.CLOSED) {
            return;
        }
        try {
            if (state == ExpansionState.ENABLED) {
                onDisable();
            }
        } finally {
            state = target;
            if (target == ExpansionState.CLOSED) {
                onClose();
            }
        }
    }
}
