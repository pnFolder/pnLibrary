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
class UpdateResolver(private val libraryComponent: ComponentId) {
    @JvmOverloads
    fun resolve(
        installed: List<InstalledComponent>,
        releases: List<ComponentRelease>,
        channels: Map<ComponentId, UpdateChannel> = emptyMap(),
        defaultChannel: UpdateChannel = UpdateChannel.STABLE,
        frozen: Set<ComponentId> = emptySet(),
        platform: PlatformType? = null,
        javaFeature: Int = Int.MAX_VALUE,
        policy: ResolverPolicy = ResolverPolicy(),
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
        }.filter { release -> release.artifacts.isEmpty() || release.artifacts.any { artifact ->
            (platform == null || artifact.platform == platform) && artifact.supports(javaFeature)
        } }
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
        policy: ResolverPolicy,
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
                .filter { externalDependenciesAvailable(it, policy) }
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

        if (policy.allowManagedInstalls) {
            var added: Boolean
            do {
                added = false
                val missing = domains.values.flatten().flatMap(ComponentRelease::dependencies)
                    .map(ComponentDependency::component).filterNot(domains::containsKey).distinct().sorted()
                missing.forEach { dependency ->
                    val candidates = releases.filter {
                        it.component == dependency && it.supportedApi.supports(targetApi) && externalDependenciesAvailable(it, policy)
                    }
                        .distinctBy { it.version.toString() }.sortedByDescending(ComponentRelease::version)
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
        val installedById = installed.associateBy(InstalledComponent::component)
        val retained = retainRequired(solution, installedById.keys)
        val ordered = retained.values.sortedBy(ComponentRelease::component)
        val changes = ordered.mapNotNull { target ->
            val current = installedById[target.component]
            if (current?.version == target.version) null
            else ComponentChange(target.component, current?.version, target.version)
        }
        val unavailableRequestedDependencies = if (policy.allowManagedInstalls) emptyList() else
            releases.filter { candidate ->
                val current = installedById[candidate.component]
                current != null && candidate.version > current.version && candidate.supportedApi.supports(targetApi)
            }.flatMap { candidate ->
                candidate.dependencies.mapNotNull { dependency ->
                    if (domains.containsKey(dependency.component)) null
                    else BlockedReason.MissingDependency(candidate.component, dependency.component, dependency.minimumVersion)
                }
            }.distinct()
        val installedExternal = policy.installedExternalPlugins.map { it.lowercase() }.toSet()
        val externalReasons = releases.filter { candidate ->
            val current = installedById[candidate.component]
            current != null && candidate.version > current.version && candidate.supportedApi.supports(targetApi)
        }.flatMap { release -> release.externalDependencies.mapNotNull { dependency ->
            if (dependency.plugin.lowercase() in installedExternal) null
            else BlockedReason.MissingExternalDependency(
                release.component, dependency.plugin, dependency.minimumVersion, dependency.downloadPage?.toString(),
            )
        } }.distinct()
        return Attempt(UpdatePlan(targetApi, changes, ordered), (unavailableRequestedDependencies + externalReasons).distinct())
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

    private fun retainRequired(
        selected: LinkedHashMap<ComponentId, ComponentRelease>,
        installed: Set<ComponentId>,
    ): LinkedHashMap<ComponentId, ComponentRelease> {
        val required = installed.toMutableSet()
        var changed: Boolean
        do {
            changed = false
            required.toList().forEach { id -> selected[id]?.dependencies?.forEach { dependency ->
                if (required.add(dependency.component)) changed = true
            } }
        } while (changed)
        return LinkedHashMap(selected.filterKeys(required::contains))
    }

    private fun externalDependenciesAvailable(release: ComponentRelease, policy: ResolverPolicy): Boolean {
        val installed = policy.installedExternalPlugins.map { it.lowercase() }.toSet()
        return release.externalDependencies.all { it.plugin.lowercase() in installed }
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
