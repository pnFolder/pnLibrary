package org.pnfolder.pnlibrary.api.downloads

import java.nio.file.Path
import java.util.Collections
import java.util.function.Consumer

/** Lifecycle state reported for one managed file download. */
enum class DownloadState {
    DECLARED,
    CURRENT,
    DOWNLOADING,
    STAGED,
    BLOCKED,
    FAILED,
    CLOSED
}

/** Root used to resolve a downloaded file's relative destination path. */
enum class DownloadDestination { DATA_FOLDER, CACHE }

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

        /** Validates this declaration and creates an immutable download unit. */
        fun build(): FileDownloads {
            require(files.isNotEmpty()) { "At least one file download is required" }
            return FileDownloads(this)
        }
    }

    /** Creates download-unit builders. */
    companion object {
        /** Returns an empty download-unit builder. */
        @JvmStatic
        fun builder(): Builder = Builder()
    }
}
