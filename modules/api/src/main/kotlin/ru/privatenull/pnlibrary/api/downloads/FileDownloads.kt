package ru.privatenull.pnlibrary.api.downloads

import ru.privatenull.pnlibrary.api.platform.PlatformType
import java.net.URI
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Collections
import java.util.concurrent.CompletionStage
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

/**
 * Immutable status of one declared download.
 *
 * @property key application-defined download identifier
 * @property state current lifecycle state
 * @property message optional diagnostic or blocking explanation
 */
data class DownloadSnapshot(
    val key: String,
    val state: DownloadState,
    val message: String? = null
)

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

/** Root used to resolve a downloaded file's relative destination path. */
enum class DownloadDestination { DATA_FOLDER, CACHE }

/**
 * Verified HTTPS source and runtime compatibility constraints for one file.
 *
 * @property uri HTTPS URI from which the file is downloaded
 * @property platform optional platform restriction
 * @property minimumJava oldest supported Java feature version
 * @property maximumJava newest supported Java feature version, or `null` when unbounded
 * @property size expected byte length, when integrity metadata is available
 * @property sha256 expected lowercase SHA-256 digest, when available
 */
class DirectDownloadSource private constructor(builder: Builder) {
    val uri: URI = builder.uri ?: error("download URL is required")
    val platform: PlatformType? = builder.platform
    val minimumJava: Int = builder.minimumJava
    val maximumJava: Int? = builder.maximumJava
    val size: Long? = builder.size
    val sha256: String? = builder.sha256

    /** Returns whether this source supports [platform] on [javaFeature]. */
    fun supports(platform: PlatformType, javaFeature: Int): Boolean =
        (this.platform == null || this.platform == platform) && javaFeature >= minimumJava &&
            (maximumJava == null || javaFeature <= maximumJava)

    /** Fluent builder for a validated [DirectDownloadSource]. */
    class Builder internal constructor() {
        internal var uri: URI? = null
        internal var platform: PlatformType? = null
        internal var minimumJava = 8
        internal var maximumJava: Int? = null
        internal var size: Long? = null
        internal var sha256: String? = null

        /** Sets the required HTTPS download URL. */
        fun url(value: String) = apply {
            val parsed = URI.create(value)
            require(parsed.scheme.equals("https", true) && !parsed.host.isNullOrBlank()) { "download URL must use HTTPS" }
            uri = parsed
        }

        /** Restricts the source to [value]. */
        fun platform(value: PlatformType) = apply { platform = value }

        /** Restricts the source to the inclusive Java feature-version range. */
        @JvmOverloads
        fun java(minimum: Int, maximum: Int? = null) = apply {
            require(minimum >= 8 && (maximum == null || maximum >= minimum)) { "invalid Java range" }
            minimumJava = minimum
            maximumJava = maximum
        }

        /** Sets the expected [size] and SHA-256 digest used after download. */
        fun integrity(size: Long, sha256: String) = apply {
            require(size > 0) { "download size must be positive" }
            require(sha256.matches(Regex("[0-9a-fA-F]{64}"))) { "SHA-256 must contain 64 hexadecimal characters" }
            this.size = size
            this.sha256 = sha256.lowercase(java.util.Locale.ROOT)
        }

        /** Validates this configuration and creates an immutable source. */
        fun build() = DirectDownloadSource(this)
    }

    /** Creates source builders. */
    companion object {
        /** Returns an empty source builder. */
        @JvmStatic
        fun builder() = Builder()
    }
}

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
        fun required(value: Boolean) = apply { required = value }

        /** Enables or disables ordinary automatic downloading. */
        fun automaticDownload(value: Boolean) = apply { automatic = value }

        /** Enables or disables mandatory automatic downloading. */
        fun forceAutomaticDownload(value: Boolean) = apply { forceAutomatic = value }

        /** Sets the source HTTPS URL. */
        fun url(value: String) = apply { source.url(value) }

        /** Restricts the source to [value]. */
        fun platform(value: PlatformType) = apply { source.platform(value) }

        /** Restricts the source to the inclusive Java feature-version range. */
        @JvmOverloads
        fun java(minimum: Int, maximum: Int? = null) = apply { source.java(minimum, maximum) }

        /** Sets expected size and SHA-256 integrity metadata. */
        fun integrity(size: Long, sha256: String) = apply { source.integrity(size, sha256) }

        /** Selects the destination root and confined relative path. */
        fun destination(value: DownloadDestination, relativePath: String) = apply {
            destination = value
            this.relativePath = relativePath
        }

        internal fun build(): FileDownload {
            require(id.isNotBlank()) { "file download ID is required" }
            require(relativePath.isNotBlank()) { "destination path is required" }
            return FileDownload(this)
        }
    }

    /** Internal path-validation utilities. */
    companion object {
        private fun safeRelativePath(value: String): String {
            val path = Paths.get(value).normalize()
            require(!path.isAbsolute && path.nameCount > 0 && !path.startsWith("..")) {
                "download destination must stay inside its root"
            }
            return path.toString().replace('\\', '/')
        }
    }
}

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
        fun dataDirectory(value: Path) = apply { dataDirectory = value }

        /** Declares and validates a file under the unique [id]. */
        fun file(id: String, configure: Consumer<FileDownload.Builder>) = apply {
            val builder = FileDownload.Builder(id)
            configure.accept(builder)
            val file = builder.build()
            require(files.none { it.key.equals(file.key, true) }) { "duplicate file download: ${file.key}" }
            files += file
        }

        /** Validates this declaration and creates an immutable download unit. */
        fun build(): FileDownloads {
            require(files.isNotEmpty()) { "at least one file download is required" }
            return FileDownloads(this)
        }
    }

    /** Creates download-unit builders. */
    companion object {
        /** Returns an empty download-unit builder. */
        @JvmStatic
        fun builder() = Builder()
    }
}
