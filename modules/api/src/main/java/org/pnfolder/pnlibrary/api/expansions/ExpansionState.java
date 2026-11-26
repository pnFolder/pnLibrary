package org.pnfolder.pnlibrary.api.expansions;

/** Lifecycle of an {@link Expansion}. {@link #CLOSED} is terminal; a {@link #FAILED} expansion may be started again with {@link Expansion#enable}. */
public enum ExpansionState {
    DISABLED,
    ENABLED,
    FAILED,
    CLOSED
}
