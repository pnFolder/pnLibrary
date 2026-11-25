package org.pnfolder.pnlibrary.api.downloads

import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.CompletionStage
import java.util.function.Consumer

/** Live registration used to inspect and trigger a group of managed downloads. */
@ApiStatus.NonExtendable
interface DownloadRegistration : AutoCloseable {
    /** Whether this registration has released its resources. */
    val isClosed: Boolean get() = false

    /** Returns the current status of every download in this registration. */
    fun snapshots(): List<DownloadSnapshot>

    /** Starts eligible downloads and completes with their resulting states. */
    fun downloadNow(): CompletionStage<List<DownloadSnapshot>>

    /**
     * Calls [handler] with the resulting states each time a download of this registration ends, however it started:
     * by an administrator, by [downloadNow], or by the library on its own. Use it instead of polling [snapshots].
     */
    fun whenFinished(handler: Consumer<List<DownloadSnapshot>>)

    /** Cancels pending work and releases this registration. */
    override fun close()
}
