package org.pnfolder.pnlibrary.updates.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import org.pnfolder.pnlibrary.api.updates.release.UpdatePlan;
import org.pnfolder.pnlibrary.api.updates.state.ApiRaised;
import org.pnfolder.pnlibrary.api.updates.state.BlockedReason;
import org.pnfolder.pnlibrary.api.updates.state.UnsupportedApi;
import org.pnfolder.pnlibrary.api.updates.state.WaitsForLibrary;
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
    @DisplayName("library moves to the new API; a plugin that cannot follow stays and is noted")
    void libraryMovesToTheNewApiAndUnsupportedPluginStaysNoted() {
        ResolutionResult.Ready result = (ResolutionResult.Ready) resolve(
            list(installed(library, "2.9.0", range(4), 4), installed(market, "2.7.1", range(3, 4))),
            list(release(library, "3.0.0", range(5), 5), release(library, "2.9.4", range(4), 4)));

        assertEquals(version("3.0.0"), selected(result.getPlan(), library).getVersion());
        assertEquals(5, result.getPlan().getTargetApi());
        assertEquals(Collections.singleton(library), changed(result));
        assertTrue(result.getAdvisories().contains(new UnsupportedApi(market, range(3, 4), 5)));
        assertTrue(result.getAdvisories().contains(new ApiRaised(library, 4, 5)));
    }

    @Test
    @DisplayName("plugin whose newest release needs a newer API than the library has is not updated and waits")
    void pluginWithReleaseForNewerApiWaitsForLibrary() {
        ResolutionResult.Ready result = (ResolutionResult.Ready) resolve(
            list(installed(library, "1.0.0", range(1), 1), installed(market, "1.0.0", range(1))),
            list(release(market, "2.0.0", range(2)), release(market, "1.1.0", range(1))));

        assertEquals(version("1.1.0"), selected(result.getPlan(), market).getVersion());
        assertEquals(1, result.getPlan().getTargetApi());
        assertEquals(Collections.singletonList(new WaitsForLibrary(market, version("2.0.0"), 2, 1)), result.getAdvisories());
    }

    @Test
    @DisplayName("plugin that needs the newer API is installed together with the library release that provides it")
    void pluginForNewerApiIsInstalledOnceTheLibraryProvidesIt() {
        List<InstalledProduct> installed = list(installed(library, "1.0.0", range(1), 1), installed(market, "1.0.0", range(1)));
        List<ProductRelease> releases = list(release(market, "2.0.0", range(2)));
        ResolutionResult.Ready waiting = (ResolutionResult.Ready) resolve(installed, releases);
        assertTrue(changed(waiting).isEmpty());

        releases.add(release(library, "2.0.0", range(2), 2));
        ResolutionResult.Ready together = (ResolutionResult.Ready) resolve(installed, releases);
        assertEquals(new HashSet<>(Arrays.asList(library, market)), changed(together));

        // The library already stands on API 2: the next check installs the plugin by itself.
        ResolutionResult.Ready next = (ResolutionResult.Ready) resolve(
            list(installed(library, "2.0.0", range(2), 2), installed(market, "1.0.0", range(1, 2))), releases);
        assertEquals(Collections.singleton(market), changed(next));
        assertTrue(next.getAdvisories().isEmpty());
    }

    @Test
    @DisplayName("both on one API gives an ordinary update without notes")
    void bothOnOneApiGivesOrdinaryUpdate() {
        ResolutionResult.Ready result = (ResolutionResult.Ready) resolve(
            list(installed(library, "1.0.0", range(1), 1), installed(market, "1.0.0", range(1))),
            list(release(library, "1.1.0", range(1), 1), release(market, "1.1.0", range(1))));

        assertEquals(new HashSet<>(Arrays.asList(library, market)), changed(result));
        assertTrue(result.getAdvisories().isEmpty());
    }

    @Test
    @DisplayName("a held library stays; a held plugin does not stop the library API update")
    void holdDelaysOnlyItsOwnProduct() {
        List<InstalledProduct> installed = list(installed(library, "1.0.0", range(1), 1), installed(market, "1.0.0", range(1)));
        List<ProductRelease> releases = list(release(library, "2.0.0", range(2), 2), release(market, "2.0.0", range(2)));

        ResolutionResult.Ready pluginHeld = (ResolutionResult.Ready) new UpdateResolver(library).resolve(installed, releases,
            Collections.emptyMap(), UpdateChannel.STABLE, Collections.singleton(market), null, Integer.MAX_VALUE,
            new ResolverPolicy());
        assertEquals(Collections.singleton(library), changed(pluginHeld));
        assertTrue(pluginHeld.getAdvisories().contains(new UnsupportedApi(market, range(1), 2)));

        ResolutionResult.Ready libraryHeld = (ResolutionResult.Ready) new UpdateResolver(library).resolve(installed, releases,
            Collections.emptyMap(), UpdateChannel.STABLE, Collections.singleton(library), null, Integer.MAX_VALUE,
            new ResolverPolicy());
        assertTrue(changed(libraryHeld).isEmpty());
        assertEquals(1, libraryHeld.getPlan().getTargetApi());
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

    @Test
    void pausedPluginDependenciesDoNotBlockALibraryUpgrade() {
        ProductDependency missingManaged = new ProductDependency(economy, version("2.0.0"));
        ExternalPluginDependency missingExternal = ExternalPluginDependency.builder("Vault", "1.7.3")
                .downloadPage("https://example.com/releases").build();
        List<InstalledProduct> installed = list(installed(library, "1.0.0", range(1), 1),
                installed(market, "1.0.0", range(1)));
        List<ProductRelease> releases = list(release(library, "2.0.0", range(1), 1),
                release(market, "2.0.0", range(1), null, UpdateChannel.STABLE, list(missingManaged),
                        list(missingExternal), Collections.emptyList()));
        UpdateResolver resolver = new UpdateResolver(library);

        assertTrue(resolver.resolve(installed, releases) instanceof ResolutionResult.Blocked);
        ResolutionResult.Ready ready = (ResolutionResult.Ready) resolver.resolve(installed, releases,
                Collections.emptyMap(), UpdateChannel.STABLE, Collections.singleton(market), PlatformType.BUKKIT, 17,
                new ResolverPolicy());

        assertEquals(Collections.singleton(library), changed(ready));
        assertEquals(version("1.0.0"), selected(ready.getPlan(), market).getVersion());
    }

    @Test
    void forcedPlanContainsOnlyTheNewestLibraryBuildForTheSelectedChannelAndPlatform() {
        ArtifactDescriptor bukkit = new ArtifactDescriptor("library.jar", PlatformType.BUKKIT, 8,
                null, 10L, repeat("00", 32), null);
        ArtifactDescriptor velocity = new ArtifactDescriptor("library.jar", PlatformType.VELOCITY, 17,
                null, 10L, repeat("00", 32), null);
        ArtifactDescriptor modernBukkit = new ArtifactDescriptor("library.jar", PlatformType.BUKKIT, 21,
                null, 10L, repeat("00", 32), null);
        List<ProductRelease> releases = list(
                release(library, "2.0.0", range(2), 2, UpdateChannel.STABLE,
                        list(new ProductDependency(economy, version("2.0.0"))), Collections.emptyList(), list(bukkit)),
                release(library, "3.0.0-dev.1", range(3), 3, UpdateChannel.DEV, Collections.emptyList(),
                        Collections.emptyList(), list(bukkit)),
                release(library, "4.0.0", range(3), 3, UpdateChannel.STABLE, Collections.emptyList(),
                        Collections.emptyList(), list(velocity)),
                release(library, "5.0.0", range(3), 3, UpdateChannel.STABLE, Collections.emptyList(),
                        Collections.emptyList(), list(modernBukkit)),
                release(market, "6.0.0", range(2)));
        InstalledProduct current = installed(library, "1.0.0", range(1), 1);
        UpdateResolver resolver = new UpdateResolver(library);

        UpdatePlan plan = resolver.forceLibrary(current, releases, UpdateChannel.STABLE, PlatformType.BUKKIT, 17);
        assertEquals(2, plan.getTargetApi());
        assertEquals(Collections.singletonList(new ProductChange(library, version("1.0.0"), version("2.0.0"))), plan.getChanges());
        assertEquals(1, plan.getSelected().size());
        assertThrows(IllegalStateException.class,
                () -> resolver.forceLibrary(current, releases, UpdateChannel.STABLE, PlatformType.BUNGEECORD, 17));
    }

    private static String repeat(String text, int times) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < times; i++) {
            builder.append(text);
        }
        return builder.toString();
    }
}
