package ru.privatenull.pnlibrary.api.downloads

import ru.privatenull.pnlibrary.api.platform.PlatformType
import ru.privatenull.pnlibrary.api.updates.ComponentId
import ru.privatenull.pnlibrary.api.version.ApiVersionRange
import ru.privatenull.pnlibrary.api.version.SemanticVersion
import java.net.URI
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Collections
import java.util.function.Consumer
import java.util.concurrent.CompletionStage

enum class DownloadState { DECLARED, CURRENT, DOWNLOADING, STAGED, BLOCKED, FAILED, CLOSED }

data class DownloadSnapshot(val key: String, val state: DownloadState, val message: String? = null)

interface DownloadRegistration : AutoCloseable {
    fun snapshots(): List<DownloadSnapshot>
    fun downloadNow(): CompletionStage<List<DownloadSnapshot>>
    override fun close()
}

enum class DownloadDestination { PLUGINS, DATA_FOLDER, CACHE }

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
            minimumJava = minimum; maximumJava = maximum
        }
        fun integrity(size: Long, sha256: String) = apply {
            require(size > 0) { "download size must be positive" }
            require(sha256.matches(Regex("[0-9a-fA-F]{64}"))) { "SHA-256 must contain 64 hexadecimal characters" }
            this.size = size; this.sha256 = sha256.lowercase()
        }
        fun build() = DirectDownloadSource(this)
    }

    companion object { @JvmStatic fun builder() = Builder() }
}

sealed class DownloadDeclaration(
    val key: String,
    val source: DirectDownloadSource,
    val required: Boolean,
    val automatic: Boolean,
) {
    class Component internal constructor(builder: ComponentBuilder) : DownloadDeclaration(
        ComponentId.of(builder.id).value, builder.source.build(), builder.required, builder.automatic,
    ) {
        val component: ComponentId = ComponentId.of(builder.id)
        val version: SemanticVersion = SemanticVersion.parse(builder.version)
        val supportedApi: ApiVersionRange = builder.supportedApi ?: error("component API range is required")
    }

    class Plugin internal constructor(builder: PluginBuilder) : DownloadDeclaration(
        builder.name.trim(), builder.source.build(), builder.required, builder.automatic,
    ) {
        val plugin: String = builder.name.trim()
        val minimumVersion: SemanticVersion = SemanticVersion.parse(builder.minimumVersion)
    }

    class File internal constructor(builder: FileBuilder) : DownloadDeclaration(
        builder.id.trim(), builder.source.build(), builder.required, builder.automatic,
    ) {
        val destination: DownloadDestination = builder.destination
        val relativePath: String = safeRelativePath(builder.relativePath)
    }

    abstract class BaseBuilder<T : BaseBuilder<T>> {
        internal val source = DirectDownloadSource.builder()
        internal var required = true
        internal var automatic = false
        @Suppress("UNCHECKED_CAST") fun required(value: Boolean) = apply { required = value } as T
        @Suppress("UNCHECKED_CAST") fun automaticDownload(value: Boolean) = apply { automatic = value } as T
        @Suppress("UNCHECKED_CAST") fun url(value: String) = apply { source.url(value) } as T
        @Suppress("UNCHECKED_CAST") fun platform(value: PlatformType) = apply { source.platform(value) } as T
        @Suppress("UNCHECKED_CAST") @JvmOverloads fun java(minimum: Int, maximum: Int? = null) =
            apply { source.java(minimum, maximum) } as T
        @Suppress("UNCHECKED_CAST") fun integrity(size: Long, sha256: String) =
            apply { source.integrity(size, sha256) } as T
    }

    class ComponentBuilder internal constructor(internal val id: String) : BaseBuilder<ComponentBuilder>() {
        internal var version = ""
        internal var supportedApi: ApiVersionRange? = null
        fun version(value: String) = apply { version = value }
        fun apiVersion(value: Int) = apiVersions(value, value)
        fun apiVersions(minimum: Int, maximum: Int) = apply { supportedApi = ApiVersionRange(minimum, maximum) }
        internal fun build(): Component { require(version.isNotBlank()) { "component version is required" }; return Component(this) }
    }

    class PluginBuilder internal constructor(internal val name: String) : BaseBuilder<PluginBuilder>() {
        internal var minimumVersion = ""
        fun minimumVersion(value: String) = apply { minimumVersion = value }
        internal fun build(): Plugin { require(name.isNotBlank()); require(minimumVersion.isNotBlank()) { "minimum plugin version is required" }; return Plugin(this) }
    }

    class FileBuilder internal constructor(internal val id: String) : BaseBuilder<FileBuilder>() {
        internal var destination = DownloadDestination.DATA_FOLDER
        internal var relativePath = ""
        fun destination(value: DownloadDestination, relativePath: String) = apply {
            destination = value; this.relativePath = relativePath
        }
        internal fun build(): File { require(id.isNotBlank()); require(relativePath.isNotBlank()) { "destination path is required" }; return File(this) }
    }

    companion object {
        private fun safeRelativePath(value: String): String {
            val path = Paths.get(value).normalize()
            require(!path.isAbsolute && path.nameCount > 0 && !path.startsWith("..")) { "download destination must stay inside its root" }
            return path.toString().replace('\\', '/')
        }
    }
}

class PluginDownloads private constructor(builder: Builder) {
    val dataDirectory: Path? = builder.dataDirectory?.toAbsolutePath()?.normalize()
    val declarations: List<DownloadDeclaration> = Collections.unmodifiableList(builder.declarations.toList())

    class Builder internal constructor() {
        internal var dataDirectory: Path? = null
        internal val declarations = mutableListOf<DownloadDeclaration>()
        fun dataDirectory(value: Path) = apply { dataDirectory = value }
        fun component(id: String, configure: Consumer<DownloadDeclaration.ComponentBuilder>) = apply {
            val builder = DownloadDeclaration.ComponentBuilder(id); configure.accept(builder); add(builder.build())
        }
        fun plugin(name: String, configure: Consumer<DownloadDeclaration.PluginBuilder>) = apply {
            val builder = DownloadDeclaration.PluginBuilder(name); configure.accept(builder); add(builder.build())
        }
        fun file(id: String, configure: Consumer<DownloadDeclaration.FileBuilder>) = apply {
            val builder = DownloadDeclaration.FileBuilder(id); configure.accept(builder); add(builder.build())
        }
        private fun add(value: DownloadDeclaration) {
            require(declarations.none { it.key.equals(value.key, true) }) { "duplicate download declaration: ${value.key}" }
            declarations += value
        }
        fun build(): PluginDownloads { require(declarations.isNotEmpty()) { "at least one download is required" }; return PluginDownloads(this) }
    }

    companion object { @JvmStatic fun builder() = Builder() }
}
