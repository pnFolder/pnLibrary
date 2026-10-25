package org.pnfolder.pnlibrary.api.downloads

import org.pnfolder.pnlibrary.api.platform.PlatformType
import java.nio.file.Paths

/**
 * Declarative description of one file managed by the download service.
 *
 * @property key application-defined download identifier
 * @property source verified remote source and compatibility constraints
 * @property required whether failure to obtain this file blocks its owner
 * @property automatic whether normal automatic download is enabled
 * @property forceAutomaticDownload whether automatic download is mandatory
 * @property destination root used to resolve [relativePath]
 * @property relativePath normalized path confined to the selected destination
 */
class FileDownload internal constructor(builder: Builder) {
    val key: String = builder.id.trim()
    val source: DirectDownloadSource = builder.source.build()
    val required: Boolean = builder.required
    val automatic: Boolean = builder.automatic
    val forceAutomaticDownload: Boolean = builder.forceAutomatic
    val destination: DownloadDestination = builder.destination
    val relativePath: String = safeRelativePath(builder.relativePath)

    /** Fluent builder for a validated [FileDownload]. */
    class Builder internal constructor(internal val id: String) {
        internal val source = DirectDownloadSource.builder()
        internal var required = true
        internal var automatic = false
        internal var forceAutomatic = false
        internal var destination = DownloadDestination.DATA_FOLDER
        internal var relativePath = ""

        /** Sets whether this file is required for its owner to operate. */
        fun required(value: Boolean): Builder = apply { required = value }

        /** Enables or disables ordinary automatic downloading. */
        fun automaticDownload(value: Boolean): Builder = apply { automatic = value }

        /** Enables or disables mandatory automatic downloading. */
        fun forceAutomaticDownload(value: Boolean): Builder = apply { forceAutomatic = value }

        /** Sets the source HTTPS URL. */
        fun url(value: String): Builder = apply { source.url(value) }

        /** Restricts the source to [value]. */
        fun platform(value: PlatformType): Builder = apply { source.platform(value) }

        /** Restricts the source to the inclusive Java feature-version range. */
        @JvmOverloads
        fun java(minimum: Int, maximum: Int? = null): Builder = apply { source.java(minimum, maximum) }

        /** Sets expected size and SHA-256 integrity metadata. */
        fun integrity(size: Long, sha256: String): Builder = apply { source.integrity(size, sha256) }

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
