package org.pnfolder.pnlibrary.api.compat.declaration;

import org.jetbrains.annotations.NotNull;

/**
 * Everything a plugin or module says about the Minecraft versions it runs on. Every method is optional; what is not
 * set takes the plugin's choice, then the default.
 *
 * <pre>{@code
 * setup.minecraft(mc -> mc
 *     .versions(MinecraftVersions.from(MinecraftVersion.V1_16_5))  // 1.16.5 and newer
 *     .versions(MinecraftVersions.only("1.12.2"))                  // and also 1.12.2
 *     .whenNewer(NewerAction.ALLOW_WITH_WARNING)
 *     .forceNewer(false));
 * }</pre>
 */
public interface MinecraftSupportBuilder {
    /** Adds {@code versions} to the ones this plugin or module runs on; calls add up and never replace each other. */
    @NotNull MinecraftSupportBuilder versions(@NotNull MinecraftVersions versions);

    /** Chooses what happens outside the declared versions; {@link UnsupportedAction#DISABLE_MODULE} by default. */
    @NotNull MinecraftSupportBuilder whenOlder(@NotNull UnsupportedAction action);

    /** Chooses what happens above the newest declared version; {@link NewerAction#ALLOW_WITH_WARNING} by default. */
    @NotNull MinecraftSupportBuilder whenNewer(@NotNull NewerAction action);

    /**
     * Chooses whether an administrator may start this anyway with {@code /pn modules mc force}, although the server
     * version is outside the declared ones. Allowed by default; pass {@code false} for code that cannot work there.
     */
    @NotNull MinecraftSupportBuilder force(boolean allowed);

    /**
     * Chooses whether an administrator may start this anyway above the newest declared version. It overrides
     * {@link #force(boolean)} there only, so older versions can be forbidden while newer ones are allowed, or the
     * other way round. Not set by default: {@code force} decides.
     */
    @NotNull MinecraftSupportBuilder forceNewer(boolean allowed);
}
