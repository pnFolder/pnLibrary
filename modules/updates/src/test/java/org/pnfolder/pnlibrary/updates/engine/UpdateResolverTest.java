package org.pnfolder.pnlibrary.updates.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.dependencies.external.ExternalPluginDependency;
import org.pnfolder.pnlibrary.api.platform.PlatformType;
import org.pnfolder.pnlibrary.api.updates.product.ProductId;
import org.pnfolder.pnlibrary.api.updates.product.UpdateChannel;
import org.pnfolder.pnlibrary.api.updates.release.ArtifactDescriptor;
import org.pnfolder.pnlibrary.api.updates.release.InstalledProduct;
import org.pnfolder.pnlibrary.api.updates.release.ProductChange;
import org.pnfolder.pnlibrary.api.updates.release.ProductDependency;
import org.pnfolder.pnlibrary.api.updates.release.ProductRelease;
import org.pnfolder.pnlibrary.api.updates.state.BlockedReason;
import org.pnfolder.pnlibrary.api.version.ApiVersionRange;
import org.pnfolder.pnlibrary.api.version.SemanticVersion;

class UpdateResolverTest {
    private final ProductId library = ProductId.of("pnlibrary");
    private final ProductId market = ProductId.of("pnmarket");
    private final ProductId economy = ProductId.of("pneconomy");

    private static SemanticVersion version(String value) {
        return SemanticVersion.parse(value);
    }

    private static ApiVersionRange range(int min) {
        return new ApiVersionRange(min, min);
    }

    private static ApiVersionRange range(int min, int max) {
        return new ApiVersionRange(min, max);
    }

    private static InstalledProduct installed(ProductId id, String version, ApiVersionRange api) {
        return new InstalledProduct(id, version(version), api, null);
    }

    private static InstalledProduct installed(ProductId id, String version, ApiVersionRange api, Integer provides) {
        return new InstalledProduct(id, version(version), api, provides);
    }

    private static ProductRelease release(ProductId id, String version, ApiVersionRange api) {
        return release(id, version, api, null, UpdateChannel.STABLE, Collections.emptyList(),
            Collections.emptyList(), Collections.emptyList());
    }

    private static ProductRelease release(ProductId id, String version, ApiVersionRange api, Integer provides) {
        return release(id, version, api, provides, UpdateChannel.STABLE, Collections.emptyList(),
            Collections.emptyList(), Collections.emptyList());
    }

    private static ProductRelease release(ProductId id, String version, ApiVersionRange api, Integer provides,
                                          UpdateChannel channel, List<ProductDependency> dependencies,
                                          List<ExternalPluginDependency> external, List<ArtifactDescriptor> artifacts) {
        return new ProductRelease(id, version(version), channel, api, provides, dependencies, null, artifacts, external, null, null, "");
    }

    private ResolutionResult resolve(List<InstalledProduct> installed, List<ProductRelease> releases) {
        return new UpdateResolver(library).resolve(installed, releases);
    }

    private static ProductRelease selected(org.pnfolder.pnlibrary.api.updates.release.UpdatePlan plan, ProductId product) {
        ProductRelease found = null;
        for (ProductRelease release : plan.getSelected()) {
            if (release.getProduct().equals(product)) {
                assertNull(found, "more than one selected release of " + product);
                found = release;
            }
        }
        assertNotNull(found, "no selected release of " + product);
        return found;
    }

    @SafeVarargs
    private static <T> List<T> list(T... values) {
        return new ArrayList<>(Arrays.asList(values));
    }

    @Test
    @DisplayName("selects newest release compatible with installed API instead of latest")
    void selectsNewestReleaseCompatibleWithInstalledApiInsteadOfLatest() {
        ResolutionResult.Ready result = (ResolutionResult.Ready) resolve(
            list(installed(library, "2.8.0", range(4), 4), installed(market, "3.9.6", range(4))),
            list(release(market, "4.0.0", range(5)), release(market, "3.9.7", range(4))));

        assertEquals(version("3.9.7"), selected(result.getPlan(), market).getVersion());
    }

