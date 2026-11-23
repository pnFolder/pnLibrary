package org.pnfolder.pnlibrary.core.plugin.module

/** A module context whose Minecraft version check an administrator can switch off ("start anyway"). */
internal interface MinecraftForcible {
    fun forceMinecraft(on: Boolean)
}
