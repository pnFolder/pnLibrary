package org.pnfolder.pnlibrary.core.updates.install

import org.pnfolder.pnlibrary.api.runtime.PnLibraryIdentity
import org.pnfolder.pnlibrary.api.updates.ProductId
import org.pnfolder.pnlibrary.api.updates.release.ProductRelease
import org.pnfolder.pnlibrary.api.updates.UpdateChannel
import org.pnfolder.pnlibrary.api.updates.release.UpdatePlan
import org.pnfolder.pnlibrary.api.version.PnLibraryApi
import org.pnfolder.pnlibrary.common.jvm.JavaRuntime
import org.pnfolder.pnlibrary.core.updates.engine.FreezeStore
import org.pnfolder.pnlibrary.core.updates.engine.RefreshMode
import org.pnfolder.pnlibrary.core.updates.engine.ResolutionResult
import org.pnfolder.pnlibrary.core.updates.engine.ResolverPolicy
import org.pnfolder.pnlibrary.core.updates.engine.UpdateResolver
import org.pnfolder.pnlibrary.core.updates.policy.UpdateConfiguration
import org.pnfolder.pnlibrary.core.updates.policy.UpdatePolicyOverrides
import org.pnfolder.pnlibrary.core.updates.service.RegisteredUpdate
import org.pnfolder.pnlibrary.spi.platform.PlatformIdentity
import org.pnfolder.pnlibrary.spi.platform.PlatformPluginInventory
import java.time.Instant

/** Loads release catalogs and resolves one coherent update plan for all registered products. */
internal class UpdateGraphResolver(
    private val inventory: PlatformPluginInventory,
    private val identity: PlatformIdentity,
    private val configuration: UpdateConfiguration,
    private val catalogs: ReleaseCatalogGateway,
    private val freezes: FreezeStore,
    private val overrides: UpdatePolicyOverrides? = null,
) {
    fun resolve(
        registrations: List<RegisteredUpdate>,
        refreshMode: RefreshMode = RefreshMode.CACHED,
    ): ResolutionResult {
        if (registrations.isEmpty()) return emptyPlan()

        val releases = loadReleases(registrations, refreshMode)
        observeLatestVersions(registrations, releases)

        return UpdateResolver(ProductId.of(PnLibraryIdentity.PRODUCT_ID)).resolve(
            installed = installedProducts(registrations),
            releases = releases,
            channels = registrations.associate { it.descriptor.id to channelFor(it) },
            defaultChannel = UpdateChannel.STABLE,
            frozen = frozenProducts(registrations),
            platform = identity.type,
            javaFeature = JavaRuntime.feature,
            policy = resolverPolicy(),
        )
    }

    fun channelFor(registration: RegisteredUpdate): UpdateChannel =
        channelFor(registration.descriptor.id, registration.request.channel)

    fun channelFor(product: ProductId, fallback: UpdateChannel): UpdateChannel =
        overrides?.channel(product.value)
            ?: if (product.value == PnLibraryIdentity.PRODUCT_ID) configuration.library.channel
            else fallback

    private fun installedProducts(registrations: List<RegisteredUpdate>) = registrations.map { registration ->
        val providedApi = PnLibraryApi.VERSION.takeIf { registration.descriptor.id.value == PnLibraryIdentity.PRODUCT_ID }
        registration.installedProduct(providedApi)
    }

    private fun loadReleases(
        registrations: List<RegisteredUpdate>,
        refreshMode: RefreshMode,
    ): List<ProductRelease> = registrations.flatMap { registration ->
        val catalog = catalogs.load(
            owner = registration.request.repositoryOwner,
            repository = registration.request.repositoryName,
            refreshMode = refreshMode,
        )
        require(catalog.product.equals(registration.product, ignoreCase = true)) {
            "Release catalog product ${catalog.product} does not match ${registration.descriptor.id}"
        }
        catalog.releases.map(registration::releaseFrom)
    }

    private fun observeLatestVersions(
        registrations: List<RegisteredUpdate>,
        releases: List<ProductRelease>,
    ) {
        registrations.forEach { registration ->
            val productReleases = releases.filter { it.product == registration.descriptor.id }
            registration.observe(productReleases, channelFor(registration))
        }
    }

    private fun frozenProducts(registrations: List<RegisteredUpdate>): Set<ProductId> {
        val configuredFreezes = registrations.mapNotNull { registration ->
            registration.descriptor.id.takeIf {
                !configuration.pluginUpdatesEnabled || registration.product in configuration.ignoredPlugins
            }
        }
        return (freezes.active().keys + configuredFreezes).toSet()
    }

    private fun resolverPolicy() = ResolverPolicy(
        allowManagedInstalls = configuration.downloads.allowManagedPlugins &&
            configuration.installation.allowNewPlugins,
        // An unreadable inventory makes external plugin dependencies count as missing,
        // so releases that need them are skipped instead of installed blindly.
        installedExternalPlugins = runCatching { inventory.installedPlugins() }
            .getOrNull()
            .orEmpty()
            .keys,
    )

    private fun emptyPlan(): ResolutionResult =
        ResolutionResult.Ready(UpdatePlan(PnLibraryApi.VERSION, emptyList(), emptyList()))
}
