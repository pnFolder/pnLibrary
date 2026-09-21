package ru.privatenull.pnlibrary.api.plugin

import ru.privatenull.pnlibrary.api.updates.ExternalDependency
import ru.privatenull.pnlibrary.api.updates.ManagedDependency

/** One dependency accepted by [PluginBuilder.depends]. */
interface PluginDependency {
    /** Whether absence or an outdated version blocks plugin registration. */
    val required: Boolean get() = true

    /** Whether a verified artifact may be downloaded automatically when configured globally. */
    val automaticDownload: Boolean get() = false
    /** Whether this dependency explicitly overrides the global automatic-download switch. */
    val forceAutomaticDownload: Boolean get() = false
    /** Managed pnLibrary component, when this is a library component dependency. */
    val managed: ManagedDependency? get() = null

    /** Native/third-party plugin dependency, when this is an external dependency. */
    val external: ExternalDependency? get() = null
}

/** Short factories for the unified dependency DSL. */
object Dependencies {
    @JvmStatic
    @JvmOverloads
    fun managed(component: String, minimumVersion: String, repositoryOwner: String, repositoryName: String,
                required: Boolean = true, automaticDownload: Boolean = false,
                forceAutomaticDownload: Boolean = false): PluginDependency =
        ManagedDependency(component, minimumVersion, repositoryOwner, repositoryName, required, automaticDownload, forceAutomaticDownload)

    @JvmStatic
    @JvmOverloads
    fun plugin(plugin: String, minimumVersion: String, downloadPage: String,
               required: Boolean = true, automaticDownload: Boolean = false,
               forceAutomaticDownload: Boolean = false): PluginDependency =
        ExternalDependency.builder(plugin, minimumVersion).downloadPage(downloadPage)
            .required(required).automaticDownload(automaticDownload).forceAutomaticDownload(forceAutomaticDownload).build()

    @JvmStatic
    @JvmOverloads
    fun plugin(plugin: String, minimumVersion: String, downloadUrl: String, size: Long, sha256: String,
               required: Boolean = true, automaticDownload: Boolean = false,
               forceAutomaticDownload: Boolean = false): PluginDependency =
        ExternalDependency.builder(plugin, minimumVersion).artifact(downloadUrl, size, sha256)
            .required(required).automaticDownload(automaticDownload).forceAutomaticDownload(forceAutomaticDownload).build()
}
