package org.pnfolder.pnlibrary.api.compat.declaration;

import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;

/**
 * The one method on the registration of a plugin (all its modules) and on the builder of one module. A module that
 * declares its own versions uses them; one that declares nothing uses the plugin's. Calls add up.
 *
 * <pre>{@code
 * registration.minecraft(mc -> mc
 *     .versions(MinecraftVersions.between(MinecraftVersion.V1_16_5, MinecraftVersion.V1_21_4))
 *     .force(false)             // no "start anyway" for this plugin
 *     .forceNewer(true));       // except above the newest declared version
 * registration.registerModule(MODULE_ID, setup -> setup.minecraft(mc -> mc
 *     .versions(MinecraftVersions.from(MinecraftVersion.V1_20_5))
 *     .whenNewer(NewerAction.ALLOW)));
 * }</pre>
 */
public interface MinecraftSupportDeclaration {
    /** Declares the Minecraft versions, and what to do outside them, with {@code setup}. */
    void minecraft(@NotNull Consumer<MinecraftSupportBuilder> setup);
}
