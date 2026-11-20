package org.pnfolder.pnlibrary.api.expansions

/**
 * A shared feature that pnLibrary starts, stops, and finally closes as one unit, in the manner of a
 * PlaceholderAPI expansion. Register it with [ExpansionManager.register].
 *
 * Extend [AbstractExpansion] rather than implementing this interface directly; it keeps [state] consistent.
 */
interface Expansion : AutoCloseable {
    /** Identifier that is unique among registered expansions. */
    val id: String

    /** Where the expansion is in its lifecycle. */
    val state: ExpansionState

    /** Starts the expansion; does nothing when it is already enabled. A failure leaves it [ExpansionState.FAILED] and is rethrown. */
    fun enable()

    /** Releases resources so that [enable] can start the expansion again. */
    fun disable()

    /** Disables the expansion for good. */
    override fun close()
}
