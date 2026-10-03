package ru.privatenull.pnlibrary.core.downloads

import ru.privatenull.pnlibrary.api.downloads.DownloadRegistration
import ru.privatenull.pnlibrary.api.downloads.DownloadSnapshot
import java.util.concurrent.CompletionStage

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
        first.close()
        second.close()
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
