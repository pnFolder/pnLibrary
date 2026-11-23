package org.pnfolder.pnlibrary.api.compat

import org.pnfolder.pnlibrary.common.minecraft.MinecraftVersion

/**
 * The same methods on the registration of a plugin (all its modules) and on the builder of one module. A module that
 * declares its own versions uses them; one that declares nothing uses the plugin's.
 *
 * ```kotlin
 * registration.supportedMinecraft(MinecraftVersion.V1_16_5, MinecraftVersion.V1_21_4)   // the plugin, from and to
 * registration.registerModule(MODULE_ID) { setup ->
 *     setup.supportedMinecraftFrom(MinecraftVersion.V1_20_5)       // this module, that version and every newer
 *     setup.onNewerMinecraft(NewerAction.ALLOW)
 * }
 * ```
 *
 * Every method also takes the version as text (`"1.21.11"`), for a release that has no constant yet.
 */
interface MinecraftSupportDeclaration {
    /** The rule these methods fill in. */
    val minecraftRule: MinecraftSupportRule

    /** Adds the versions from [from] to [to], both included. */
    fun supportedMinecraft(from: MinecraftVersion, to: MinecraftVersion) = minecraftRule.between(from.versionText(), to.versionText())

    /** Adds one version. */
    fun supportedMinecraft(version: MinecraftVersion) = minecraftRule.between(version.versionText(), version.versionText())

    /** Adds the versions from [from] to [to], both included, as text. */
    fun supportedMinecraft(from: String, to: String) = minecraftRule.between(from, to)

    /** Adds one version as text. */
    fun supportedMinecraft(version: String) = minecraftRule.between(version, version)

    /** Adds exactly these versions and nothing between them. */
    fun supportedMinecraftOnly(vararg versions: MinecraftVersion) = versions.forEach { supportedMinecraft(it) }

    /** Adds exactly these versions, as text. */
    fun supportedMinecraftOnly(vararg versions: String) = versions.forEach { supportedMinecraft(it) }

    /** Adds [version] and every newer one, also releases pnLibrary does not know yet. */
    fun supportedMinecraftFrom(version: MinecraftVersion) = minecraftRule.between(version.versionText(), null)

    /** Adds [version] and every newer one, as text. */
    fun supportedMinecraftFrom(version: String) = minecraftRule.between(version, null)

    /** Adds every version up to [version], included. */
    fun supportedMinecraftUpTo(version: MinecraftVersion) = minecraftRule.between(null, version.versionText())

    /** Adds every version up to [version], as text. */
    fun supportedMinecraftUpTo(version: String) = minecraftRule.between(null, version)

    /** Chooses what happens outside the declared versions; [UnsupportedAction.DISABLE_MODULE] by default. */
    fun onUnsupportedMinecraft(action: UnsupportedAction) {
        minecraftRule.unsupportedAction = action
    }

    /** Chooses what happens above the newest declared version; [NewerAction.ALLOW_WITH_WARNING] by default. */
    fun onNewerMinecraft(action: NewerAction) {
        minecraftRule.newerAction = action
    }
}

private fun MinecraftVersion.versionText(): String {
    require(known) { "UNKNOWN is not a Minecraft version; give the version as text" }
    return text
}
