package org.pnfolder.pnlibrary.api.compat

/**
 * The result of the Minecraft version check of one module, as `/pn modules view` and the menu show it.
 *
 * @property support how the server's version stands against the declaration
 * @property declared the declared versions in words, such as `1.16.5-1.21.4, 1.8.8`, or `null` when nothing was declared
 * @property server the server's Minecraft version, or `null` when it cannot be read
 * @property forced an administrator chose to start the module anyway
 * @property blocked the module does not start because of this check
 * @property note the sentence that explains the result, or `null` when all is well
 */
data class MinecraftCompatibility(
    val support: MinecraftSupport,
    val declared: String?,
    val server: String?,
    val forced: Boolean = false,
    val blocked: Boolean = false,
    val note: String? = null,
) {
    companion object {
        /** A module that declared nothing. */
        @JvmField
        val NOT_DECLARED = MinecraftCompatibility(MinecraftSupport.NOT_DECLARED, null, null)
    }
}
