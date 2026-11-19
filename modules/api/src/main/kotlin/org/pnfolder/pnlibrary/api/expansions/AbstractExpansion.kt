package org.pnfolder.pnlibrary.api.expansions

/**
 * Base class that implements the [Expansion] state machine, so subclasses only acquire and release resources.
 *
 * Example:
 * ```kotlin
 * class WeatherExpansion : AbstractExpansion("weather") {
 *     override fun onEnable() { /* acquire resources */ }
 *     override fun onDisable() { /* release them */ }
 * }
 * ```
 */
abstract class AbstractExpansion protected constructor(final override val id: String) : Expansion {
    @Volatile
    final override var state: ExpansionState = ExpansionState.DISABLED
        private set

    /** Acquires the resources of this expansion; an exception marks the expansion [ExpansionState.FAILED]. */
    protected abstract fun onEnable()

    /** Releases everything [onEnable] acquired. Called only for an enabled expansion. */
    protected abstract fun onDisable()

    /** Releases what outlives [onDisable]; called once when the expansion is closed for good, after [onDisable] if it was enabled. */
    protected open fun onClose() {}

    @Synchronized
    final override fun enable() {
        if (state == ExpansionState.ENABLED || state == ExpansionState.CLOSED) return
        try {
            onEnable()
            state = ExpansionState.ENABLED
        } catch (error: Throwable) {
            state = ExpansionState.FAILED
            throw error
        }
    }

    @Synchronized
    final override fun disable() = release(ExpansionState.DISABLED)

    @Synchronized
    final override fun close() = release(ExpansionState.CLOSED)

    private fun release(target: ExpansionState) {
        if (state == ExpansionState.CLOSED) return
        try {
            if (state == ExpansionState.ENABLED) onDisable()
        } finally {
            state = target
            if (target == ExpansionState.CLOSED) onClose()
        }
    }
}
