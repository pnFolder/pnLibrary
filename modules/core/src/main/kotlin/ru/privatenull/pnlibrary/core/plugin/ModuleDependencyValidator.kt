package ru.privatenull.pnlibrary.core.plugin

import ru.privatenull.pnlibrary.api.plugin.PluginDependency
import ru.privatenull.pnlibrary.api.updates.ProductDescriptor
import ru.privatenull.pnlibrary.api.version.SemanticVersion
import ru.privatenull.pnlibrary.spi.platform.PlatformAdapter

/** Checks that all required managed and native plugin dependencies can be satisfied. */
internal class ModuleDependencyValidator(
    private val platform: PlatformAdapter,
    private val downloadsAvailable: () -> Boolean,
) {
    fun validate(
        dependencies: List<PluginDependency>,
        registeredProducts: Map<String, ProductDescriptor>,
    ) {
        val problems = mutableListOf<String>()
        collectManagedProblems(dependencies, registeredProducts, problems)
        collectExternalProblems(dependencies, problems)

        require(problems.isEmpty()) {
            "Unsatisfied pnLibrary component dependencies: ${problems.joinToString("; ")}"
        }
    }

    private fun collectManagedProblems(
        dependencies: List<PluginDependency>,
        registeredProducts: Map<String, ProductDescriptor>,
        problems: MutableList<String>,
    ) {
        dependencies.mapNotNull(PluginDependency::managed)
            .filter { it.required }
            .forEach { dependency ->
                val installed = registeredProducts[dependency.product.value]
                when {
                    installed == null -> problems +=
                        "${dependency.product} >= ${dependency.minimumVersion} is missing " +
                            "(${dependency.repositoryOwner}/${dependency.repositoryName})"

                    installed.version < dependency.minimumVersion -> problems +=
                        "${dependency.product} ${installed.version} is installed, " +
                            "${dependency.minimumVersion} is required"
                }
            }
    }

    private fun collectExternalProblems(
        dependencies: List<PluginDependency>,
        problems: MutableList<String>,
    ) {
        val installedPlugins = runCatching(platform::installedPlugins)
            .getOrNull()
            .orEmpty()
            .mapKeys { (name, _) -> name.lowercase() }

        dependencies.mapNotNull(PluginDependency::external)
            .filter { it.required }
            .forEach { dependency ->
                val installedVersion = installedPlugins[dependency.plugin.lowercase()]
                val automaticDownloadPossible = downloadsAvailable() &&
                    dependency.automaticDownload &&
                    dependency.artifact != null

                if (installedVersion == null) {
                    if (!automaticDownloadPossible) {
                        problems += "${dependency.plugin} >= ${dependency.minimumVersion} is missing " +
                            "(${dependency.downloadPage ?: "no download page"})"
                    }
                    return@forEach
                }

                val parsedVersion = SemanticVersion.tryParse(installedVersion)
                val supported = parsedVersion?.let(dependency.versions::accepts) == true
                if (!supported && !automaticDownloadPossible) {
                    problems += "${dependency.plugin} $installedVersion is installed, " +
                        "${dependency.minimumVersion} is required"
                }
            }
    }
}
