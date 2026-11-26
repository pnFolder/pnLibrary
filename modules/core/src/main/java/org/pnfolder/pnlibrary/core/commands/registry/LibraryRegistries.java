package org.pnfolder.pnlibrary.core.commands.registry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.expansions.ExpansionManager;
import org.pnfolder.pnlibrary.api.registry.Registry;
import org.pnfolder.pnlibrary.api.registry.RegistryEntry;
import org.pnfolder.pnlibrary.api.registry.Visibility;
import org.pnfolder.pnlibrary.api.services.ServiceManager;
import org.pnfolder.pnlibrary.core.placeholders.format.FormatDef;
import org.pnfolder.pnlibrary.core.placeholders.PlaceholderEngine;
import org.pnfolder.pnlibrary.core.placeholders.RegistryEntries;
import org.pnfolder.pnlibrary.core.registry.ListRegistry;
import org.pnfolder.pnlibrary.core.scenario.ScenarioDefinitionInfo;

/** Everything the library keeps in lists, whole and not narrowed to one module, for {@code /pn registry}. */
public final class LibraryRegistries {
    private LibraryRegistries() {
    }

    /** The registries in the order {@code /pn registry} lists them. */
    @NotNull
    public static List<Registry<?>> of(@NotNull final PlaceholderEngine placeholders, @NotNull ServiceManager services,
            @NotNull ExpansionManager expansions, @NotNull Supplier<List<ScenarioDefinitionInfo>> definitions) {
        List<Registry<?>> registries = new ArrayList<Registry<?>>();
        registries.add(listing("placeholders", () -> {
            List<RegistryEntry<?>> entries = new ArrayList<RegistryEntry<?>>();
            for (org.pnfolder.pnlibrary.api.placeholders.info.PlaceholderInfo info : placeholders.list()) {
                entries.add(RegistryEntries.placeholder(info));
            }
            return entries;
        }));
        registries.add(listing("formats", () -> formats(placeholders)));
        registries.add(definitionsOf("actions", "action", definitions));
        registries.add(definitionsOf("requirements", "requirement", definitions));
        registries.add(definitionsOf("functions", "function", definitions));
        registries.add(services.registry());
        registries.add(expansions.registry());
        return registries;
    }

    private static List<RegistryEntry<?>> formats(PlaceholderEngine placeholders) {
        List<FormatDef> sorted = new ArrayList<FormatDef>(placeholders.getFormats().all());
        Collections.sort(sorted, (left, right) -> left.getName().compareTo(right.getName()));
        List<RegistryEntry<?>> entries = new ArrayList<RegistryEntry<?>>();
        for (FormatDef format : sorted) {
            entries.add(RegistryEntries.format(format));
        }
        return entries;
    }

    private static Registry<?> definitionsOf(String name, final String kind, final Supplier<List<ScenarioDefinitionInfo>> definitions) {
        return listing(name, () -> {
            List<RegistryEntry<?>> entries = new ArrayList<RegistryEntry<?>>();
            for (ScenarioDefinitionInfo info : definitions.get()) {
                if (info.getKind().equals(kind)) {
                    entries.add(new RegistryEntry<ScenarioDefinitionInfo>(info.getName(), info, info.getOwner(),
                            Visibility.valueOf(info.getVisibility().toUpperCase(Locale.ROOT))));
                }
            }
            return entries;
        });
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Registry<?> listing(String name, final Supplier<List<RegistryEntry<?>>> source) {
        return new ListRegistry(name, source);
    }
}
