package org.pnfolder.pnlibrary.api.compat.declaration;

import org.jetbrains.annotations.NotNull;

/**
 * The same methods on the registration of a plugin (all its modules) and on the builder of one module. A module that
 * declares its own versions uses them; one that declares nothing uses the plugin's. Calls add up.
 *
 * <pre>{@code
 * registration.supportedMinecraft(MinecraftVersions.between(MinecraftVersion.V1_16_5, MinecraftVersion.V1_21_4));
 * registration.allowForce(false);   // no "start anyway" for this plugin
 * registration.allowForceNewer(true); // except above the newest declared version
 * registration.registerModule(MODULE_ID, setup -> {
 *     setup.supportedMinecraft(MinecraftVersions.from(MinecraftVersion.V1_20_5));
 *     setup.onNewerMinecraft(NewerAction.ALLOW);
 * });
 * }</pre>
 */
public interface MinecraftSupportDeclaration {
    /** Adds {@code versions} to the ones this plugin or module runs on. */
    void supportedMinecraft(@NotNull MinecraftVersions versions);

    /** Chooses what happens outside the declared versions; {@link UnsupportedAction#DISABLE_MODULE} by default. */
    void onUnsupportedMinecraft(@NotNull UnsupportedAction action);

    /** Chooses what happens above the newest declared version; {@link NewerAction#ALLOW_WITH_WARNING} by default. */
    void onNewerMinecraft(@NotNull NewerAction action);

    /**
     * Chooses whether an administrator may start this anyway with {@code /pn modules mc force}, although the server
     * version is outside the declared ones. Allowed by default; pass {@code false} for code that cannot work there.
     */
    void allowForce(boolean allowed);

    /**
     * Chooses whether an administrator may start this anyway on a server version above the newest declared one. It
     * overrides {@link #allowForce(boolean)} there only, so older versions can be forbidden while newer ones are
     * allowed, or the other way round. Not set by default: {@code allowForce} decides.
     */
    void allowForceNewer(boolean allowed);
}
