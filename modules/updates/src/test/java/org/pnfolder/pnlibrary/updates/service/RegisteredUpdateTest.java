package org.pnfolder.pnlibrary.updates.service;

import org.pnfolder.pnlibrary.updates.service.registration.RegisteredUpdate;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.net.URI;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.platform.PlatformType;
import org.pnfolder.pnlibrary.api.updates.product.ProductDescriptor;
import org.pnfolder.pnlibrary.api.updates.product.UpdateChannel;
import org.pnfolder.pnlibrary.api.updates.release.ArtifactDescriptor;
import org.pnfolder.pnlibrary.api.updates.release.ProductRelease;
import org.pnfolder.pnlibrary.api.updates.request.PluginUpdateRequest;
import org.pnfolder.pnlibrary.api.updates.state.UpdatePlanSnapshot;
import org.pnfolder.pnlibrary.api.updates.state.UpdateSnapshot;
import org.pnfolder.pnlibrary.api.updates.state.UpdateState;
import org.pnfolder.pnlibrary.api.version.ApiVersionRange;
import org.pnfolder.pnlibrary.api.version.SemanticVersion;
import org.pnfolder.pnlibrary.updates.engine.catalog.CatalogArtifact;
import org.pnfolder.pnlibrary.updates.engine.catalog.CatalogRelease;

class RegisteredUpdateTest {
    private final PluginUpdateRequest request = PluginUpdateRequest.builder()
        .repository("owner", "example")
        .supportedApi(1, 1)
        .exactArtifact("example.jar")
        .build();
    private final ProductDescriptor descriptor = ProductDescriptor.builder().version("1.0.0").pnLibraryApi(1, 1).build()
        .bindTo("example");

    private UpdateChannel channel = UpdateChannel.STABLE;
    private boolean automatic;
    private int closedWith;
    private final List<CompletableFuture<UpdatePlanSnapshot>> checks = new ArrayList<>();
    private final List<UpdatePlanSnapshot> staged = new ArrayList<>();

    private RegisteredUpdate registration() {
        return registration(descriptor);
    }

    private RegisteredUpdate registration(ProductDescriptor product) {
        return new RegisteredUpdate(product, request, request.getArtifacts().get(0), Paths.get("plugins/example.jar"),
            Paths.get("plugins/update"), () -> channel,
            () -> {
                CompletableFuture<UpdatePlanSnapshot> check = new CompletableFuture<>();
                checks.add(check);
                return check;
            },
            plan -> {
                staged.add(plan);
                return CompletableFuture.completedFuture(plan);
            },
            closed -> closedWith++, () -> automatic);
    }

    private ProductRelease release(String version) {
        return release(version, UpdateChannel.STABLE);
    }

    private ProductRelease release(String version, UpdateChannel releaseChannel) {
        return new ProductRelease(descriptor.getId(), SemanticVersion.parse(version), releaseChannel,
            new ApiVersionRange(1, 1), null, Collections.emptyList(), "owner/example", Collections.emptyList(),
            Collections.emptyList(), null, null, "");
    }

    private CatalogRelease catalogRelease(ApiVersionRange api, List<CatalogArtifact> artifacts) {
        return new CatalogRelease(SemanticVersion.parse("1.3.0"), UpdateChannel.STABLE, "Fixes",
            URI.create("https://example.com/changes"), Instant.parse("2026-10-01T00:00:00Z"), api, artifacts);
    }

    @Test
    @DisplayName("a new registration reports that it is checking")
    void aNewRegistrationReportsThatItIsChecking() {
        RegisteredUpdate registration = registration();
        UpdateSnapshot snapshot = registration.getSnapshot();

        assertEquals(UpdateState.CHECKING, snapshot.getState());
        assertEquals("1.0.0", snapshot.getCurrentVersion());
        assertNull(snapshot.getLatestVersion());
        assertEquals("owner/example", registration.getRepository());
    }

    @Test
    @DisplayName("observing a newer release reports an available update")
    void observingANewerReleaseReportsAnAvailableUpdate() {
        RegisteredUpdate registration = registration();

        registration.observe(Arrays.asList(release("1.0.0"), release("1.2.0")), UpdateChannel.STABLE);

        UpdateSnapshot snapshot = registration.getSnapshot();
        assertEquals(UpdateState.UPDATE_AVAILABLE, snapshot.getState());
        assertEquals("1.2.0", snapshot.getLatestVersion());
        assertEquals("1.2.0", snapshot.getAvailableReleases().get(0).getVersion());
        assertEquals("1.0.0", snapshot.getAvailableReleases().get(1).getVersion());
        assertEquals(2, snapshot.getAvailableReleases().size());
        assertEquals("https://github.com/owner/example/releases", snapshot.getReleaseUrl());
    }

