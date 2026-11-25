package org.pnfolder.pnlibrary.core.commands.registry

import org.pnfolder.pnlibrary.core.registry.ListRegistry
import org.pnfolder.pnlibrary.api.expansions.ExpansionManager
import org.pnfolder.pnlibrary.api.registry.Registry
import org.pnfolder.pnlibrary.api.registry.RegistryEntry
import org.pnfolder.pnlibrary.api.registry.Visibility
import org.pnfolder.pnlibrary.api.services.ServiceManager
import org.pnfolder.pnlibrary.core.placeholders.PlaceholderEngine
import org.pnfolder.pnlibrary.core.placeholders.asEntry
import org.pnfolder.pnlibrary.core.scenario.ScenarioDefinitionInfo

/** Everything the library keeps in lists, whole and not narrowed to one module, for `/pn registry`. */
internal fun libraryRegistries(
    placeholders: PlaceholderEngine,
    services: ServiceManager,
    expansions: ExpansionManager,
    definitions: () -> List<ScenarioDefinitionInfo>,
): List<Registry<*>> = listOf(
    ListRegistry("placeholders") { placeholders.list().map { it.asEntry() } },
    ListRegistry("formats") { placeholders.formats.all().sortedBy { it.name }.map { it.asEntry() } },
    definitionsOf("actions", "action", definitions),
    definitionsOf("requirements", "requirement", definitions),
    definitionsOf("functions", "function", definitions),
    services.registry(),
    expansions.registry(),
)

private fun definitionsOf(name: String, kind: String, definitions: () -> List<ScenarioDefinitionInfo>) =
    ListRegistry(name) {
        definitions().filter { it.kind == kind }
            .map { RegistryEntry(it.name, it, it.owner, Visibility.valueOf(it.visibility.uppercase())) }
    }
