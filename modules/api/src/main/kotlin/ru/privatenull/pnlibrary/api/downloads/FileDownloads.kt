package ru.privatenull.pnlibrary.api.downloads

import ru.privatenull.pnlibrary.api.platform.PlatformType
import java.net.URI
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Collections
import java.util.concurrent.CompletionStage
import java.util.function.Consumer

enum class DownloadState { DECLARED, CURRENT, DOWNLOADING, STAGED, BLOCKED, FAILED, CLOSED }

data class DownloadSnapshot(val key: String, val state: DownloadState, val message: String? = null)

interface DownloadRegistration : AutoCloseable {
    val isClosed: Boolean get() = false
    fun snapshots(): List<DownloadSnapshot>
    fun downloadNow(): CompletionStage<List<DownloadSnapshot>>
    override fun close()
}

enum class DownloadDestination { DATA_FOLDER, CACHE }

class DirectDownloadSource private constructor(builder: Builder) {
    val uri: URI = builder.uri ?: error("download URL is required")
    val platform: PlatformType? = builder.platform
    val minimumJava: Int = builder.minimumJava
    val maximumJava: Int? = builder.maximumJava
    val size: Long? = builder.size
    val sha256: String? = builder.sha256

    fun supports(platform: PlatformType, javaFeature: Int): Boolean =
        (this.platform == null || this.platform == platform) && javaFeature >= minimumJava &&
            (maximumJava == null || javaFeature <= maximumJava)

    class Builder internal constructor() {
        internal var uri: URI? = null
        internal var platform: PlatformType? = null
        internal var minimumJava = 8
        internal var maximumJava: Int? = null
        internal var size: Long? = null
        internal var sha256: String? = null

        fun url(value: String) = apply {
            val parsed = URI.create(value)
            require(parsed.scheme.equals("https", true) && !parsed.host.isNullOrBlank()) { "download URL must use HTTPS" }
            uri = parsed
        }
        fun platform(value: PlatformType) = apply { platform = value }
        @JvmOverloads fun java(minimum: Int, maximum: Int? = null) = apply {
            require(minimum >= 8 && (maximum == null || maximum >= minimum)) { "invalid Java range" }
            minimumJava = minimum
            maximumJava = maximum
        }
        fun integrity(size: Long, sha256: String) = apply {
            require(size > 0) { "download size must be positive" }
            require(sha256.matches(Regex("[0-9a-fA-F]{64}"))) { "SHA-256 must contain 64 hexadecimal characters" }
            this.size = size
            this.sha256 = sha256.lowercase(java.util.Locale.ROOT)
        }
        fun build() = DirectDownloadSource(this)
    }

    companion object { @JvmStatic fun builder() = Builder() }
}

class FileDownload internal constructor(builder: Builder) {
    val key: String = builder.id.trim()
    val source: DirectDownloadSource = builder.source.build()
    val required: Boolean = builder.required
    val automatic: Boolean = builder.automatic
    val forceAutomaticDownload: Boolean = builder.forceAutomatic
    val destination: DownloadDestination = builder.destination
    val relativePath: String = safeRelativePath(builder.relativePath)

    class Builder internal constructor(internal val id: String) {
        internal val source = DirectDownloadSource.builder()
        internal var required = true
        internal var automatic = false
        internal var forceAutomatic = false
        internal var destination = DownloadDestination.DATA_FOLDER
        internal var relativePath = ""

        fun required(value: Boolean) = apply { required = value }
        fun automaticDownload(value: Boolean) = apply { automatic = value }
        fun forceAutomaticDownload(value: Boolean) = apply { forceAutomatic = value }
        fun url(value: String) = apply { source.url(value) }
        fun platform(value: PlatformType) = apply { source.platform(value) }
        @JvmOverloads fun java(minimum: Int, maximum: Int? = null) = apply { source.java(minimum, maximum) }
        fun integrity(size: Long, sha256: String) = apply { source.integrity(size, sha256) }
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

class FileDownloads private constructor(builder: Builder) {
    val dataDirectory: Path? = builder.dataDirectory?.toAbsolutePath()?.normalize()
    val files: List<FileDownload> = Collections.unmodifiableList(builder.files.toList())

    class Builder internal constructor() {
        internal var dataDirectory: Path? = null
        internal val files = mutableListOf<FileDownload>()

        fun dataDirectory(value: Path) = apply { dataDirectory = value }
        fun file(id: String, configure: Consumer<FileDownload.Builder>) = apply {
            val builder = FileDownload.Builder(id)
            configure.accept(builder)
            val file = builder.build()
            require(files.none { it.key.equals(file.key, true) }) { "duplicate file download: ${file.key}" }
            files += file
        }
        fun build(): FileDownloads {
            require(files.isNotEmpty()) { "at least one file download is required" }
            return FileDownloads(this)
        }
    }

    companion object { @JvmStatic fun builder() = Builder() }
}
