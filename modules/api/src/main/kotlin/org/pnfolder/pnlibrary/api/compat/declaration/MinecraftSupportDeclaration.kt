package org.pnfolder.pnlibrary.api.compat.declaration

/**
 * The same methods on the registration of a plugin (all its modules) and on the builder of one module. A module that
 * declares its own versions uses them; one that declares nothing uses the plugin's. Calls add up.
 *
 * ```kotlin
 * registration.supportedMinecraft(MinecraftVersions.between(MinecraftVersion.V1_16_5, MinecraftVersion.V1_21_4))
 * registration.registerModule(MODULE_ID) { setup ->
 *     setup.supportedMinecraft(MinecraftVersions.from(MinecraftVersion.V1_20_5))
 *     setup.onNewerMinecraft(NewerAction.ALLOW)
 * }
 * ```
 */
interface MinecraftSupportDeclaration {
    /** Adds [versions] to the ones this plugin or module runs on. */
    fun supportedMinecraft(versions: MinecraftVersions)

    /** Chooses what happens outside the declared versions; [UnsupportedAction.DISABLE_MODULE] by default. */
    fun onUnsupportedMinecraft(action: UnsupportedAction)

    /** Chooses what happens above the newest declared version; [NewerAction.ALLOW_WITH_WARNING] by default. */
    fun onNewerMinecraft(action: NewerAction)
}
