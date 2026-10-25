package org.pnfolder.pnlibrary.core.downloads

import org.pnfolder.pnlibrary.api.dependencies.PluginDependency
import org.pnfolder.pnlibrary.api.downloads.DownloadRegistration
import org.pnfolder.pnlibrary.api.downloads.FileDownloads

/** Registration and lifecycle boundary used by plugin orchestration. */
internal interface DownloadService : AutoCloseable {
    fun register(owner: Any, request: FileDownloads): DownloadRegistration
    fun registerDependencies(owner: Any, dependencies: List<PluginDependency>): DownloadRegistration?
}
