package org.pnfolder.pnlibrary.core.updates.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.pnfolder.pnlibrary.api.platform.PlatformType
import org.pnfolder.pnlibrary.api.updates.PluginUpdateRequest
import org.pnfolder.pnlibrary.api.updates.ProductDescriptor
import org.pnfolder.pnlibrary.api.updates.UpdateChannel
import org.pnfolder.pnlibrary.api.updates.UpdatePlanSnapshot
import org.pnfolder.pnlibrary.api.updates.UpdateState
import org.pnfolder.pnlibrary.api.updates.release.ProductRelease
import org.pnfolder.pnlibrary.api.version.ApiVersionRange
import org.pnfolder.pnlibrary.api.version.SemanticVersion
import org.pnfolder.pnlibrary.core.updates.engine.CatalogArtifact
import org.pnfolder.pnlibrary.core.updates.engine.CatalogRelease
import java.net.URI
import java.nio.file.Paths
import java.time.Instant
import java.util.concurrent.CompletableFuture

class RegisteredUpdateTest {
    private val request = PluginUpdateRequest.builder()
        .repository("owner", "example")
        .supportedApi(1, 1)
        .exactArtifact("example.jar")
        .build()
    private val descriptor = ProductDescriptor.builder().version("1.0.0").pnLibraryApi(1, 1).build().bindTo("example")

    private var channel = UpdateChannel.STABLE
    private var automatic = false
    private var closedWith = 0
    private val checks = mutableListOf<CompletableFuture<UpdatePlanSnapshot>>()
    private val staged = mutableListOf<UpdatePlanSnapshot>()

    private fun registration(product: ProductDescriptor = descriptor) = RegisteredUpdate(
        descriptor = product,
        request = request,
        artifact = request.artifacts.first(),
        jar = Paths.get("plugins/example.jar"),
        updateDirectory = Paths.get("plugins/update"),
        selectedChannel = { channel },
        checkForUpdates = { CompletableFuture<UpdatePlanSnapshot>().also(checks::add) },
        stagePlan = { plan -> staged += plan; CompletableFuture.completedFuture(plan) },
        onClose = { closedWith++ },
        effectiveAutomatic = { automatic },
    )

    private fun release(version: String, channel: UpdateChannel = UpdateChannel.STABLE) = ProductRelease(
        product = descriptor.id,
        version = SemanticVersion.parse(version),
        channel = channel,
        supportedApi = ApiVersionRange(1, 1),
        providesApi = null,
        repository = "owner/example",
    )

    private fun catalogRelease(api: ApiVersionRange, artifacts: List<CatalogArtifact>) = CatalogRelease(
        version = SemanticVersion.parse("1.3.0"),
        channel = UpdateChannel.STABLE,
        description = "Fixes",
        changelogUrl = URI.create("https://example.com/changes"),
        publishedAt = Instant.parse("2026-10-01T00:00:00Z"),
        api = api,
        artifacts = artifacts,
    )

    @Test
    fun `a new registration reports that it is checking`() {
        val registration = registration()
        val snapshot = registration.snapshot

        assertEquals(UpdateState.CHECKING, snapshot.state)
        assertEquals("1.0.0", snapshot.currentVersion)
        assertNull(snapshot.latestVersion)
        assertEquals("owner/example", registration.repository)
    }

    @Test
    fun `observing a newer release reports an available update`() {
        val registration = registration()

        registration.observe(listOf(release("1.0.0"), release("1.2.0")), UpdateChannel.STABLE)

        val snapshot = registration.snapshot
        assertEquals(UpdateState.UPDATE_AVAILABLE, snapshot.state)
        assertEquals("1.2.0", snapshot.latestVersion)
        assertEquals(listOf("1.2.0", "1.0.0"), snapshot.availableReleases.map { it.version })
        assertEquals("https://github.com/owner/example/releases", snapshot.releaseUrl)
    }

    @Test
    fun `the installed version or an older one is up to date`() {
        val registration = registration()

        registration.observe(listOf(release("1.0.0"), release("0.9.0")), UpdateChannel.STABLE)

        assertEquals(UpdateState.UP_TO_DATE, registration.snapshot.state)
    }

    @Test
    fun `a release outside the chosen channel is not offered`() {
        val registration = registration()

        registration.observe(listOf(release("2.0.0-dev.1", UpdateChannel.DEV)), UpdateChannel.STABLE)

        assertEquals(UpdateState.UP_TO_DATE, registration.snapshot.state)
        assertNull(registration.snapshot.latestVersion)
    }

    @Test
    fun `a closed registration ignores observations and refuses checks`() {
        val registration = registration()
        registration.close()

        registration.observe(listOf(release("9.0.0")), UpdateChannel.STABLE)

        assertEquals(UpdateState.CHECKING, registration.snapshot.state)
        assertTrue(registration.isClosed)
        assertThrows(IllegalStateException::class.java) { registration.checkNow() }
        assertThrows(IllegalStateException::class.java) { registration.downloadNow() }
    }

    @Test
    fun `closing notifies the owner only once`() {
        val registration = registration()

        registration.close()
        registration.close()

        assertEquals(1, closedWith)
    }

    @Test
    fun `refreshing the policy picks up a changed channel and automatic download`() {
        val registration = registration()
        registration.observe(listOf(release("1.1.0")), UpdateChannel.STABLE)
        channel = UpdateChannel.DEV
        automatic = true

        registration.refreshPolicy()

        val snapshot = registration.snapshot
        assertEquals(UpdateChannel.DEV, snapshot.channel)
        assertTrue(snapshot.automaticDownload)
        assertEquals("1.1.0", snapshot.latestVersion)
        assertEquals(UpdateState.UPDATE_AVAILABLE, snapshot.state)
    }

    @Test
    fun `downloading stages the plan only after the check completes`() {
        val registration = registration()
        val plan = mock(UpdatePlanSnapshot::class.java)

        registration.downloadNow()
        assertTrue(staged.isEmpty())

        checks.single().complete(plan)

        assertEquals(listOf(plan), staged)
    }

    @Test
    fun `a catalog release maps to a product release with its artifacts`() {
        val artifact = CatalogArtifact(
            file = "example-1.3.0.jar",
            platform = PlatformType.BUKKIT,
            javaMinimum = 17,
            url = URI.create("https://example.com/example-1.3.0.jar"),
            sha256 = "a".repeat(64),
            size = 10,
        )

        val mapped = registration().releaseFrom(catalogRelease(ApiVersionRange(1, 1), listOf(artifact)))

        assertEquals(descriptor.id, mapped.product)
        assertEquals("owner/example", mapped.repository)
        assertNull(mapped.providesApi)
        assertEquals("https://example.com/changes", mapped.changelogUrl)
        val mappedArtifact = mapped.artifacts.single()
        assertEquals("example-1.3.0.jar", mappedArtifact.file)
        assertEquals(17, mappedArtifact.minimumJava)
        assertEquals(10L, mappedArtifact.size)
    }

    @Test
    fun `only the library product provides the API it was released with`() {
        val catalog = catalogRelease(ApiVersionRange(1, 2), emptyList())

        assertEquals(2, registration(ProductDescriptor.library("1.0.0")).releaseFrom(catalog).providesApi)
        assertNull(registration().releaseFrom(catalog).providesApi)
    }
}
