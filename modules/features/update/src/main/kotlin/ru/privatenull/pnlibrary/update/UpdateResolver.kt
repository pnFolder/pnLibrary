package ru.privatenull.pnlibrary.update

import ru.privatenull.pnlibrary.api.updates.*
import ru.privatenull.pnlibrary.api.platform.PlatformType

data class ResolverPolicy(
    val allowManagedInstalls: Boolean = false,
    val installedExternalPlugins: Set<String> = emptySet(),
)

/** Result of resolving all managed components as one compatible ecosystem. */
sealed class ResolutionResult {
    data class Ready(val plan: UpdatePlan) : ResolutionResult()
    data class Blocked(
        val reasons: List<BlockedReason>,
        val fallbackPlan: UpdatePlan?,
    ) : ResolutionResult()
}

/** Deterministic API, channel, freeze, and dependency resolver for pnUpdate. */
class UpdateResolver(private val libraryComponent: ProductId) {
    @JvmOverloads
    fun resolve(
        installed: List<InstalledProduct>,
        releases: List<ProductRelease>,
        channels: Map<ProductId, UpdateChannel> = emptyMap(),
        defaultChannel: UpdateChannel = UpdateChannel.STABLE,
        frozen: Set<ProductId> = emptySet(),
        platform: PlatformType? = null,
        javaFeature: Int = Int.MAX_VALUE,
        policy: ResolverPolicy = ResolverPolicy(),
    ): ResolutionResult {
        require(installed.isNotEmpty()) { "at least one installed component is required" }
        require(installed.map(InstalledProduct::product).distinct().size == installed.size) {
            "installed component IDs must be unique"
        }
        val installedById = installed.associateBy(InstalledProduct::product)
        val currentLibrary = requireNotNull(installedById[libraryComponent]) {
            "installed pnLibrary component is required"
        }
        require(currentLibrary.providesApi != null) { "installed pnLibrary must provide an API generation" }

        val allowedReleases = releases.filter { release ->
            channels.getOrDefault(release.product, defaultChannel).accepts(release.channel)
        }.filter { release -> release.artifacts.isEmpty() || release.artifacts.any { artifact ->
            (platform == null || artifact.platform == platform) && artifact.supports(javaFeature)
        } }
        val currentLibraryRelease = currentLibrary.asRelease()
        val libraryDomain = if (libraryComponent in frozen) {
            listOf(currentLibraryRelease)
        } else {
            (allowedReleases.filter { it.product == libraryComponent } + currentLibraryRelease)
                .filter { it.providesApi != null && it.version >= currentLibrary.version }
                .distinctBy { it.version.toString() }
                .sortedByDescending(ProductRelease::version)
        }

        var primaryFailure: List<BlockedReason>? = null
        libraryDomain.forEachIndexed { index, libraryRelease ->
            val attempt = resolveForApi(
                installed,
                allowedReleases,
                libraryRelease,
                frozen,
                policy,
            )
            if (attempt.plan != null) {
                if (attempt.reasons.isNotEmpty()) return ResolutionResult.Blocked(attempt.reasons, attempt.plan)
                if (index == 0 && primaryFailure == null) return ResolutionResult.Ready(attempt.plan)
                return ResolutionResult.Blocked(primaryFailure.orEmpty(), attempt.plan)
            }
            if (primaryFailure == null) primaryFailure = attempt.reasons
        }

        if (libraryComponent in frozen && allowedReleases.any {
                it.product == libraryComponent && it.version > currentLibrary.version
            }
        ) {
            primaryFailure = listOf(BlockedReason.Frozen(libraryComponent)) + primaryFailure.orEmpty()
        }
        return ResolutionResult.Blocked(primaryFailure.orEmpty().distinct(), null)
    }

