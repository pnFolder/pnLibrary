package org.pnfolder.pnlibrary.api.plugin.control

import java.util.concurrent.CompletionStage

/**
 * Switches one module on and off. The same operations are behind `/pn modules` and the module screens of the menu.
 *
 * A module registered without a [PluginModule] has nothing to switch: [state] stays [ModuleState.ENABLED] and every
 * change fails with [UnsupportedOperationException].
 */
interface ModuleControl {
    /** Current state. */
    val state: ModuleState

    /** The error that put the module into [ModuleState.FAILED], or `null`. */
    val lastError: Throwable?

    /** Whether the module has hooks, that is, was registered with a [PluginModule]. */
    val isControllable: Boolean

    /** Whether the module starts when the server starts; an administrator's choice is saved and survives restarts. */
    val autoStart: Boolean

    /** Switches the module on. Does nothing when it already works. */
    fun enable(): CompletionStage<ModuleState>

    /** Switches the module off for now; it starts again with the server unless [autoStart] is off. */
    fun disable(): CompletionStage<ModuleState>

    /** Disables the module and enables it again. */
    fun restart(): CompletionStage<ModuleState>

    /** Re-reads the module's settings; when the module cannot do that in place it is restarted. */
    fun reload(): CompletionStage<ModuleState>

    /** Saves whether the module starts with the server. This does not switch it on or off right now. */
    fun setAutoStart(enabled: Boolean)
}
