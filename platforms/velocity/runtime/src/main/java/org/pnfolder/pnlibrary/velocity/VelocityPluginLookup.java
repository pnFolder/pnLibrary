package org.pnfolder.pnlibrary.velocity;

import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.plugin.PluginDescription;
import com.velocitypowered.api.plugin.PluginManager;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.spi.platform.PluginSnapshot;

/** The questions the adapter asks about installed Velocity plugins, in the terms of the shared platform contract. */
final class VelocityPluginLookup {
    private final PluginManager manager;

    VelocityPluginLookup(@NotNull PluginManager manager) {
        this.manager = manager;
    }

    Collection<PluginContainer> all() {
        return manager.getPlugins();
    }

    /** The container of the plugin whose main object is {@code instance}. */
    @Nullable PluginContainer containerOf(@NotNull Object instance) {
        return manager.fromInstance(instance).orElse(null);
    }

    /** The main object of the plugin with this id (any letter case), the id a plugin's logger is named after. */
    @Nullable Object instanceById(@NotNull String id) {
        return manager.getPlugin(id.toLowerCase(Locale.ROOT)).flatMap(PluginContainer::getInstance).orElse(null);
    }

    Collection<Object> instances() {
        List<Object> instances = new ArrayList<>();
        for (PluginContainer container : all()) {
            container.getInstance().ifPresent(instances::add);
        }
        return instances;
    }

    @Nullable PluginSnapshot describe(@Nullable PluginContainer container) {
        if (container == null) {
            return null;
        }
        PluginDescription description = container.getDescription();
        return new PluginSnapshot(description.getId(), description.getName().orElse(description.getId()),
                description.getVersion().orElse("unknown"), description.getAuthors());
    }
}
