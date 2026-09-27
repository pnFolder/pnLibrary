package ru.privatenull.pnlibrary.update

import com.google.gson.JsonParser
import ru.privatenull.pnlibrary.api.updates.ProductRelease
import ru.privatenull.pnlibrary.api.updates.ProductId
import ru.privatenull.pnlibrary.api.updates.ProductDependency
import ru.privatenull.pnlibrary.api.updates.UpdateChannel
import ru.privatenull.pnlibrary.api.updates.PluginUpdateRequest
import ru.privatenull.pnlibrary.api.updates.ArtifactDescriptor
import ru.privatenull.pnlibrary.api.platform.PlatformType
import ru.privatenull.pnlibrary.api.plugin.PluginDependency
import ru.privatenull.pnlibrary.api.version.SemanticVersion
import ru.privatenull.pnlibrary.api.version.PnLibraryApi
import java.net.URI
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.jar.JarInputStream

data class ReleaseSource(val owner: String, val repository: String) {
    init {
        require(PART.matches(owner) && PART.matches(repository)) { "invalid release source: $owner/$repository" }
    }
    companion object { private val PART = Regex("[A-Za-z0-9_.-]+") }
}

enum class RefreshMode { CACHED, FORCE_REMOTE }

class ReleaseCatalogueClient(
    private val http: TrustedHttpClient,
    private val store: ReleaseCatalogueStore,
    private val executor: Executor,
    private val ttl: Duration,
    private val inspectArtifacts: Boolean = false,
) {
    private val inFlight = ConcurrentHashMap<String, CompletableFuture<List<ProductRelease>>>()

    fun releases(source: ReleaseSource, channel: UpdateChannel): CompletableFuture<List<ProductRelease>> {
        return releases(source, channel, null, null, emptyList(), null, RefreshMode.CACHED)
    }

    fun releases(
        source: ReleaseSource,
        channel: UpdateChannel,
        product: ProductId?,
        fallback: PluginUpdateRequest?,
        platform: PlatformType?,
    ): CompletableFuture<List<ProductRelease>> =
        releases(source, channel, product, fallback, emptyList(), platform, RefreshMode.CACHED)

    fun releases(
        source: ReleaseSource,
        channel: UpdateChannel,
        product: ProductId?,
        fallback: PluginUpdateRequest?,
        dependencies: List<PluginDependency>,
        platform: PlatformType?,
        refresh: RefreshMode = RefreshMode.CACHED,
    ): CompletableFuture<List<ProductRelease>> {
        val key = requestKey(source, channel, product, fallback, dependencies, platform) + ":${refresh.name}"
        inFlight[key]?.let { return it }
        val promise = CompletableFuture<List<ProductRelease>>()
        val existing = inFlight.putIfAbsent(key, promise)
        if (existing != null) return existing
        executor.execute {
            try { promise.complete(load(source, channel, product, fallback, dependencies, platform, refresh)) }
            catch (error: Throwable) { promise.completeExceptionally(error) }
            finally { inFlight.remove(key, promise) }
        }
        return promise
    }

    private fun requestKey(
        source: ReleaseSource,
        channel: UpdateChannel,
        product: ProductId?,
        request: PluginUpdateRequest?,
        dependencies: List<PluginDependency>,
        platform: PlatformType?,
    ): String {
        if (request == null) return "${source.owner}/${source.repository}:${channel.name}"
        val artifacts = request.artifacts.joinToString(",") {
            "${it.pattern}:${it.platform?.name}:${it.minimumJava}:${it.maximumJava}"
        }
        val dependencyKey = dependencies.joinToString(",") {
            it.managed?.let { dependency -> "product:${dependency.product}:${dependency.minimumVersion}" }
                ?: it.external?.let { dependency -> "plugin:${dependency.plugin}:${dependency.minimumVersion}" }
                ?: "unknown"
        }
        return buildString {
            append(source.owner).append('/').append(source.repository).append(':').append(channel.name)
            append('|').append(platform?.name)
            append('|').append(product?.value).append('|').append(request.supportedApi)
            append('|').append(artifacts).append('|').append(dependencyKey)
        }
    }

    private fun load(
        source: ReleaseSource,
        channel: UpdateChannel,
        product: ProductId?,
        fallback: PluginUpdateRequest?,
        dependencies: List<PluginDependency>,
        platform: PlatformType?,
        refresh: RefreshMode,
    ): List<ProductRelease> {
        val releasesUri = URI.create("https://api.github.com/repos/${source.owner}/${source.repository}/releases?per_page=30")
        val releasesBytes = validatedBytes(releasesUri, RELEASES_LIMIT, refresh) { bytes ->
            val root = JsonParser.parseString(String(bytes, Charsets.UTF_8))
            require(root.isJsonArray) { "GitHub releases response is not an array" }
        }
        val releases = JsonParser.parseString(String(releasesBytes, Charsets.UTF_8)).asJsonArray
        if (releases.isEmpty) throw ReleaseSelectionException("В GitHub-репозитории нет опубликованных релизов")
        val failures = mutableListOf<ReleaseSelectionException>()
        val selected = releases.take(30).mapNotNull { raw ->
            val release = raw.asJsonObject
            if (release.get("draft")?.asBoolean != false) return@mapNotNull null
            // Release assets are discovered directly from GitHub. The old
            // pn-update.json sidecar is intentionally no longer part of the
            // update protocol.
            try {
                fallbackRelease(release, channel, product, fallback, dependencies, platform, source)
            } catch (error: ReleaseSelectionException) {
                failures += error
                null
            }
        }.distinctBy { it.product to it.version }.sortedByDescending { it.version }
        if (selected.isEmpty() && failures.isNotEmpty()) throw failures.first()
        return selected
    }

    private fun validatedBytes(uri: URI, limit: Int, refresh: RefreshMode, validator: (ByteArray) -> Unit): ByteArray {
        val cached = store.read(uri, ttl).takeIf { refresh == RefreshMode.CACHED }
        if (cached != null) {
            try {
                validator(cached.bytes)
                if (cached.fresh) return cached.bytes
            } catch (_: Exception) {
                store.quarantine(uri)
            }
        }
        return try {
            val bytes = http.get(uri, limit)
            validator(bytes)
            store.write(uri, bytes)
            bytes
        } catch (error: Exception) {
            if (cached != null) {
                validator(cached.bytes)
                cached.bytes
            } else throw error
        }
    }

    private fun fallbackRelease(
        release: com.google.gson.JsonObject,
        acceptedChannel: UpdateChannel,
        product: ProductId?,
        request: PluginUpdateRequest?,
        dependencies: List<PluginDependency>,
        platform: PlatformType?,
        source: ReleaseSource,
    ): ProductRelease? {
        request ?: return null
        product ?: return null
        val tag = release.get("tag_name")?.asString?.trim()?.removePrefix("v") ?: return null
        val version = SemanticVersion.tryParse(tag) ?: return null
        val prerelease = release.get("prerelease")?.asBoolean == true
        val releaseChannel = when {
            !prerelease -> UpdateChannel.STABLE
            tag.contains("dev", true) || tag.contains("snapshot", true) -> UpdateChannel.DEV
            tag.contains("alpha", true) -> UpdateChannel.ALPHA
            else -> UpdateChannel.BETA
        }
        if (!acceptedChannel.accepts(releaseChannel)) return null
        val assets = release.getAsJsonArray("assets")
            ?: throw ReleaseSelectionException("Релиз $version не содержит JAR-файлов")
        val jarNames = assets.mapNotNull { it.asJsonObject.get("name")?.asString }
            .filter { isDistributionArtifact(it) }
        if (jarNames.isEmpty()) throw ReleaseSelectionException("Релиз $version не содержит JAR-файлов")
        var artifacts = assets.mapNotNull { raw ->
            val value = raw.asJsonObject
            val name = value.get("name")?.asString ?: return@mapNotNull null
            if (!isDistributionArtifact(name)) return@mapNotNull null
            val rule = request.artifacts.firstOrNull { artifact ->
                Regex(artifact.pattern).matches(name) && (artifact.platform == null || platform == null || artifact.platform == platform)
            } ?: return@mapNotNull null
            val digest = value.get("digest")?.asString?.removePrefix("sha256:")
                ?.takeIf { it.matches(Regex("[0-9a-fA-F]{64}")) } ?: return@mapNotNull null
            val size = value.get("size")?.asLong?.takeIf { it > 0 } ?: return@mapNotNull null
            val uri = value.get("browser_download_url")?.asString?.let(URI::create) ?: return@mapNotNull null
            ArtifactDescriptor(name, rule.platform ?: platform ?: return@mapNotNull null,
                rule.minimumJava, rule.maximumJava, size, digest, uri)
        }
        if (artifacts.isEmpty()) throw ReleaseSelectionException(
            "В релизе $version нет JAR, подходящего под artifact-pattern; найдены: ${jarNames.joinToString()}",
        )
        var supportedApi = request.supportedApi
        var providesApi = PnLibraryApi.VERSION.takeIf { product.value == "pnlibrary" }
        var descriptorChannel = releaseChannel
        if (inspectArtifacts) {
            val inspected = artifacts.map { artifact -> artifact to inspectArtifact(artifact, product, version) }
            val descriptors = inspected.map { it.second }
            require(descriptors.map { it.supportedApi }.distinct().size == 1) {
                "release $version contains inconsistent pnLibrary API metadata"
            }
            supportedApi = descriptors.first().supportedApi
            providesApi = descriptors.first().supportedApi.maximum.takeIf { product.value == "pnlibrary" }
            descriptorChannel = descriptors.first().channel
            artifacts = inspected.map { (artifact, descriptor) ->
                ArtifactDescriptor(
                    artifact.file, artifact.platform, descriptor.minimumJava, descriptor.maximumJava,
                    artifact.size, artifact.sha256, artifact.downloadUri,
                )
            }
        }
        if (artifacts.none { it.supports(Runtime.version().feature()) }) {
            throw ReleaseSelectionException(
                "JAR релиза $version несовместим с Java ${Runtime.version().feature()}",
            )
        }
        return ProductRelease(
            product, version, descriptorChannel, supportedApi, providesApi,
            dependencies.mapNotNull { it.managed }.map { ProductDependency(it.product, it.minimumVersion) },
            "${source.owner}/${source.repository}", artifacts,
            dependencies.mapNotNull { it.external },
        )
    }

    private fun inspectArtifact(
        artifact: ArtifactDescriptor,
        expectedProduct: ProductId,
        expectedVersion: SemanticVersion,
    ): ru.privatenull.pnlibrary.api.updates.ProductDescriptor {
        val uri = requireNotNull(artifact.downloadUri) { "release artifact has no download URL: ${artifact.file}" }
        require(artifact.size <= Int.MAX_VALUE) { "release artifact is too large to inspect: ${artifact.file}" }
        val bytes = http.get(uri, artifact.size.toInt())
        require(bytes.size.toLong() == artifact.size) { "release artifact size does not match GitHub metadata: ${artifact.file}" }
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        require(digest.equals(artifact.sha256, true)) { "release artifact SHA-256 does not match GitHub metadata: ${artifact.file}" }
        var descriptorBytes: ByteArray? = null
        var matches = 0
        JarInputStream(ByteArrayInputStream(bytes)).use { jar ->
            while (true) {
                val entry = jar.nextJarEntry ?: break
                if (!entry.isDirectory && entry.name.removePrefix("./") == EmbeddedDescriptorReader.ENTRY) {
                    matches++
                    val output = ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (true) {
                        val read = jar.read(buffer)
                        if (read < 0) break
                        require(output.size() + read <= ProductDescriptorCodec.MAX_MANIFEST_BYTES) {
                            "embedded descriptor exceeds size limit"
                        }
                        output.write(buffer, 0, read)
                    }
                    descriptorBytes = output.toByteArray()
                }
            }
        }
        require(matches == 1) { "${artifact.file} must contain exactly one ${EmbeddedDescriptorReader.ENTRY}" }
        val descriptor = ProductDescriptorCodec().decodeInstalled(requireNotNull(descriptorBytes))
        require(descriptor.id == expectedProduct) { "artifact ${artifact.file} declares ${descriptor.id}, expected $expectedProduct" }
        require(descriptor.version == expectedVersion) {
            "artifact ${artifact.file} declares version ${descriptor.version}, expected $expectedVersion"
        }
        return descriptor
    }

    private fun isDistributionArtifact(name: String): Boolean =
        name.endsWith(".jar", true) &&
            !name.endsWith("-sources.jar", true) &&
            !name.endsWith("-javadoc.jar", true)

    companion object {
        private const val RELEASES_LIMIT = 2 * 1024 * 1024
    }
}

class ReleaseSelectionException(message: String) : IllegalArgumentException(message)
