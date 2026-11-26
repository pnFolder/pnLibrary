package org.pnfolder.pnlibrary.updates.install;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pnfolder.pnlibrary.api.platform.PlatformType;
import org.pnfolder.pnlibrary.api.runtime.PnLibraryIdentity;
import org.pnfolder.pnlibrary.api.updates.product.ProductDescriptor;
import org.pnfolder.pnlibrary.api.updates.product.ProductId;
import org.pnfolder.pnlibrary.api.updates.product.UpdateChannel;
import org.pnfolder.pnlibrary.api.updates.request.CatalogSource;
import org.pnfolder.pnlibrary.api.updates.request.PluginUpdateRequest;
import org.pnfolder.pnlibrary.spi.platform.PlatformIdentity;
import org.pnfolder.pnlibrary.spi.platform.PlatformPluginInventory;
import org.pnfolder.pnlibrary.updates.engine.FreezeStore;
import org.pnfolder.pnlibrary.updates.engine.ResolutionResult;
import org.pnfolder.pnlibrary.updates.engine.UpdatePreferencesStore;
import org.pnfolder.pnlibrary.updates.engine.catalog.RefreshMode;
import org.pnfolder.pnlibrary.updates.engine.catalog.ReleaseCatalog;
import org.pnfolder.pnlibrary.updates.policy.UpdateConfiguration;
import org.pnfolder.pnlibrary.updates.policy.UpdatePolicyOverrides;
import org.pnfolder.pnlibrary.updates.service.RegisteredUpdate;

class UpdateGraphResolverTest {
    @TempDir
    Path directory;

    private final ReleaseCatalogGateway catalogs = mock(ReleaseCatalogGateway.class);
    private final PlatformPluginInventory inventory = mock(PlatformPluginInventory.class);
    private final PlatformIdentity identity = mock(PlatformIdentity.class);
    private final PluginUpdateRequest request = PluginUpdateRequest.builder()
        .repository("owner", "example")
        .supportedApi(1, 1)
        .exactArtifact("example.jar")
        .build();

    @BeforeEach
    void setUp() {
        when(identity.getType()).thenReturn(PlatformType.BUKKIT);
    }

    private RegisteredUpdate registration() {
        return registration(ProductDescriptor.builder().version("1.0.0").pnLibraryApi(1, 1).build().bindTo("example"));
    }

    private RegisteredUpdate registration(ProductDescriptor product) {
        return new RegisteredUpdate(product, request, request.getArtifacts().get(0), Paths.get("plugins/example.jar"),
            Paths.get("plugins/update"), () -> UpdateChannel.STABLE, () -> new CompletableFuture<>(),
            CompletableFuture::completedFuture, registration -> { }, () -> false);
    }

    private RegisteredUpdate library() {
        return registration(ProductDescriptor.library("1.0.0"));
    }

    private UpdateGraphResolver resolver() {
        return resolver(new UpdateConfiguration(), null);
    }

    private UpdateGraphResolver resolver(UpdateConfiguration configuration, UpdatePolicyOverrides overrides) {
        return new UpdateGraphResolver(inventory, identity, configuration, catalogs,
            new FreezeStore(new UpdatePreferencesStore(directory.resolve("preferences.json"))), overrides);
    }

    @Test
    @DisplayName("nothing registered resolves to an empty plan without loading any catalog")
    void nothingRegisteredResolvesToAnEmptyPlanWithoutLoadingAnyCatalog() {
        ResolutionResult result = resolver().resolve(Collections.<RegisteredUpdate>emptyList());

        assertTrue(result instanceof ResolutionResult.Ready);
        assertTrue(((ResolutionResult.Ready) result).getPlan().getChanges().isEmpty());
        verifyNoInteractions(catalogs);
    }

    @Test
    @DisplayName("a catalog for another product is rejected")
    void aCatalogForAnotherProductIsRejected() {
        when(catalogs.load(new CatalogSource("owner", "example"), RefreshMode.CACHED))
            .thenReturn(new ReleaseCatalog("someone-else", Collections.emptyList()));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> resolver().resolve(Collections.singletonList(registration())));

        assertTrue(error.getMessage().contains("someone-else"));
    }

    @Test
    @DisplayName("the requested refresh mode reaches the catalog gateway")
    void theRequestedRefreshModeReachesTheCatalogGateway() {
        when(catalogs.load(new CatalogSource("owner", "example"), RefreshMode.FORCE_REMOTE))
            .thenReturn(new ReleaseCatalog(PnLibraryIdentity.PRODUCT_ID, Collections.emptyList()));

        resolver().resolve(Collections.singletonList(library()), RefreshMode.FORCE_REMOTE);

        verify(catalogs).load(new CatalogSource("owner", "example"), RefreshMode.FORCE_REMOTE);
        verify(catalogs, never()).load(new CatalogSource("owner", "example"), RefreshMode.CACHED);
    }

    @Test
    @DisplayName("an unreadable plugin inventory does not stop resolution")
    void anUnreadablePluginInventoryDoesNotStopResolution() {
        when(inventory.installedPlugins()).thenThrow(new IllegalStateException("scan failed"));
        when(catalogs.load(new CatalogSource("owner", "example"), RefreshMode.CACHED))
            .thenReturn(new ReleaseCatalog(PnLibraryIdentity.PRODUCT_ID, Collections.emptyList()));

        ResolutionResult result = resolver().resolve(Collections.singletonList(library()));

        assertTrue(result instanceof ResolutionResult.Ready);
    }

    @Test
    @DisplayName("the library channel comes from configuration and a plugin keeps its requested channel")
    void theLibraryChannelComesFromConfigurationAndAPluginKeepsItsRequestedChannel() throws IOException {
        UpdateConfiguration configuration = new UpdateConfiguration()
            .withLibrary(new UpdateConfiguration.LibraryPolicy(UpdateChannel.DEV, true));
        UpdateGraphResolver resolver = resolver(configuration, null);

        assertEquals(UpdateChannel.DEV, resolver.channelFor(ProductId.of(PnLibraryIdentity.PRODUCT_ID), UpdateChannel.STABLE));
        assertEquals(UpdateChannel.STABLE, resolver.channelFor(ProductId.of("example"), UpdateChannel.STABLE));
        assertEquals(UpdateChannel.DEV, resolver.channelFor(library()));
    }
}
