package org.pnfolder.pnlibrary.core.updates.install

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.pnfolder.pnlibrary.api.platform.PlatformType
import org.pnfolder.pnlibrary.api.runtime.PnLibraryIdentity
import org.pnfolder.pnlibrary.api.updates.PluginUpdateRequest
import org.pnfolder.pnlibrary.api.updates.ProductDescriptor
import org.pnfolder.pnlibrary.api.updates.ProductId
import org.pnfolder.pnlibrary.api.updates.UpdateChannel
import org.pnfolder.pnlibrary.core.updates.engine.FreezeStore
import org.pnfolder.pnlibrary.core.updates.engine.RefreshMode
import org.pnfolder.pnlibrary.core.updates.engine.ReleaseCatalog
import org.pnfolder.pnlibrary.core.updates.engine.ResolutionResult
import org.pnfolder.pnlibrary.core.updates.policy.UpdateConfiguration
import org.pnfolder.pnlibrary.core.updates.policy.UpdatePolicyOverrides
import org.pnfolder.pnlibrary.core.updates.service.RegisteredUpdate
import org.pnfolder.pnlibrary.spi.platform.PlatformIdentity
import org.pnfolder.pnlibrary.spi.platform.PlatformPluginInventory
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.CompletableFuture

class UpdateGraphResolverTest {
    @TempDir
    lateinit var directory: Path

    private val catalogs = mock(ReleaseCatalogGateway::class.java)
    private val inventory = mock(PlatformPluginInventory::class.java)
    private val identity = mock(PlatformIdentity::class.java).also { `when`(it.type).thenReturn(PlatformType.BUKKIT) }
    private val request = PluginUpdateRequest.builder()
        .repository("owner", "example")
        .supportedApi(1, 1)
        .exactArtifact("example.jar")
        .channel(UpdateChannel.DEV)
        .build()

    private fun registration(product: ProductDescriptor = ProductDescriptor.builder().version("1.0.0").pnLibraryApi(1, 1).build().bindTo("example")) =
        RegisteredUpdate(
            descriptor = product,
            request = request,
            artifact = request.artifacts.first(),
            jar = Paths.get("plugins/example.jar"),
            updateDirectory = Paths.get("plugins/update"),
            selectedChannel = { UpdateChannel.STABLE },
            checkForUpdates = { CompletableFuture() },
            stagePlan = { CompletableFuture.completedFuture(it) },
            onClose = {},
            effectiveAutomatic = { false },
        )

    private fun library() = registration(ProductDescriptor.library("1.0.0"))

    private fun resolver(
        configuration: UpdateConfiguration = UpdateConfiguration(),
        overrides: UpdatePolicyOverrides? = null,
    ) = UpdateGraphResolver(inventory, identity, configuration, catalogs, FreezeStore(directory.resolve("freezes.json")), overrides)

    @Test
    fun `nothing registered resolves to an empty plan without loading any catalog`() {
        val result = resolver().resolve(emptyList())

        assertTrue(result is ResolutionResult.Ready)
        assertTrue((result as ResolutionResult.Ready).plan.changes.isEmpty())
        verifyNoInteractions(catalogs)
    }

    @Test
    fun `a catalog for another product is rejected`() {
        `when`(catalogs.load("owner", "example", RefreshMode.CACHED)).thenReturn(ReleaseCatalog("someone-else", emptyList()))

        val error = assertThrows(IllegalArgumentException::class.java) { resolver().resolve(listOf(registration())) }

        assertTrue(error.message!!.contains("someone-else"))
    }

    @Test
    fun `the requested refresh mode reaches the catalog gateway`() {
        `when`(catalogs.load("owner", "example", RefreshMode.FORCE_REMOTE)).thenReturn(ReleaseCatalog(PnLibraryIdentity.PRODUCT_ID, emptyList()))

        resolver().resolve(listOf(library()), RefreshMode.FORCE_REMOTE)

        verify(catalogs).load("owner", "example", RefreshMode.FORCE_REMOTE)
        verify(catalogs, never()).load("owner", "example", RefreshMode.CACHED)
    }

    @Test
    fun `an unreadable plugin inventory does not stop resolution`() {
        `when`(inventory.installedPlugins()).thenThrow(IllegalStateException("scan failed"))
        `when`(catalogs.load("owner", "example", RefreshMode.CACHED)).thenReturn(ReleaseCatalog(PnLibraryIdentity.PRODUCT_ID, emptyList()))

        val result = resolver().resolve(listOf(library()))

        assertTrue(result is ResolutionResult.Ready)
    }

    @Test
    fun `the library channel comes from configuration and a plugin keeps its requested channel`() {
        val configuration = UpdateConfiguration(library = UpdateConfiguration.LibraryPolicy(channel = UpdateChannel.DEV))
        val resolver = resolver(configuration)

        assertEquals(UpdateChannel.DEV, resolver.channelFor(ProductId.of(PnLibraryIdentity.PRODUCT_ID), UpdateChannel.STABLE))
        assertEquals(UpdateChannel.STABLE, resolver.channelFor(ProductId.of("example"), UpdateChannel.STABLE))
        assertEquals(UpdateChannel.DEV, resolver.channelFor(registration()))
    }
}
