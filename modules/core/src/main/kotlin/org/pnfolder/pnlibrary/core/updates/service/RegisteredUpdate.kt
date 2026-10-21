package org.pnfolder.pnlibrary.core.updates.service

import org.pnfolder.pnlibrary.api.updates.release.ArtifactDescriptor
import org.pnfolder.pnlibrary.api.updates.release.InstalledProduct
import org.pnfolder.pnlibrary.api.updates.PluginUpdateArtifact
import org.pnfolder.pnlibrary.api.updates.PluginUpdateRequest
import org.pnfolder.pnlibrary.api.updates.ProductDescriptor
import org.pnfolder.pnlibrary.api.updates.release.ProductRelease
import org.pnfolder.pnlibrary.api.updates.release.ReleaseSummary
import org.pnfolder.pnlibrary.api.updates.UpdateChannel
import org.pnfolder.pnlibrary.api.updates.UpdatePlanSnapshot
import org.pnfolder.pnlibrary.api.updates.UpdateRegistration
import org.pnfolder.pnlibrary.api.updates.UpdateSnapshot
import org.pnfolder.pnlibrary.api.updates.UpdateState
import org.pnfolder.pnlibrary.common.jvm.JavaRuntime
import org.pnfolder.pnlibrary.core.updates.engine.CatalogRelease
import org.pnfolder.pnlibrary.core.updates.policy.ReleaseChannelSelector
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
    private val effectiveAutomatic: () -> Boolean = { request.automaticDownload },
) : UpdateRegistration {
    val product: String = descriptor.id.value
    val version: String = descriptor.version.toString()
    override val repository: String = "${request.repositoryOwner}/${request.repositoryName}"

    private val closed = AtomicBoolean(false)
    private val state = AtomicReference(initialSnapshot())

    override val isClosed: Boolean get() = closed.get()
    override val snapshot: UpdateSnapshot get() = state.get()

    override fun checkNow() {
        checkOpen()
        checkForUpdates()
    }

    override fun downloadNow() {
        checkOpen()
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
        description = catalogRelease.description,
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
        changelogUrl = catalogRelease.changelogUrl?.toString(),
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
                state = if (latest != null && latest.version > current) UpdateState.UPDATE_AVAILABLE else UpdateState.UP_TO_DATE,
                currentJava = JavaRuntime.feature,
                requiredJava = artifact.minimumJava,
                automaticDownload = effectiveAutomatic(),
                releaseUrl = "https://github.com/$repository/releases",
                message = null,
                latestChangelogUrl = latest?.changelogUrl,
                supportedApi = request.supportedApi,
                availableReleases = selection.all
                    .sortedByDescending(ProductRelease::version)
                    .map { release -> ReleaseSummary(release.version.toString(), release.channel, release.publishedAt, release.changelogUrl) },
            ),
        )
    }

    /** Re-reads the channel and automatic-download choice after an administrator changed them. */
    fun refreshPolicy() {
        state.updateAndGet { old ->
            UpdateSnapshot(
                old.product, old.currentVersion, old.latestVersion, selectedChannel(), old.state, old.currentJava,
                old.requiredJava, effectiveAutomatic(), old.releaseUrl, old.message, old.latestChangelogUrl,
                old.supportedApi, old.availableReleases,
            )
        }
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
        currentJava = JavaRuntime.feature,
        requiredJava = artifact.minimumJava,
        automaticDownload = effectiveAutomatic(),
        releaseUrl = null,
        message = null,
        supportedApi = request.supportedApi,
    )

    private fun checkOpen() {
        check(!closed.get()) { "Update registration is closed" }
    }
}
