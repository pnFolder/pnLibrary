package org.pnfolder.pnlibrary.api.downloads

import org.pnfolder.pnlibrary.api.downloads.source.DownloadDestination
import java.nio.file.Path
import java.util.Collections
import java.util.function.Consumer

/**
 * Immutable collection of files registered as one download unit.
 *
 * @property dataDirectory optional plugin data-directory override
 * @property files files owned by this unit
 */
class FileDownloads private constructor(builder: Builder) {
    val dataDirectory: Path? = builder.dataDirectory?.toAbsolutePath()?.normalize()
    val files: List<FileDownload> = Collections.unmodifiableList(builder.files.toList())

    /** Fluent builder for a [FileDownloads] declaration. */
    class Builder internal constructor() {
        internal var dataDirectory: Path? = null
        internal val files = mutableListOf<FileDownload>()

        /** Overrides the plugin data directory used by data-folder destinations. */
        fun dataDirectory(value: Path): Builder = apply { dataDirectory = value }

        /** Declares and validates a file under the unique [id]. */
        fun file(id: String, configure: Consumer<FileDownload.Builder>): Builder = apply {
            val builder = FileDownload.Builder(id)
            configure.accept(builder)
            val file = builder.build()
            require(files.none { it.key.equals(file.key, true) }) { "Duplicate file download: ${file.key}" }
            files += file
        }

        /** Declares a file under [id] that is downloaded from [url] to [relativePath] in the data folder, with everything else as usual. */
        fun file(id: String, url: String, relativePath: String): Builder =
            file(id) { it.url(url).destination(DownloadDestination.DATA_FOLDER, relativePath) }

        /** Validates this declaration and creates an immutable download unit. */
        fun build(): FileDownloads {
            require(files.isNotEmpty()) { "At least one file download is required" }
            return FileDownloads(this)
        }
    }

    companion object {
        /** Creates a new [Builder]. */
        @JvmStatic
        fun builder(): Builder = Builder()
    }
}
