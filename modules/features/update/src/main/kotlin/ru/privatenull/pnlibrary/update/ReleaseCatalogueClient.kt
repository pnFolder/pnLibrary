package ru.privatenull.pnlibrary.update

import com.google.gson.JsonParser
import ru.privatenull.pnlibrary.api.updates.ComponentRelease
import ru.privatenull.pnlibrary.api.updates.UpdateChannel
import ru.privatenull.pnlibrary.api.updates.PluginUpdateRequest
import ru.privatenull.pnlibrary.api.updates.ArtifactDescriptor
import ru.privatenull.pnlibrary.api.platform.PlatformType
import ru.privatenull.pnlibrary.api.version.SemanticVersion
import ru.privatenull.pnlibrary.api.version.PnLibraryApi
import java.net.URI
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor

data class ReleaseSource(val owner: String, val repository: String) {
    init {
        require(PART.matches(owner) && PART.matches(repository)) { "invalid release source: $owner/$repository" }
    }
    companion object { private val PART = Regex("[A-Za-z0-9_.-]+") }
}

class ReleaseCatalogueClient(
    private val http: TrustedHttpClient,
    private val store: ReleaseCatalogueStore,
    private val executor: Executor,
    private val ttl: Duration,
    private val codec: ComponentDescriptorCodec = ComponentDescriptorCodec(),
) {
    private val inFlight = ConcurrentHashMap<String, CompletableFuture<List<ComponentRelease>>>()

    fun releases(source: ReleaseSource, channel: UpdateChannel): CompletableFuture<List<ComponentRelease>> {
        return releases(source, channel, null, null)
    }

    fun releases(
        source: ReleaseSource,
        channel: UpdateChannel,
        fallback: PluginUpdateRequest?,
        platform: PlatformType?,
    ): CompletableFuture<List<ComponentRelease>> {
        val key = requestKey(source, channel, fallback, platform)
        inFlight[key]?.let { return it }
        val promise = CompletableFuture<List<ComponentRelease>>()
        val existing = inFlight.putIfAbsent(key, promise)
        if (existing != null) return existing
        executor.execute {
            try { promise.complete(load(source, channel, fallback, platform)) }
            catch (error: Throwable) { promise.completeExceptionally(error) }
            finally { inFlight.remove(key, promise) }
        }
        return promise
    }

    private fun requestKey(
        source: ReleaseSource,
        channel: UpdateChannel,
        request: PluginUpdateRequest?,
        platform: PlatformType?,
    ): String {
        if (request == null) return "${source.owner}/${source.repository}:${channel.name}"
        val artifacts = request.artifacts.joinToString(",") {
            "${it.pattern}:${it.platform?.name}:${it.minimumJava}:${it.maximumJava}"
        }
        val dependencies = request.dependencies.joinToString(",") { "${it.component}:${it.minimumVersion}" }
        return buildString {
            append(source.owner).append('/').append(source.repository).append(':').append(channel.name)
            append('|').append(platform?.name)
            append('|').append(request.component.value).append('|').append(request.supportedApi)
            append('|').append(artifacts).append('|').append(dependencies)
        }
    }

    private fun load(
        source: ReleaseSource,
        channel: UpdateChannel,
        fallback: PluginUpdateRequest?,
        platform: PlatformType?,
    ): List<ComponentRelease> {
        val releasesUri = URI.create("https://api.github.com/repos/${source.owner}/${source.repository}/releases?per_page=30")
        val releasesBytes = validatedBytes(releasesUri, RELEASES_LIMIT) { bytes ->
            val root = JsonParser.parseString(String(bytes, Charsets.UTF_8))
            require(root.isJsonArray) { "GitHub releases response is not an array" }
        }
        val releases = JsonParser.parseString(String(releasesBytes, Charsets.UTF_8)).asJsonArray
        return releases.take(30).mapNotNull { raw ->
            val release = raw.asJsonObject
            if (release.get("draft")?.asBoolean != false) return@mapNotNull null
            val asset = release.getAsJsonArray("assets")?.firstOrNull { item ->
                item.asJsonObject.get("name")?.asString == MANIFEST_NAME
            }?.asJsonObject
            if (asset == null) return@mapNotNull fallbackRelease(release, channel, fallback, platform, source)
            val url = asset.get("browser_download_url")?.asString ?: return@mapNotNull null
            val uri = URI.create(url)
            val bytes = validatedBytes(uri, MANIFEST_LIMIT) { codec.decodeRelease(it) }
            val decoded = codec.decodeRelease(bytes)
            ComponentRelease(
                decoded.component, decoded.version, decoded.channel, decoded.supportedApi, decoded.providesApi,
                decoded.dependencies, decoded.repository,
                decoded.artifacts.map { artifact ->
                    val download = release.getAsJsonArray("assets")?.firstOrNull { item ->
                        item.asJsonObject.get("name")?.asString == artifact.file
                    }?.asJsonObject?.get("browser_download_url")?.asString?.let(URI::create)
                    ru.privatenull.pnlibrary.api.updates.ArtifactDescriptor(
                        artifact.file, artifact.platform, artifact.minimumJava, artifact.maximumJava,
                        artifact.size, artifact.sha256, download,
                    )
                },
                decoded.externalDependencies,
            ).takeIf { channel.accepts(it.channel) }
        }.distinctBy { it.component to it.version }.sortedByDescending { it.version }
    }

    private fun fallbackRelease(
        release: com.google.gson.JsonObject,
        acceptedChannel: UpdateChannel,
        request: PluginUpdateRequest?,
        platform: PlatformType?,
        source: ReleaseSource,
    ): ComponentRelease? {
        request ?: return null
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
        val assets = release.getAsJsonArray("assets") ?: return null
        val artifacts = assets.mapNotNull { raw ->
            val value = raw.asJsonObject
            val name = value.get("name")?.asString ?: return@mapNotNull null
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
        if (artifacts.isEmpty()) return null
        return ComponentRelease(
            request.component, version, releaseChannel, request.supportedApi,
            PnLibraryApi.VERSION.takeIf { request.component.value == "pnlibrary" },
            request.dependencies, "${source.owner}/${source.repository}", artifacts, request.externalDependencies,
        )
    }

    private fun validatedBytes(uri: URI, limit: Int, validator: (ByteArray) -> Unit): ByteArray {
        val cached = store.read(uri, ttl)
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

    companion object {
        private const val MANIFEST_NAME = "pn-update.json"
        private const val RELEASES_LIMIT = 2 * 1024 * 1024
        private const val MANIFEST_LIMIT = ComponentDescriptorCodec.MAX_MANIFEST_BYTES
    }
}