    private fun resolveForApi(
        installed: List<InstalledProduct>,
        releases: List<ProductRelease>,
        libraryRelease: ProductRelease,
        frozen: Set<ProductId>,
        policy: ResolverPolicy,
    ): Attempt {
        val targetApi = requireNotNull(libraryRelease.providesApi)
        val domains = linkedMapOf<ProductId, List<ProductRelease>>()
        domains[libraryComponent] = listOf(libraryRelease)
        val reasons = mutableListOf<BlockedReason>()

        installed.sortedBy { it.product }.filter { it.product != libraryComponent }.forEach { current ->
            val currentRelease = current.asRelease()
            val compatibleUpdates = releases
                .filter { it.product == current.product && it.version >= current.version }
                .filter { it.supportedApi.supports(targetApi) }
                .filter { externalPluginDependenciesAvailable(it, policy) }
            val domain = if (current.product in frozen) {
                if (current.supportedApi.supports(targetApi)) listOf(currentRelease) else emptyList()
            } else {
                (compatibleUpdates + currentRelease.takeIf { current.supportedApi.supports(targetApi) })
                    .filterNotNull()
                    .distinctBy { it.version.toString() }
                    .sortedByDescending(ProductRelease::version)
            }
            if (domain.isEmpty()) {
                if (current.product in frozen) reasons += BlockedReason.Frozen(current.product)
                reasons += BlockedReason.NoCompatibleRelease(current.product, targetApi)
            } else {
                domains[current.product] = domain
            }
        }
        if (reasons.isNotEmpty()) return Attempt(null, reasons)

        if (policy.allowManagedInstalls) {
            var added: Boolean
            do {
                added = false
                val missing = domains.values.flatten().flatMap(ProductRelease::dependencies)
                    .map(ProductDependency::product).filterNot(domains::containsKey).distinct().sorted()
                missing.forEach { dependency ->
                    val candidates = releases.filter {
                        it.product == dependency && it.supportedApi.supports(targetApi) && externalPluginDependenciesAvailable(it, policy)
                    }
                        .distinctBy { it.version.toString() }.sortedByDescending(ProductRelease::version)
                    if (candidates.isNotEmpty()) {
                        domains[dependency] = candidates
                        added = true
                    }
                }
            } while (added)
        }

        val ids = domains.keys.filter { it != libraryComponent }.sorted()
        val selected = linkedMapOf(libraryComponent to libraryRelease)
        val solution = search(ids, 0, domains, selected)
            ?: return Attempt(null, dependencyReasons(domains))
        val installedById = installed.associateBy(InstalledProduct::product)
        val retained = retainRequired(solution, installedById.keys)
        val ordered = retained.values.sortedBy(ProductRelease::product)
        val changes = ordered.mapNotNull { target ->
            val current = installedById[target.product]
            if (current?.version == target.version) null
            else ProductChange(target.product, current?.version, target.version)
        }
        val unavailableRequestedDependencies = if (policy.allowManagedInstalls) emptyList() else
            releases.filter { candidate ->
                val current = installedById[candidate.product]
                current != null && candidate.version > current.version && candidate.supportedApi.supports(targetApi)
            }.flatMap { candidate ->
                candidate.dependencies.mapNotNull { dependency ->
                    if (domains.containsKey(dependency.product)) null
                    else BlockedReason.MissingDependency(candidate.product, dependency.product, dependency.minimumVersion)
                }
            }.distinct()
        val installedExternal = policy.installedExternalPlugins.map { it.lowercase() }.toSet()
        val externalReasons = releases.filter { candidate ->
            val current = installedById[candidate.product]
            current != null && candidate.version > current.version && candidate.supportedApi.supports(targetApi)
        }.flatMap { release -> release.externalPluginDependencies.mapNotNull { dependency ->
            if (dependency.plugin.lowercase() in installedExternal) null
            else BlockedReason.MissingExternalPluginDependency(
                release.product, dependency.plugin, dependency.minimumVersion, dependency.downloadPage?.toString(),
            )
        } }.distinct()
        return Attempt(UpdatePlan(targetApi, changes, ordered), (unavailableRequestedDependencies + externalReasons).distinct())
    }

    private fun search(
        ids: List<ProductId>,
        index: Int,
        domains: Map<ProductId, List<ProductRelease>>,
        selected: LinkedHashMap<ProductId, ProductRelease>,
    ): LinkedHashMap<ProductId, ProductRelease>? {
        if (index == ids.size) return if (dependenciesSatisfied(selected)) LinkedHashMap(selected) else null
        val id = ids[index]
        for (candidate in domains.getValue(id)) {
            selected[id] = candidate
            search(ids, index + 1, domains, selected)?.let { return it }
        }
        selected.remove(id)
        return null
    }

    private fun dependenciesSatisfied(selected: Map<ProductId, ProductRelease>): Boolean =
        selected.values.all { release ->
            release.dependencies.all { dependency ->
                val target = selected[dependency.product]
                target != null && target.version >= dependency.minimumVersion
            }
        }

    private fun retainRequired(
        selected: LinkedHashMap<ProductId, ProductRelease>,
        installed: Set<ProductId>,
    ): LinkedHashMap<ProductId, ProductRelease> {
        val required = installed.toMutableSet()
        var changed: Boolean
        do {
            changed = false
            required.toList().forEach { id -> selected[id]?.dependencies?.forEach { dependency ->
                if (required.add(dependency.product)) changed = true
            } }
        } while (changed)
        return LinkedHashMap(selected.filterKeys(required::contains))
    }

    private fun externalPluginDependenciesAvailable(release: ProductRelease, policy: ResolverPolicy): Boolean {
        val installed = policy.installedExternalPlugins.map { it.lowercase() }.toSet()
        return release.externalPluginDependencies.all { it.plugin.lowercase() in installed }
    }

    private fun dependencyReasons(domains: Map<ProductId, List<ProductRelease>>): List<BlockedReason> {
        val available = domains.mapValues { (_, candidates) -> candidates.maxOf(ProductRelease::version) }
        return domains.values.flatten().flatMap { release ->
            release.dependencies.mapNotNull { dependency ->
                if (available[dependency.product]?.let { it >= dependency.minimumVersion } == true) null
                else BlockedReason.MissingDependency(
                    release.product,
                    dependency.product,
                    dependency.minimumVersion,
                )
            }
        }.distinct().ifEmpty {
            listOf(BlockedReason.NoCompatibleRelease(libraryComponent, domains.getValue(libraryComponent).single().providesApi!!))
        }
    }

    private fun InstalledProduct.asRelease() = ProductRelease(
        product = product,
        version = version,
        channel = UpdateChannel.STABLE,
        supportedApi = supportedApi,
        providesApi = providesApi,
    )

    private data class Attempt(val plan: UpdatePlan?, val reasons: List<BlockedReason>)
}
