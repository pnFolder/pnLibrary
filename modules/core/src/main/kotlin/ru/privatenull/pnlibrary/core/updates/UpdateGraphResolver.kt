package ru.privatenull.pnlibrary.core.updates

import ru.privatenull.pnlibrary.common.jvm.JavaRuntime

import ru.privatenull.pnlibrary.api.updates.ProductId
import ru.privatenull.pnlibrary.api.updates.ProductRelease
import ru.privatenull.pnlibrary.api.updates.UpdateChannel
import ru.privatenull.pnlibrary.api.updates.UpdatePlan
import ru.privatenull.pnlibrary.api.version.PnLibraryApi
import ru.privatenull.pnlibrary.spi.platform.PlatformIdentity
import ru.privatenull.pnlibrary.spi.platform.PlatformOwnerMetadata
import ru.privatenull.pnlibrary.spi.platform.PlatformPluginInventory
import ru.privatenull.pnlibrary.core.updates.engine.RefreshMode
import ru.privatenull.pnlibrary.core.updates.engine.FreezeStore
import ru.privatenull.pnlibrary.core.updates.engine.ResolutionResult
import ru.privatenull.pnlibrary.core.updates.engine.ResolverPolicy
import ru.privatenull.pnlibrary.core.updates.engine.UpdateResolver
import java.time.Instant

/** Loads release catalogues and resolves one coherent update plan for all registered products. */
internal class UpdateGraphResolver(
    private val ownership: PlatformOwnerMetadata,
    private val inventory: PlatformPluginInventory,
    private val identity: PlatformIdentity,
    private val configuration: UpdateConfiguration,
    private val catalogs: ReleaseCatalogGateway,
    private val freezes: FreezeStore,
) {
    fun resolve(
        registrations: List<RegisteredUpdate>,
        refreshMode: RefreshMode = RefreshMode.CACHED,
    ): ResolutionResult {
        if (registrations.isEmpty()) return emptyPlan()

        val releases = loadReleases(registrations, refreshMode)
        observeLatestVersions(registrations, releases)

        return UpdateResolver(ProductId.of("pnlibrary")).resolve(
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
        if (product.value == "pnlibrary") configuration.library.channel
        else configuration.plugins[product.value]?.channel ?: fallback

    private fun installedProducts(registrations: List<RegisteredUpdate>) = registrations.map { registration ->
        val providedApi = PnLibraryApi.VERSION.takeIf { registration.descriptor.id.value == "pnlibrary" }
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
            val policy = configuration.plugins[registration.product]
            registration.descriptor.id.takeIf {
                !configuration.pluginUpdatesEnabled ||
                    policy?.enabled == false ||
                    policy?.pauseUntil?.isAfter(Instant.now()) == true
            }
        }
        return (freezes.active().keys + configuredFreezes).toSet()
    }

    private fun resolverPolicy() = ResolverPolicy(
        allowManagedInstalls = configuration.downloads.allowManagedPlugins &&
            configuration.installation.allowNewPlugins,
        installedExternalPlugins = runCatching { inventory.installedPlugins() }
            .getOrNull()
            .orEmpty()
            .keys,
    )

    private fun emptyPlan(): ResolutionResult =
        ResolutionResult.Ready(UpdatePlan(PnLibraryApi.VERSION, emptyList(), emptyList()))
}
