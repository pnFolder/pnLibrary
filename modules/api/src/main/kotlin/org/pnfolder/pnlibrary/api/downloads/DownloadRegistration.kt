package org.pnfolder.pnlibrary.api.downloads

import java.util.concurrent.CompletionStage

/** Live registration used to inspect and trigger a group of managed downloads. */
interface DownloadRegistration : AutoCloseable {
    /** Whether this registration has released its resources. */
    val isClosed: Boolean get() = false

    /** Returns the current status of every download in this registration. */
    fun snapshots(): List<DownloadSnapshot>

    /** Starts eligible downloads and completes with their resulting states. */
    fun downloadNow(): CompletionStage<List<DownloadSnapshot>>

    /** Cancels pending work and releases this registration. */
    override fun close()
}
