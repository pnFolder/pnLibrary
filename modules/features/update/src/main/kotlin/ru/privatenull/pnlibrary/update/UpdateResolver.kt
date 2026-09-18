package ru.privatenull.pnlibrary.update

import ru.privatenull.pnlibrary.api.updates.*

/** Result of resolving all managed components as one compatible ecosystem. */
sealed class ResolutionResult {
    data class Ready(val plan: UpdatePlan) : ResolutionResult()
    data class Blocked(
        val reasons: List<BlockedReason>,
        val fallbackPlan: UpdatePlan?,
    ) : ResolutionResult()
}

/** Deterministic API, channel, freeze, and dependency resolver for pnUpdate. */
class UpdateResolver(private val libraryComponent: ComponentId) {
    @JvmOverloads
    fun resolve(
        installed: List<InstalledComponent>,
        releases: List<ComponentRelease>,
        channels: Map<ComponentId, UpdateChannel> = emptyMap(),
        defaultChannel: UpdateChannel = UpdateChannel.STABLE,
        frozen: Set<ComponentId> = emptySet(),
    ): ResolutionResult {
        require(installed.isNotEmpty()) { "at least one installed component is required" }
        require(installed.map(InstalledComponent::component).distinct().size == installed.size) {
            "installed component IDs must be unique"
        }
        val installedById = installed.associateBy(InstalledComponent::component)
        val currentLibrary = requireNotNull(installedById[libraryComponent]) {
            "installed pnLibrary component is required"
        }
        require(currentLibrary.providesApi != null) { "installed pnLibrary must provide an API generation" }

        val allowedReleases = releases.filter { release ->
            channels.getOrDefault(release.component, defaultChannel).accepts(release.channel)
        }
        val currentLibraryRelease = currentLibrary.asRelease()
        val libraryDomain = if (libraryComponent in frozen) {
            listOf(currentLibraryRelease)
        } else {
            (allowedReleases.filter { it.component == libraryComponent } + currentLibraryRelease)
                .filter { it.providesApi != null && it.version >= currentLibrary.version }
                .distinctBy { it.version.toString() }
                .sortedByDescending(ComponentRelease::version)
        }

        var primaryFailure: List<BlockedReason>? = null
        libraryDomain.forEachIndexed { index, libraryRelease ->
            val attempt = resolveForApi(
                installed,
                allowedReleases,
                libraryRelease,
                frozen,
            )
            if (attempt.plan != null) {
                if (index == 0 && primaryFailure == null) return ResolutionResult.Ready(attempt.plan)
                return ResolutionResult.Blocked(primaryFailure.orEmpty(), attempt.plan)
            }
            if (primaryFailure == null) primaryFailure = attempt.reasons
        }

        if (libraryComponent in frozen && allowedReleases.any {
                it.component == libraryComponent && it.version > currentLibrary.version
            }
        ) {
            primaryFailure = listOf(BlockedReason.Frozen(libraryComponent)) + primaryFailure.orEmpty()
        }
        return ResolutionResult.Blocked(primaryFailure.orEmpty().distinct(), null)
    }

    private fun resolveForApi(
        installed: List<InstalledComponent>,
        releases: List<ComponentRelease>,
        libraryRelease: ComponentRelease,
        frozen: Set<ComponentId>,
    ): Attempt {
        val targetApi = requireNotNull(libraryRelease.providesApi)
        val domains = linkedMapOf<ComponentId, List<ComponentRelease>>()
        domains[libraryComponent] = listOf(libraryRelease)
        val reasons = mutableListOf<BlockedReason>()

        installed.sortedBy { it.component }.filter { it.component != libraryComponent }.forEach { current ->
            val currentRelease = current.asRelease()
            val compatibleUpdates = releases
                .filter { it.component == current.component && it.version >= current.version }
                .filter { it.supportedApi.supports(targetApi) }
            val domain = if (current.component in frozen) {
                if (current.supportedApi.supports(targetApi)) listOf(currentRelease) else emptyList()
            } else {
                (compatibleUpdates + currentRelease.takeIf { current.supportedApi.supports(targetApi) })
                    .filterNotNull()
                    .distinctBy { it.version.toString() }
                    .sortedByDescending(ComponentRelease::version)
            }
            if (domain.isEmpty()) {
                if (current.component in frozen) reasons += BlockedReason.Frozen(current.component)
                reasons += BlockedReason.NoCompatibleRelease(current.component, targetApi)
            } else {
                domains[current.component] = domain
            }
        }
        if (reasons.isNotEmpty()) return Attempt(null, reasons)

        val ids = domains.keys.filter { it != libraryComponent }.sorted()
        val selected = linkedMapOf(libraryComponent to libraryRelease)
        val solution = search(ids, 0, domains, selected)
            ?: return Attempt(null, dependencyReasons(domains))
        val installedById = installed.associateBy(InstalledComponent::component)
        val ordered = solution.values.sortedBy(ComponentRelease::component)
        val changes = ordered.mapNotNull { target ->
            val current = installedById[target.component] ?: return@mapNotNull null
            if (current.version == target.version) null
            else ComponentChange(target.component, current.version, target.version)
        }
        return Attempt(UpdatePlan(targetApi, changes, ordered), emptyList())
    }

    private fun search(
        ids: List<ComponentId>,
        index: Int,
        domains: Map<ComponentId, List<ComponentRelease>>,
        selected: LinkedHashMap<ComponentId, ComponentRelease>,
    ): LinkedHashMap<ComponentId, ComponentRelease>? {
        if (index == ids.size) return if (dependenciesSatisfied(selected)) LinkedHashMap(selected) else null
        val id = ids[index]
        for (candidate in domains.getValue(id)) {
            selected[id] = candidate
            search(ids, index + 1, domains, selected)?.let { return it }
        }
        selected.remove(id)
        return null
    }

    private fun dependenciesSatisfied(selected: Map<ComponentId, ComponentRelease>): Boolean =
        selected.values.all { release ->
            release.dependencies.all { dependency ->
                val target = selected[dependency.component]
                target != null && target.version >= dependency.minimumVersion
            }
        }

    private fun dependencyReasons(domains: Map<ComponentId, List<ComponentRelease>>): List<BlockedReason> {
        val available = domains.mapValues { (_, candidates) -> candidates.maxOf(ComponentRelease::version) }
        return domains.values.flatten().flatMap { release ->
            release.dependencies.mapNotNull { dependency ->
                if (available[dependency.component]?.let { it >= dependency.minimumVersion } == true) null
                else BlockedReason.MissingDependency(
                    release.component,
                    dependency.component,
                    dependency.minimumVersion,
                )
            }
        }.distinct().ifEmpty {
            listOf(BlockedReason.NoCompatibleRelease(libraryComponent, domains.getValue(libraryComponent).single().providesApi!!))
        }
    }

    private fun InstalledComponent.asRelease() = ComponentRelease(
        component = component,
        version = version,
        channel = UpdateChannel.STABLE,
        supportedApi = supportedApi,
        providesApi = providesApi,
    )

    private data class Attempt(val plan: UpdatePlan?, val reasons: List<BlockedReason>)
}
