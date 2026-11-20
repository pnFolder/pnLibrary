package org.pnfolder.pnlibrary.updates.service

import org.pnfolder.pnlibrary.api.platform.PlatformType
import org.pnfolder.pnlibrary.api.runtime.PnLibraryIdentity
import org.pnfolder.pnlibrary.api.updates.UpdateRegistration
import org.pnfolder.pnlibrary.api.updates.product.ProductDescriptor
import org.pnfolder.pnlibrary.api.updates.product.UpdateChannel
import org.pnfolder.pnlibrary.api.updates.release.ArtifactDescriptor
import org.pnfolder.pnlibrary.api.updates.release.InstalledProduct
import org.pnfolder.pnlibrary.api.updates.release.ProductRelease
import org.pnfolder.pnlibrary.api.updates.release.ReleaseSummary
import org.pnfolder.pnlibrary.api.updates.request.PluginUpdateArtifact
import org.pnfolder.pnlibrary.api.updates.request.PluginUpdateRequest
import org.pnfolder.pnlibrary.api.updates.state.UpdatePlanSnapshot
import org.pnfolder.pnlibrary.api.updates.state.UpdateSnapshot
import org.pnfolder.pnlibrary.api.updates.state.UpdateState
import org.pnfolder.pnlibrary.common.jvm.JavaRuntime
import org.pnfolder.pnlibrary.updates.engine.catalog.CatalogRelease
import org.pnfolder.pnlibrary.updates.policy.ReleaseChannelSelector
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
    private val platform: PlatformType? = null,
    private val chosenJava: () -> Int? = { null },
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

    /** The product as the resolver sees it installed. */
    fun installedProduct(providedApi: Int?) = InstalledProduct(
        descriptor.id,
        descriptor.version,
        descriptor.supportedApi,
        providedApi,
    )

    /** Converts a catalog release into a resolver release for this product. */
    fun releaseFrom(catalogRelease: CatalogRelease): ProductRelease = ProductRelease(
        product = descriptor.id,
        version = catalogRelease.version,
        channel = catalogRelease.channel,
        description = catalogRelease.description,
        supportedApi = catalogRelease.api,
        providesApi = catalogRelease.api.maximum.takeIf { descriptor.id.value == PnLibraryIdentity.PRODUCT_ID },
        repository = repository,
        artifacts = catalogRelease.artifacts.map { artifact ->
            ArtifactDescriptor(
                file = artifact.file,
                platform = artifact.platform,
                minimumJava = artifact.javaMinimum,
                maximumJava = artifact.javaMaximum,
                size = artifact.size,
                sha256 = artifact.sha256,
                downloadUri = artifact.url,
            )
        },
        publishedAt = catalogRelease.publishedAt,
        changelogUrl = catalogRelease.changelogUrl?.toString(),
    )

    /** Remembers the releases found for [channel] and updates the snapshot. */
    fun observe(releases: List<ProductRelease>, channel: UpdateChannel) {
        if (closed.get()) return

        val selection = ReleaseChannelSelector.select(descriptor.id, releases, channel)
        val latest = selection.latestAllowed
        val current = descriptor.version
        val builds = latest?.artifacts.orEmpty()
            .filter { (platform == null || it.matchesPlatform(platform)) && it.supports(JavaRuntime.feature) }
            .map { it.minimumJava }.distinct().sorted()
        val recommended = descriptor.minimumJava.takeIf { it in builds } ?: builds.maxOrNull()
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
                javaBuilds = builds,
                javaBuild = chosenJava(),
                recommendedJava = recommended,
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
                old.javaBuilds, chosenJava(), old.recommendedJava,
            )
        }
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) onClose(this)
    }

    /** Marks the registration as closed so later results are ignored. */
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
