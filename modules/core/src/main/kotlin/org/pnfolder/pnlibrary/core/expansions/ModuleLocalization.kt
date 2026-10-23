package org.pnfolder.pnlibrary.core.expansions

import org.pnfolder.pnlibrary.localization.MinecraftLocalization
import org.pnfolder.pnlibrary.localization.layout.KeyboardLayout
import org.pnfolder.pnlibrary.localization.layout.KeyboardLayouts

/** One module's view of the shared translation loader: loading is shared, closing is not, and its layouts end with the module. */
internal class ModuleLocalization(
    private val shared: MinecraftLocalization,
    private val module: ModuleBinding,
) : MinecraftLocalization by shared {
    override val layouts: KeyboardLayouts = object : KeyboardLayouts by shared.layouts {
        override fun register(layout: KeyboardLayout): AutoCloseable = module.own { shared.layouts.register(layout) }

        override fun close() = Unit
    }

    override fun close() = Unit
}