    @Test
    @DisplayName("builds an atomic cross API migration and reuses pre-compatible plugins")
    void buildsAnAtomicCrossApiMigrationAndReusesPreCompatiblePlugins() {
        ResolutionResult.Ready result = (ResolutionResult.Ready) resolve(
            list(installed(library, "2.9.0", range(4), 4), installed(market, "3.0.0", range(4, 5)),
                installed(economy, "2.0.0", range(4))),
            list(release(library, "3.0.0", range(5), 5), release(economy, "3.0.0", range(5))));

        assertEquals(5, result.getPlan().getTargetApi());
        assertEquals(new HashSet<>(Arrays.asList(library, economy)), changed(result));
        assertEquals(version("3.0.0"), selected(result.getPlan(), market).getVersion());
    }

    @Test
    @DisplayName("reports blocked future API while returning old generation fallback updates")
    void reportsBlockedFutureApiWhileReturningOldGenerationFallbackUpdates() {
        ResolutionResult.Blocked result = (ResolutionResult.Blocked) resolve(
            list(installed(library, "2.9.0", range(4), 4), installed(market, "2.7.1", range(3, 4))),
            list(release(library, "3.0.0", range(5), 5), release(library, "2.9.4", range(4), 4)));

        boolean reported = false;
        for (BlockedReason reason : result.getReasons()) {
            reported |= reason instanceof BlockedReason.NoCompatibleRelease
                && ((BlockedReason.NoCompatibleRelease) reason).getProduct().equals(market);
        }
        assertTrue(reported);
        assertEquals(version("2.9.4"), selected(result.getFallbackPlan(), library).getVersion());
    }

    @Test
    @DisplayName("satisfies dependency closure and component channel overrides")
    void satisfiesDependencyClosureAndComponentChannelOverrides() {
        ProductDependency dependency = new ProductDependency(economy, version("3.0.0"));
        ResolutionResult.Ready result = (ResolutionResult.Ready) new UpdateResolver(library).resolve(
            list(installed(library, "2.9.0", range(4), 4), installed(market, "3.0.0", range(4)),
                installed(economy, "2.0.0", range(4))),
            list(release(market, "4.0.0", range(4), null, UpdateChannel.STABLE, list(dependency),
                    Collections.emptyList(), Collections.emptyList()),
                release(economy, "3.1.0", range(4), null, UpdateChannel.BETA, Collections.emptyList(),
                    Collections.emptyList(), Collections.emptyList())),
            Collections.singletonMap(economy, UpdateChannel.BETA), UpdateChannel.STABLE, Collections.emptySet(),
            null, Integer.MAX_VALUE, new ResolverPolicy());

        assertEquals(new HashSet<>(Arrays.asList(market, economy)), changed(result));
    }

    @Test
    @DisplayName("freeze blocks only a transition that requires the frozen component")
    void freezeBlocksOnlyATransitionThatRequiresTheFrozenComponent() {
        ResolutionResult.Blocked result = (ResolutionResult.Blocked) new UpdateResolver(library).resolve(
            list(installed(library, "2.9.0", range(4), 4), installed(market, "2.7.1", range(4))),
            list(release(library, "3.0.0", range(5), 5), release(market, "3.0.0", range(5))),
            Collections.emptyMap(), UpdateChannel.STABLE, Collections.singleton(market),
            null, Integer.MAX_VALUE, new ResolverPolicy());

        boolean reported = false;
        for (BlockedReason reason : result.getReasons()) {
            reported |= reason instanceof BlockedReason.Frozen && ((BlockedReason.Frozen) reason).getProduct().equals(market);
        }
        assertTrue(reported);
        assertEquals(4, result.getFallbackPlan().getTargetApi());
    }

