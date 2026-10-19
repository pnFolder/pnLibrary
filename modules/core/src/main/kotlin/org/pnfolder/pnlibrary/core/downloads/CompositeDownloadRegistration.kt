package org.pnfolder.pnlibrary.core.downloads

import org.pnfolder.pnlibrary.api.downloads.DownloadRegistration
import org.pnfolder.pnlibrary.api.downloads.DownloadSnapshot
import org.pnfolder.pnlibrary.core.lifecycle.ResourceCleanup
import java.util.concurrent.CompletionStage

/** Presents two download registrations, such as declared files and dependencies, as one. */
internal class CompositeDownloadRegistration private constructor(
    private val first: DownloadRegistration,
    private val second: DownloadRegistration,
) : DownloadRegistration {
    override val isClosed: Boolean
        get() = first.isClosed && second.isClosed

    override fun snapshots(): List<DownloadSnapshot> = first.snapshots() + second.snapshots()

    override fun downloadNow(): CompletionStage<List<DownloadSnapshot>> =
        first.downloadNow().thenCombine(second.downloadNow()) { firstResult, secondResult ->
            firstResult + secondResult
        }

    override fun close() {
        ResourceCleanup.closeAll({ first.close() }, { second.close() })
    }

    companion object {
        fun combine(
            first: DownloadRegistration?,
            second: DownloadRegistration?,
        ): DownloadRegistration? = when {
            first == null -> second
            second == null -> first
            else -> CompositeDownloadRegistration(first, second)
        }
    }
}
