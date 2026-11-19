package org.pnfolder.pnlibrary.updates.policy

import org.pnfolder.pnlibrary.api.updates.product.UpdateChannel
import org.pnfolder.pnlibrary.common.lang.runCatchingNonFatal
import org.pnfolder.pnlibrary.updates.engine.UpdatePreferencesStore

/**
 * The channel and automatic-download choices an administrator makes with commands or in the menu: one per component,
 * and one for all plugins together. They are kept until changed again and take precedence over the defaults.
 *
 * The choices are the channel, automatic and all-plugins sections of [store]; the pause section belongs to another owner.
 */
internal class UpdatePolicyOverrides(
    private val store: UpdatePreferencesStore,
    private val warning: (String) -> Unit = {},
) {
    private data class State(
        val channels: Map<String, UpdateChannel>,
        val automatic: Map<String, Boolean>,
        val allPlugins: Boolean? = null,
        val javaBuilds: Map<String, Int> = emptyMap(),
    )

    @Volatile
    private var state = load()

    /** The channel chosen for [product] at runtime, or null when the default decides. */
    fun channel(product: String): UpdateChannel? = state.channels[product]

    /** The Java build chosen for [product], or null when the recommended one is used. */
    fun javaBuild(product: String): Int? = state.javaBuilds[product]

    /** Remembers the Java build [java] for [product], or forgets the choice when it is null. */
    @Synchronized
    fun setJavaBuild(product: String, java: Int?) {
        state = state.copy(javaBuilds = if (java == null) state.javaBuilds - product else state.javaBuilds + (product to java))
        save()
    }

    /** The automatic-download choice for [product] at runtime, or null when the default decides. */
    fun automatic(product: String): Boolean? = state.automatic[product]

    /** The automatic-download choice for all plugins together, or null when none was made. */
    fun allPlugins(): Boolean? = state.allPlugins

    /** Whether any component was switched to automatic download at runtime. */
    fun anyAutomaticEnabled(): Boolean = state.allPlugins == true || state.automatic.values.any { it }

    /** Remembers [channel] for [product], or forgets the choice when [channel] is null. */
    @Synchronized
    fun setChannel(product: String, channel: UpdateChannel?) {
        state = state.copy(channels = if (channel == null) state.channels - product else state.channels + (product to channel))
        save()
    }

    @Synchronized
    fun setAutomatic(product: String, enabled: Boolean) {
        state = state.copy(automatic = state.automatic + (product to enabled))
        save()
    }

    /** Sets automatic download for every plugin and forgets the choices made for single ones; [libraryProduct] keeps its own. */
    @Synchronized
    fun setAllPlugins(enabled: Boolean, libraryProduct: String) {
        state = state.copy(allPlugins = enabled, automatic = state.automatic.filterKeys { it == libraryProduct })
        save()
    }

    private fun load(): State {
        val stored = store.current()
        return State(
            channels = stored.channels.mapNotNull { (product, name) ->
                runCatchingNonFatal { UpdateChannel.valueOf(name) }
                    .onFailure { warning("Unknown update channel for $product in ${store.fileName}; the default is used") }
                    .getOrNull()?.let { product to it }
            }.toMap(),
            automatic = stored.automatic,
            allPlugins = stored.allPlugins,
            javaBuilds = stored.javaBuilds,
        )
    }

    private fun save() {
        val saved = state
        store.update {
            it.copy(
                channels = saved.channels.mapValues { (_, channel) -> channel.name },
                automatic = saved.automatic,
                allPlugins = saved.allPlugins,
                javaBuilds = saved.javaBuilds,
            )
        }
    }
}