    @Test
    @DisplayName("blocks the entire API migration when one installed plugin has no compatible release")
    void blocksTheEntireApiMigrationWhenOneInstalledPluginHasNoCompatibleRelease() {
        ResolutionResult.Blocked result = (ResolutionResult.Blocked) resolve(
            list(installed(library, "1.0.0", range(1), 1), installed(market, "1.0.0", range(1)),
                installed(economy, "1.0.0", range(1))),
            list(release(library, "2.0.0", range(2), 2), release(market, "2.0.0", range(2))));

        boolean reported = false;
        for (BlockedReason reason : result.getReasons()) {
            reported |= reason instanceof BlockedReason.NoCompatibleRelease
                && ((BlockedReason.NoCompatibleRelease) reason).getProduct().equals(economy);
        }
        assertTrue(reported);
        boolean libraryUntouched = true;
        if (result.getFallbackPlan() != null) {
            for (ProductChange change : result.getFallbackPlan().getChanges()) {
                libraryUntouched &= !change.getProduct().equals(library);
            }
        }
        assertTrue(libraryUntouched);
    }

    @Test
    @DisplayName("introduces a missing managed dependency only when policy permits")
    void introducesAMissingManagedDependencyOnlyWhenPolicyPermits() {
        ProductDependency dependency = new ProductDependency(economy, version("2.0.0"));
        List<ProductRelease> releases = list(
            release(market, "2.0.0", range(1), null, UpdateChannel.STABLE, list(dependency),
                Collections.emptyList(), Collections.emptyList()),
            release(economy, "2.1.0", range(1)));
        List<InstalledProduct> installed = list(installed(library, "1.0.0", range(1), 1), installed(market, "1.0.0", range(1)));

        ResolutionResult.Blocked blocked = (ResolutionResult.Blocked) resolve(installed, releases);
        boolean reported = false;
        for (BlockedReason reason : blocked.getReasons()) {
            reported |= reason instanceof BlockedReason.MissingDependency
                && ((BlockedReason.MissingDependency) reason).getDependency().equals(economy);
        }
        assertTrue(reported);

        ResolutionResult.Ready ready = (ResolutionResult.Ready) new UpdateResolver(library).resolve(
            installed, releases, Collections.emptyMap(), UpdateChannel.STABLE,
            Collections.emptySet(), PlatformType.BUKKIT, 17,
            new ResolverPolicy(true, Collections.emptySet()));
        assertEquals(version("2.1.0"), selected(ready.getPlan(), economy).getVersion());
    }

    @Test
    @DisplayName("reports manual external dependency and filters incompatible platform artifact")
    void reportsManualExternalDependencyAndFiltersIncompatiblePlatformArtifact() {
        ExternalPluginDependency vault = ExternalPluginDependency.builder("Vault", "1.7.3")
            .downloadPage("https://github.com/MilkBowl/Vault/releases").build();
        ArtifactDescriptor velocityOnly = new ArtifactDescriptor(
            "market.jar", PlatformType.VELOCITY, 17, null, 10L, repeat("00", 32), null);
        ResolutionResult.Blocked result = (ResolutionResult.Blocked) new UpdateResolver(library).resolve(
            list(installed(library, "1.0.0", range(1), 1), installed(market, "1.0.0", range(1))),
            list(release(market, "3.0.0", range(1), null, UpdateChannel.STABLE, Collections.emptyList(),
                    Collections.emptyList(), list(velocityOnly)),
                release(market, "2.0.0", range(1), null, UpdateChannel.STABLE, Collections.emptyList(),
                    list(vault), Collections.emptyList())),
            Collections.emptyMap(), UpdateChannel.STABLE, Collections.emptySet(),
            PlatformType.BUKKIT, 17, new ResolverPolicy());

        boolean reported = false;
        for (BlockedReason reason : result.getReasons()) {
            reported |= reason instanceof BlockedReason.MissingExternalPluginDependency
                && "Vault".equals(((BlockedReason.MissingExternalPluginDependency) reason).getPlugin());
        }
        assertTrue(reported);
        assertEquals(version("1.0.0"), selected(result.getFallbackPlan(), market).getVersion());
    }

    private static java.util.Set<ProductId> changed(ResolutionResult.Ready result) {
        java.util.Set<ProductId> products = new HashSet<>();
        for (ProductChange change : result.getPlan().getChanges()) {
            products.add(change.getProduct());
        }
        return products;
    }

    private static String repeat(String text, int times) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < times; i++) {
            builder.append(text);
        }
        return builder.toString();
    }
}