    @Test
    @DisplayName("the installed version or an older one is up to date")
    void theInstalledVersionOrAnOlderOneIsUpToDate() {
        RegisteredUpdate registration = registration();

        registration.observe(Arrays.asList(release("1.0.0"), release("0.9.0")), UpdateChannel.STABLE);

        assertEquals(UpdateState.UP_TO_DATE, registration.getSnapshot().getState());
    }

    @Test
    @DisplayName("a release outside the chosen channel is not offered")
    void aReleaseOutsideTheChosenChannelIsNotOffered() {
        RegisteredUpdate registration = registration();

        registration.observe(Collections.singletonList(release("2.0.0-dev.1", UpdateChannel.DEV)), UpdateChannel.STABLE);

        assertEquals(UpdateState.UP_TO_DATE, registration.getSnapshot().getState());
        assertNull(registration.getSnapshot().getLatestVersion());
    }

    @Test
    @DisplayName("a closed registration ignores observations and refuses checks")
    void aClosedRegistrationIgnoresObservationsAndRefusesChecks() {
        RegisteredUpdate registration = registration();
        registration.close();

        registration.observe(Collections.singletonList(release("9.0.0")), UpdateChannel.STABLE);

        assertEquals(UpdateState.CHECKING, registration.getSnapshot().getState());
        assertTrue(registration.isClosed());
        assertThrows(IllegalStateException.class, registration::checkNow);
        assertThrows(IllegalStateException.class, registration::downloadNow);
    }

    @Test
    @DisplayName("closing notifies the owner only once")
    void closingNotifiesTheOwnerOnlyOnce() {
        RegisteredUpdate registration = registration();

        registration.close();
        registration.close();

        assertEquals(1, closedWith);
    }

    @Test
    @DisplayName("refreshing the policy picks up a changed channel and automatic download")
    void refreshingThePolicyPicksUpAChangedChannelAndAutomaticDownload() {
        RegisteredUpdate registration = registration();
        registration.observe(Collections.singletonList(release("1.1.0")), UpdateChannel.STABLE);
        channel = UpdateChannel.DEV;
        automatic = true;

        registration.refreshPolicy();

        UpdateSnapshot snapshot = registration.getSnapshot();
        assertEquals(UpdateChannel.DEV, snapshot.getChannel());
        assertTrue(snapshot.getAutomaticDownload());
        assertEquals("1.1.0", snapshot.getLatestVersion());
        assertEquals(UpdateState.UPDATE_AVAILABLE, snapshot.getState());
    }

    @Test
    @DisplayName("downloading stages the plan only after the check completes")
    void downloadingStagesThePlanOnlyAfterTheCheckCompletes() {
        RegisteredUpdate registration = registration();
        UpdatePlanSnapshot plan = mock(UpdatePlanSnapshot.class);

        registration.downloadNow();
        assertTrue(staged.isEmpty());

        checks.get(0).complete(plan);

        assertEquals(Collections.singletonList(plan), staged);
    }

    @Test
    @DisplayName("a catalog release maps to a product release with its artifacts")
    void aCatalogReleaseMapsToAProductReleaseWithItsArtifacts() {
        CatalogArtifact artifact = new CatalogArtifact("example-1.3.0.jar", PlatformType.BUKKIT, null, null, 17, null,
            URI.create("https://example.com/example-1.3.0.jar"), repeat('a', 64), 10L);

        ProductRelease mapped = registration().releaseFrom(catalogRelease(new ApiVersionRange(1, 1),
            Collections.singletonList(artifact)));

        assertEquals(descriptor.getId(), mapped.getProduct());
        assertEquals("owner/example", mapped.getRepository());
        assertNull(mapped.getProvidesApi());
        assertEquals("https://example.com/changes", mapped.getChangelogUrl());
        ArtifactDescriptor mappedArtifact = mapped.getArtifacts().get(0);
        assertEquals("example-1.3.0.jar", mappedArtifact.getFile());
        assertEquals(17, mappedArtifact.getMinimumJava());
        assertEquals(Long.valueOf(10L), mappedArtifact.getSize());
    }

    @Test
    @DisplayName("only the library product provides the API it was released with")
    void onlyTheLibraryProductProvidesTheApiItWasReleasedWith() {
        CatalogRelease catalog = catalogRelease(new ApiVersionRange(1, 2), Collections.emptyList());

        assertEquals(Integer.valueOf(2), registration(ProductDescriptor.library("1.0.0")).releaseFrom(catalog).getProvidesApi());
        assertNull(registration().releaseFrom(catalog).getProvidesApi());
    }

    private static String repeat(char symbol, int times) {
        char[] chars = new char[times];
        Arrays.fill(chars, symbol);
        return new String(chars);
    }
}
