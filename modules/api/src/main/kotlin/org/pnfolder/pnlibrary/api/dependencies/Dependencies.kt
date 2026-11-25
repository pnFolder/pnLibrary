package org.pnfolder.pnlibrary.api.dependencies

import org.pnfolder.pnlibrary.api.downloads.DownloadPolicy
import org.pnfolder.pnlibrary.api.dependencies.external.ExternalPluginDependency
import org.pnfolder.pnlibrary.api.dependencies.external.ManagedProductDependency

/** Short factories for the unified dependency DSL. */
object Dependencies {
    /** Declares a pnLibrary-managed product dependency downloaded from a GitHub repository. */
    @JvmStatic
    @JvmOverloads
    fun managed(
        component: String,
        minimumVersion: String,
        repositoryOwner: String,
        repositoryName: String,
        required: Boolean = true,
        downloadPolicy: DownloadPolicy = DownloadPolicy.MANUAL,
    ): PluginDependency = ManagedProductDependency(
        component,
        minimumVersion,
        repositoryOwner,
        repositoryName,
        required,
        downloadPolicy,
    )

    /** Declares an external plugin dependency with a manual download page. */
    @JvmStatic
    @JvmOverloads
    fun plugin(
        plugin: String,
        minimumVersion: String,
        downloadPage: String,
        required: Boolean = true,
        downloadPolicy: DownloadPolicy = DownloadPolicy.MANUAL,
    ): PluginDependency = ExternalPluginDependency.builder(plugin, minimumVersion)
        .downloadPage(downloadPage)
        .required(required)
        .downloadPolicy(downloadPolicy)
        .build()

    /** Declares an external plugin dependency with a direct download checked by its SHA-256. */
    @JvmStatic
    @JvmOverloads
    fun plugin(
        plugin: String,
        minimumVersion: String,
        downloadUrl: String,
        sha256: String,
        required: Boolean = true,
        downloadPolicy: DownloadPolicy = DownloadPolicy.AUTOMATIC,
    ): PluginDependency = ExternalPluginDependency.builder(plugin, minimumVersion)
        .artifact(downloadUrl, sha256)
        .required(required)
        .downloadPolicy(downloadPolicy)
        .build()

    /**
     * Declares an external plugin with several download addresses and no checksum. The first address is tried first,
     * the others are mirrors tried in order when the one before does not answer; the file is taken as it came.
     */
    @JvmStatic
    @JvmOverloads
    fun plugin(
        plugin: String,
        minimumVersion: String,
        sources: List<String>,
        required: Boolean = true,
        downloadPolicy: DownloadPolicy = DownloadPolicy.AUTOMATIC,
    ): PluginDependency {
        require(sources.isNotEmpty()) { "At least one download address is required" }
        return ExternalPluginDependency.builder(plugin, minimumVersion)
            .url(sources.first())
            .also { builder -> sources.drop(1).forEach(builder::mirror) }
            .required(required)
            .downloadPolicy(downloadPolicy)
            .build()
    }
}
