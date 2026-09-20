package ru.privatenull.pnlibrary.update

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.privatenull.pnlibrary.api.updates.*
import ru.privatenull.pnlibrary.api.version.ApiVersionRange
import ru.privatenull.pnlibrary.api.version.SemanticVersion
import ru.privatenull.pnlibrary.api.platform.PlatformType

class UpdateResolverTest {
    private val library = ComponentId.of("pnlibrary")
    private val market = ComponentId.of("pnmarket")
    private val economy = ComponentId.of("pneconomy")
    private fun version(value: String) = SemanticVersion.parse(value)
    private fun range(min: Int, max: Int = min) = ApiVersionRange(min, max)
    private fun installed(id: ComponentId, version: String, api: ApiVersionRange, provides: Int? = null) =
        InstalledComponent(id, version(version), api, provides)
    private fun release(
        id: ComponentId,
        version: String,
        api: ApiVersionRange,
        provides: Int? = null,
        channel: UpdateChannel = UpdateChannel.STABLE,
        dependencies: List<ComponentDependency> = emptyList(),
        external: List<ExternalDependency> = emptyList(),
        artifacts: List<ArtifactDescriptor> = emptyList(),
    ) = ComponentRelease(id, version(version), channel, api, provides, dependencies, artifacts = artifacts, externalDependencies = external)

    @Test
    fun `selects newest release compatible with installed API instead of latest`() {
        val result = UpdateResolver(library).resolve(
            installed = listOf(
                installed(library, "2.8.0", range(4), 4),
                installed(market, "3.9.6", range(4)),
            ),
            releases = listOf(
                release(market, "4.0.0", range(5)),
                release(market, "3.9.7", range(4)),
            ),
        ) as ResolutionResult.Ready

        assertEquals(version("3.9.7"), result.plan.selected.single { it.component == market }.version)
    }

    @Test
    fun `builds an atomic cross API migration and reuses pre-compatible plugins`() {
        val result = UpdateResolver(library).resolve(
            installed = listOf(
                installed(library, "2.9.0", range(4), 4),
                installed(market, "3.0.0", range(4, 5)),
                installed(economy, "2.0.0", range(4)),
            ),
            releases = listOf(
                release(library, "3.0.0", range(5), 5),
                release(economy, "3.0.0", range(5)),
            ),
        ) as ResolutionResult.Ready

        assertEquals(5, result.plan.targetApi)
        assertEquals(setOf(library, economy), result.plan.changes.map { it.component }.toSet())
        assertEquals(version("3.0.0"), result.plan.selected.single { it.component == market }.version)
    }

    @Test
    fun `reports blocked future API while returning old generation fallback updates`() {
        val result = UpdateResolver(library).resolve(
            installed = listOf(
                installed(library, "2.9.0", range(4), 4),
                installed(market, "2.7.1", range(3, 4)),
            ),
            releases = listOf(
                release(library, "3.0.0", range(5), 5),
                release(library, "2.9.4", range(4), 4),
            ),
        ) as ResolutionResult.Blocked

        assertTrue(result.reasons.any { it is BlockedReason.NoCompatibleRelease && it.component == market })
        assertEquals(version("2.9.4"), result.fallbackPlan!!.selected.single { it.component == library }.version)
    }

    @Test
    fun `satisfies dependency closure and component channel overrides`() {
        val dependency = ComponentDependency(economy, version("3.0.0"))
        val result = UpdateResolver(library).resolve(
            installed = listOf(
                installed(library, "2.9.0", range(4), 4),
                installed(market, "3.0.0", range(4)),
                installed(economy, "2.0.0", range(4)),
            ),
            releases = listOf(
                release(market, "4.0.0", range(4), dependencies = listOf(dependency)),
                release(economy, "3.1.0", range(4), channel = UpdateChannel.BETA),
            ),
            channels = mapOf(economy to UpdateChannel.BETA),
        ) as ResolutionResult.Ready

        assertEquals(setOf(market, economy), result.plan.changes.map { it.component }.toSet())
    }

    @Test
    fun `freeze blocks only a transition that requires the frozen component`() {
        val result = UpdateResolver(library).resolve(
            installed = listOf(
                installed(library, "2.9.0", range(4), 4),
                installed(market, "2.7.1", range(4)),
            ),
            releases = listOf(
                release(library, "3.0.0", range(5), 5),
                release(market, "3.0.0", range(5)),
            ),
            frozen = setOf(market),
        ) as ResolutionResult.Blocked

        assertTrue(result.reasons.any { it is BlockedReason.Frozen && it.component == market })
        assertEquals(4, result.fallbackPlan!!.targetApi)
    }

    @Test
    fun `blocks the entire API migration when one installed plugin has no compatible release`() {
        val result = UpdateResolver(library).resolve(
            installed = listOf(
                installed(library, "1.0.0", range(1), 1),
                installed(market, "1.0.0", range(1)),
                installed(economy, "1.0.0", range(1)),
            ),
            releases = listOf(
                release(library, "2.0.0", range(2), 2),
                release(market, "2.0.0", range(2)),
            ),
        ) as ResolutionResult.Blocked

        assertTrue(result.reasons.any { it is BlockedReason.NoCompatibleRelease && it.component == economy })
        assertTrue(result.fallbackPlan == null || result.fallbackPlan.changes.none { it.component == library })
    }

    @Test
    fun `introduces a missing managed dependency only when policy permits`() {
        val dependency = ComponentDependency(economy, version("2.0.0"))
        val releases = listOf(
            release(market, "2.0.0", range(1), dependencies = listOf(dependency)),
            release(economy, "2.1.0", range(1)),
        )
        val installed = listOf(installed(library, "1.0.0", range(1), 1), installed(market, "1.0.0", range(1)))

        val blocked = UpdateResolver(library).resolve(installed, releases) as ResolutionResult.Blocked
        assertTrue(blocked.reasons.any { it is BlockedReason.MissingDependency && it.dependency == economy })

        val ready = UpdateResolver(library).resolve(
            installed, releases, policy = ResolverPolicy(allowManagedInstalls = true),
            platform = PlatformType.BUKKIT, javaFeature = 17,
        ) as ResolutionResult.Ready
        assertEquals(version("2.1.0"), ready.plan.selected.single { it.component == economy }.version)
    }

    @Test
    fun `reports manual external dependency and filters incompatible platform artifact`() {
        val vault = ExternalDependency.builder("Vault", "1.7.3")
            .downloadPage("https://github.com/MilkBowl/Vault/releases").build()
        val velocityOnly = ArtifactDescriptor(
            "market.jar", PlatformType.VELOCITY, 17, null, 10, "00".repeat(32), null,
        )
        val result = UpdateResolver(library).resolve(
            installed = listOf(installed(library, "1.0.0", range(1), 1), installed(market, "1.0.0", range(1))),
            releases = listOf(
                release(market, "3.0.0", range(1), artifacts = listOf(velocityOnly)),
                release(market, "2.0.0", range(1), external = listOf(vault)),
            ),
            platform = PlatformType.BUKKIT,
            javaFeature = 17,
        ) as ResolutionResult.Blocked

        assertTrue(result.reasons.any { it is BlockedReason.MissingExternalDependency && it.plugin == "Vault" })
        assertEquals(version("1.0.0"), result.fallbackPlan!!.selected.single { it.component == market }.version)
    }
}
