package org.pnfolder.pnlibrary.api.plugin.control

import org.pnfolder.pnlibrary.api.libraries.ModuleLibrary
import org.pnfolder.pnlibrary.api.plugin.ModuleContext

/**
 * What a plugin gives one of its modules so that the library, an administrator's command or the menu can switch it.
 *
 * Every method has an empty default, so a module overrides only what it needs. The [ModuleContext] is the module's own,
 * the same one `registerModule` returned, so a hook reaches the logger, configuration, services and everything else
 * from it.
 *
 * ```kotlin
 * class Shop : PluginModule {
 *     override fun onEnable(context: ModuleContext) {
 *         context.registerCommand("shop") { /* ... */ }
 *     }
 * }
 * registration.registerModule("shop", Shop())
 * ```
 *
 * When a module is disabled the library cancels its tasks and removes its commands after [onDisable], so those need no
 * clean-up. Listeners, open files and anything else the module opened itself belong in [onDisable].
 */
interface PluginModule {
    /**
     * The Maven libraries this module needs, with where to download them and how to check them. They are loaded before
     * [onEnable] each time the module starts; one already loaded is not downloaded again. A required library that cannot be
     * loaded leaves the module in [ModuleState.FAILED] with the reason.
     */
    fun libraries(): List<ModuleLibrary> = emptyList()

    /** Starts the module: registers its commands, listeners and tasks. Runs at registration and on every enable. */
    fun onEnable(context: ModuleContext) {}

    /** Stops the module and releases what it opened itself. Runs on disable, on restart and when the plugin shuts down. */
    fun onDisable(context: ModuleContext) {}

    /**
     * Re-reads the module's settings without stopping it. A module that cannot do this in place returns `false`,
     * and the library restarts it instead.
     */
    fun onReload(context: ModuleContext): Boolean = false
}
