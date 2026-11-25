package org.pnfolder.pnlibrary.core.plugin.module

import org.pnfolder.pnlibrary.api.compat.declaration.MinecraftVersions
import org.pnfolder.pnlibrary.api.compat.declaration.NewerAction
import org.pnfolder.pnlibrary.api.compat.declaration.UnsupportedAction

/** What a plugin or module declared about Minecraft: the [versions] and what to do outside them. */
internal class MinecraftSupportRule {
    @Volatile var versions: MinecraftVersions = MinecraftVersions.NONE

    /** What to do outside the declared versions, or `null` to take the plugin's choice, then [UnsupportedAction.DISABLE_MODULE]. */
    @Volatile var unsupportedAction: UnsupportedAction? = null

    /** What to do above the newest declared version, or `null` to take the plugin's choice, then [NewerAction.ALLOW_WITH_WARNING]. */
    @Volatile var newerAction: NewerAction? = null

    val isDeclared: Boolean get() = versions.isDeclared

    val description: String? get() = versions.description

    @Synchronized
    fun add(more: MinecraftVersions) {
        versions += more
    }
}
