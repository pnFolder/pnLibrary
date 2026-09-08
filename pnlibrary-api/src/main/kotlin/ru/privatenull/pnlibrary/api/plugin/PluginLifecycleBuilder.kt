package ru.privatenull.pnlibrary.api.plugin

import java.util.function.Consumer

/**
 * Declarative customization evaluated when enabled and disabled messages are shown.
 * Callbacks only add rows; pnLibrary displays the completed message automatically.
 */
interface PluginLifecycleBuilder {
    fun enabled(configure: Consumer<LifecycleReport>): PluginLifecycleBuilder
    fun disabled(configure: Consumer<LifecycleReport>): PluginLifecycleBuilder
}
