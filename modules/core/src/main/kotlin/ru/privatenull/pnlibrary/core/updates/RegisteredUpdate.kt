package ru.privatenull.pnlibrary.core.updates

import ru.privatenull.pnlibrary.api.updates.PluginUpdateArtifact
import ru.privatenull.pnlibrary.api.updates.PluginUpdateRequest
import ru.privatenull.pnlibrary.api.updates.ArtifactDescriptor
import ru.privatenull.pnlibrary.api.updates.InstalledProduct
import ru.privatenull.pnlibrary.api.updates.ProductDescriptor
import ru.privatenull.pnlibrary.api.updates.ProductRelease
import ru.privatenull.pnlibrary.api.updates.ReleaseSummary
import ru.privatenull.pnlibrary.api.updates.UpdateChannel
import ru.privatenull.pnlibrary.api.updates.UpdatePlanSnapshot
import ru.privatenull.pnlibrary.api.updates.UpdateRegistration
import ru.privatenull.pnlibrary.api.updates.UpdateSnapshot
import ru.privatenull.pnlibrary.api.updates.UpdateState
import ru.privatenull.pnlibrary.update.CatalogRelease
import java.nio.file.Path
import java.util.concurrent.CompletionStage
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Runtime state and public handle for one component participating in updates. */
internal class RegisteredUpdate(
    val descriptor: ProductDescriptor,
    val request: PluginUpdateRequest,
    private val artifact: PluginUpdateArtifact,
    val jar: Path,
    val updateDirectory: Path,
    private val selectedChannel: () -> UpdateChannel,
    private val checkForUpdates: () -> CompletionStage<UpdatePlanSnapshot>,
    private val stagePlan: (UpdatePlanSnapshot) -> CompletionStage<UpdatePlanSnapshot>,
    private val onClose: (RegisteredUpdate) -> Unit,
) : UpdateRegistration {
    val product: String = descriptor.id.value
    val version: String = descriptor.version.toString()
    override val repository: String = "${request.repositoryOwner}/${request.repositoryName}"

    private val closed = AtomicBoolean(false)
    private val state = AtomicReference(initialSnapshot())

    override val isClosed: Boolean get() = closed.get()
    override val snapshot: UpdateSnapshot get() = state.get()

    override fun checkNow() {
        requireOpen()
        checkForUpdates()
    }

    override fun downloadNow() {
        requireOpen()
        checkForUpdates().thenCompose(stagePlan)
    }

    fun installedProduct(providedApi: Int?) = InstalledProduct(
        descriptor.id,
        descriptor.version,
        descriptor.supportedApi,
        providedApi,
    )

    fun releaseFrom(catalogRelease: CatalogRelease): ProductRelease = ProductRelease(
        product = descriptor.id,
        version = catalogRelease.version,
        channel = catalogRelease.channel,
        supportedApi = catalogRelease.api,
        providesApi = catalogRelease.api.maximum.takeIf { descriptor.id.value == "pnlibrary" },
        repository = repository,
        artifacts = catalogRelease.artifacts.map { artifact ->
            ArtifactDescriptor(
                file = artifact.file,
                platform = artifact.platform,
                minimumJava = artifact.javaMinimum,
                maximumJava = artifact.javaMaximum,
                size = null,
                sha256 = null,
                downloadUri = artifact.url,
            )
        },
        publishedAt = catalogRelease.publishedAt,
    )

    fun observe(releases: List<ProductRelease>, channel: UpdateChannel) {
        if (closed.get()) return

        val selection = ReleaseChannelSelector.select(descriptor.id, releases, channel)
        val latest = selection.latestAllowed
        val current = descriptor.version
        state.set(
            UpdateSnapshot(
                product = product,
                currentVersion = version,
                latestVersion = latest?.version?.toString(),
                channel = channel,
                state = if (latest != null && latest.version > current) UpdateState.AVAILABLE else UpdateState.CURRENT,
                currentJava = Runtime.version().feature(),
                requiredJava = artifact.minimumJava,
                automaticDownload = request.automaticDownload,
                releaseUrl = "https://github.com/$repository/releases",
                message = null,
                supportedApi = request.supportedApi,
                availableReleases = selection.all
                    .sortedByDescending(ProductRelease::version)
                    .map { release -> ReleaseSummary(release.version.toString(), release.channel, release.publishedAt) },
            ),
        )
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) onClose(this)
    }

    fun markClosed() {
        closed.set(true)
    }

    private fun initialSnapshot() = UpdateSnapshot(
        product = product,
        currentVersion = version,
        latestVersion = null,
        channel = selectedChannel(),
        state = UpdateState.CHECKING,
        currentJava = Runtime.version().feature(),
        requiredJava = artifact.minimumJava,
        automaticDownload = request.automaticDownload,
        releaseUrl = null,
        message = null,
        supportedApi = request.supportedApi,
    )

    private fun requireOpen() {
        check(!closed.get()) { "update registration is closed" }
    }
}
