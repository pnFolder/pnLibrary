package ru.privatenull.pnlibrary.api.plugin

import ru.privatenull.pnlibrary.api.updates.ExternalDependency
import ru.privatenull.pnlibrary.api.updates.ManagedDependency

/** One dependency accepted by [PluginBuilder.depends]. */
interface PluginDependency {
    /** Managed pnLibrary component, when this is a library component dependency. */
    val managed: ManagedDependency? get() = null

    /** Native/third-party plugin dependency, when this is an external dependency. */
    val external: ExternalDependency? get() = null
}

/** Short factories for the unified dependency DSL. */
object Dependencies {
    @JvmStatic
    fun managed(component: String, minimumVersion: String, repositoryOwner: String, repositoryName: String): PluginDependency =
        ManagedDependency(component, minimumVersion, repositoryOwner, repositoryName)

    @JvmStatic
    fun plugin(plugin: String, minimumVersion: String, downloadPage: String): PluginDependency =
        ExternalDependency.builder(plugin, minimumVersion).downloadPage(downloadPage).build()

    @JvmStatic
    fun plugin(plugin: String, minimumVersion: String, downloadUrl: String, size: Long, sha256: String): PluginDependency =
        ExternalDependency.builder(plugin, minimumVersion).artifact(downloadUrl, size, sha256).build()
}
