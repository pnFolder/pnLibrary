package org.pnfolder.demo;

import org.bukkit.plugin.java.JavaPlugin;
import org.pnfolder.pnlibrary.api.plugin.ModuleContext;
import org.pnfolder.pnlibrary.api.plugin.PluginRegistration;
import org.pnfolder.pnlibrary.api.runtime.PnLibrary;
import org.pnfolder.pnlibrary.api.runtime.PnLibraryProvider;
import org.pnfolder.pnlibrary.api.updates.ProductDescriptor;

/**
 * Everything that touches the pnLibrary API. It lives in its own class so the plugin can load, and install pnLibrary,
 * before any of these classes are needed.
 */
final class DemoRuntime {
    private final PluginRegistration registration;

    private DemoRuntime(PluginRegistration registration) {
        this.registration = registration;
    }

    /** Registers the plugin with pnLibrary and prints its enabled card; {@code null} when pnLibrary has no runtime. */
    static DemoRuntime start(JavaPlugin plugin) {
        PnLibrary library = PnLibraryProvider.getOrNull();
        if (library == null) return null;
        PluginRegistration registration = library.getPlugins().register(plugin);
        ModuleContext context = registration.registerModule("demo", builder -> builder.product(
                ProductDescriptor.builder().version(plugin.getDescription().getVersion()).pnLibraryApi(1, 2).build()));
        context.getLifecycle().enabled()
                .ok("Runtime", library.getVersion())
                .ok("Module", context.getId().getValue())
                .show();
        return new DemoRuntime(registration);
    }

    void close() {
        registration.close();
    }
}
