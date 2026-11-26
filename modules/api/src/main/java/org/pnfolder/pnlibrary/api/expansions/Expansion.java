package org.pnfolder.pnlibrary.api.expansions;

import org.jetbrains.annotations.NotNull;

/**
 * A shared feature that pnLibrary starts, stops, and finally closes as one unit, in the manner of a PlaceholderAPI
 * expansion. Register it with {@link ExpansionManager#register}.
 *
 * <p>Extend {@link AbstractExpansion} rather than implementing this interface directly; it keeps {@link #getState}
 * consistent.
 */
public interface Expansion extends AutoCloseable {
    /** Identifier that is unique among registered expansions. */
    @NotNull
    String getId();

    /** Where the expansion is in its lifecycle. */
    @NotNull
    ExpansionState getState();

    /** Starts the expansion; does nothing when it is already enabled. A failure leaves it {@link ExpansionState#FAILED} and is rethrown. */
    void enable();

    /** Releases resources so that {@link #enable} can start the expansion again. */
    void disable();

    /** Disables the expansion for good. */
    @Override
    void close();
}
