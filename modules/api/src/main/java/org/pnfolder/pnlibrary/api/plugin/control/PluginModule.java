package org.pnfolder.pnlibrary.api.plugin.control;

import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.libraries.ModuleLibrary;
import org.pnfolder.pnlibrary.api.plugin.ModuleContext;

/**
 * What a plugin gives one of its modules so that the library, an administrator's command or the menu can switch it.
 *
 * <p>Every method has an empty default, so a module overrides only what it needs. The {@link ModuleContext} is the
 * module's own, the same one {@code registerModule} returned, so a hook reaches the logger, configuration, services
 * and everything else from it.
 *
 * <pre>{@code
 * public final class Shop implements PluginModule {
 *     public void onEnable(ModuleContext context) {
 *         context.registerCommand(...);
 *     }
 * }
 * registration.registerModule("shop", new Shop());
 * }</pre>
 *
 * <p>When a module is disabled the library cancels its tasks and removes its commands after {@link #onDisable}, so
 * those need no clean-up. Listeners, open files and anything else the module opened itself belong in
 * {@link #onDisable}.
 */
public interface PluginModule {
    /**
     * The Maven libraries this module needs, with where to download them and how to check them. They are loaded before
     * {@link #onEnable} each time the module starts; one already loaded is not downloaded again. A required library
     * that cannot be loaded leaves the module in {@link ModuleState#FAILED} with the reason.
     */
    @NotNull
    default List<ModuleLibrary> libraries() {
        return Collections.emptyList();
    }

    /** Starts the module: registers its commands, listeners and tasks. Runs at registration and on every enable. */
    default void onEnable(@NotNull ModuleContext context) {
    }

    /** Stops the module and releases what it opened itself. Runs on disable, on restart and at plugin shutdown. */
    default void onDisable(@NotNull ModuleContext context) {
    }

    /**
     * Re-reads the module's settings without stopping it. A module that cannot do this in place returns
     * {@code false}, and the library restarts it instead.
     */
    default boolean onReload(@NotNull ModuleContext context) {
        return false;
    }
}
