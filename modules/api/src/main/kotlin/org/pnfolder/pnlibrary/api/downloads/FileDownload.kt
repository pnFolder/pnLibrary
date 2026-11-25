package org.pnfolder.pnlibrary.api.downloads

import org.pnfolder.pnlibrary.api.downloads.source.DownloadDestination
import org.pnfolder.pnlibrary.api.downloads.source.DirectDownloadSource
import org.pnfolder.pnlibrary.api.platform.PlatformType
import java.nio.file.Paths

/**
 * Declarative description of one file managed by the download service.
 *
 * @property key application-defined download identifier
 * @property source verified remote source and compatibility constraints
 * @property required whether failure to obtain this file blocks its owner
 * @property policy whether the file is downloaded by itself or only when an administrator asks
 * @property destination root used to resolve [relativePath]
 * @property relativePath normalized path confined to the selected destination
 */
class FileDownload internal constructor(builder: Builder) {
    val key: String = builder.id.trim()
    val source: DirectDownloadSource = builder.source.build()
    val required: Boolean = builder.required
    val policy: DownloadPolicy = builder.policy
    val destination: DownloadDestination = builder.destination
    val relativePath: String = safeRelativePath(builder.relativePath)

    /** Fluent builder for a validated [FileDownload]. */
    class Builder internal constructor(internal val id: String) {
        internal val source = DirectDownloadSource.builder()
        internal var required = true
        internal var policy = DownloadPolicy.MANUAL
        internal var destination = DownloadDestination.DATA_FOLDER
        internal var relativePath = ""

        /** Lets the owner work without this file: a failed download does not stop the whole batch. */
        fun optional(): Builder = apply { required = false }

        /** Whether the file is downloaded by itself ([DownloadPolicy.AUTOMATIC], [DownloadPolicy.FORCED]) or only when asked; [DownloadPolicy.MANUAL] by default. */
        fun policy(value: DownloadPolicy): Builder = apply { policy = value }

        /** Sets the HTTPS URL to download from. */
        fun url(value: String): Builder = apply { source.url(value) }

        /** Adds another HTTPS address of the same file; tried in order when the one before does not answer. */
        fun mirror(value: String): Builder = apply { source.mirror(value) }

        /** Restricts the source to [value]. */
        fun platform(value: PlatformType): Builder = apply { source.platform(value) }

        /** Restricts the source to the inclusive Java feature-version range. */
        @JvmOverloads
        fun java(minimum: Int, maximum: Int? = null): Builder = apply { source.java(minimum, maximum) }

        /** Sets the expected size and SHA-256 checksum the downloaded file must match. */
        fun integrity(size: Long, sha256: String): Builder = apply { source.integrity(size, sha256) }

        /** Sets the largest accepted size of this file in bytes; without it the library-wide limit applies. */
        fun maxBytes(value: Long): Builder = apply { source.maxBytes(value) }

        /** Sets the SHA-256 checksum the downloaded file must match. */
        fun sha256(value: String): Builder = apply { source.sha256(value) }

        /** Selects the destination root and confined relative path. */
        fun destination(value: DownloadDestination, relativePath: String): Builder = apply {
            destination = value
            this.relativePath = relativePath
        }

        internal fun build(): FileDownload {
            require(id.isNotBlank()) { "File download ID is required" }
            require(relativePath.isNotBlank()) { "Download destination path is required" }
            return FileDownload(this)
        }
    }

    /** Internal path-validation utilities. */
    companion object {
        private fun safeRelativePath(value: String): String {
            val path = Paths.get(value).normalize()
            require(!path.isAbsolute && path.nameCount > 0 && !path.startsWith("..")) {
                "Download destination must stay inside its root"
            }
            return path.toString().replace('\\', '/')
        }
    }
}
