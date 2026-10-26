package org.pnfolder.pnlibrary.localization.layout

import java.util.concurrent.atomic.AtomicBoolean

/**
 * The keyboard layouts a localization service reads queries against: the [KeyboardLayout.builtIn] ones plus any
 * registered here. [org.pnfolder.pnlibrary.localization.TranslationIndex.suggest] and `best` use them unless the
 * call passes its own layouts.
 *
 * The registry belongs to the localization service, which every plugin shares, so a layout registered by one plugin
 * is seen by all of them. Prefix its id with the plugin name. Through `ModuleContext.localization` a layout is removed
 * when the module closes; the returned handle only removes it earlier.
 */
interface KeyboardLayouts : AutoCloseable {
    /**
     * Adds [layout] to [all]. Throws [IllegalArgumentException] when its id is already taken, by a built-in layout or
     * a registered one, and [IllegalStateException] after [close]. Closing the returned handle removes this
     * registration; closing it again does nothing, even if the same layout was registered since.
     *
     * ```kotlin
     * val handle = context.localization.layouts.register(KeyboardLayout.of("myplugin-dvorak", "..."))
     * handle.close() // optional: closing the module removes it too
     * ```
     */
    fun register(layout: KeyboardLayout): AutoCloseable

    /** The built-in layouts first, then the registered ones in registration order. */
    fun all(): List<KeyboardLayout>

    /** Removes every registered layout and rejects further registrations. */
    override fun close()

    /** Ready-made registries. */
    companion object {
        /** The built-in layouts only. It is read-only: [register] throws [UnsupportedOperationException]. */
        @JvmField
        val builtIn: KeyboardLayouts = object : KeyboardLayouts {
            override fun register(layout: KeyboardLayout): AutoCloseable =
                throw UnsupportedOperationException("The built-in layouts are read-only; create a registry with KeyboardLayouts.create()")

            override fun all(): List<KeyboardLayout> = KeyboardLayout.builtIn
            override fun close() = Unit
        }

        /** Creates an empty registry holding the built-in layouts. */
        @JvmStatic
        fun create(): KeyboardLayouts = KeyboardLayoutRegistry()
    }
}

private class KeyboardLayoutRegistry : KeyboardLayouts {
    private val lock = Any()

    @Volatile
    private var layouts: List<KeyboardLayout> = KeyboardLayout.builtIn
    private var closed = false

    override fun register(layout: KeyboardLayout): AutoCloseable {
        synchronized(lock) {
            check(!closed) { "Keyboard layouts are closed" }
            require(layouts.none { it.id == layout.id }) { "Keyboard layout '${layout.id}' is already registered" }
            layouts = layouts + layout
        }
        val done = AtomicBoolean()
        return AutoCloseable {
            if (done.compareAndSet(false, true)) synchronized(lock) { layouts = layouts.filter { it !== layout } }
        }
    }

    override fun all(): List<KeyboardLayout> = layouts

    override fun close() {
        synchronized(lock) {
            closed = true
            layouts = KeyboardLayout.builtIn
        }
    }
}
