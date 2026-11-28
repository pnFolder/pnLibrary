package org.pnfolder.pnlibrary.core.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.pnfolder.pnlibrary.core.plugin.PluginRegistryFixture.registerModule;
import static org.pnfolder.pnlibrary.core.plugin.PluginRegistryFixture.registry;

import java.nio.file.Paths;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.dependencies.Dependencies;
import org.pnfolder.pnlibrary.api.dependencies.external.ExternalPluginDependency;
import org.pnfolder.pnlibrary.api.downloads.DownloadRegistration;
import org.pnfolder.pnlibrary.api.downloads.FileDownloads;
import org.pnfolder.pnlibrary.api.downloads.source.DownloadDestination;
import org.pnfolder.pnlibrary.api.platform.PlatformType;
import org.pnfolder.pnlibrary.api.plugin.ModuleContext;
import org.pnfolder.pnlibrary.api.updates.product.ProductDescriptor;
import org.pnfolder.pnlibrary.api.updates.request.PluginUpdateRequest;
import org.pnfolder.pnlibrary.core.commands.engine.CommandService;
import org.pnfolder.pnlibrary.core.downloads.DownloadService;

class LoadedPluginsDependenciesTest {
    private static ProductDescriptor product(String version, int api) {
        return ProductDescriptor.builder().version(version).pnLibraryApi(api, api).build();
    }

    @Test
    void failedFileRegistrationRollsBackPreviouslyRegisteredDependencyDownloads() {
        Object owner = new Object();
        DownloadService downloads = mock(DownloadService.class);
        DownloadRegistration dependencyRegistration = mock(DownloadRegistration.class);
        IllegalStateException failure = new IllegalStateException("file registration failed");
        FileDownloads request = FileDownloads.builder().dataDirectory(Paths.get("plugins/example"))
            .file("asset", it -> it.url("https://example.org/asset.bin").destination(DownloadDestination.DATA_FOLDER, "asset.bin"))
            .build();
        when(downloads.registerDependencies(org.mockito.ArgumentMatchers.eq(owner), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(Collections.emptyList()))).thenReturn(dependencyRegistration);
        when(downloads.register(owner, request)).thenThrow(failure);
        LoadedPlugins registry = PluginRegistryFixture.builder().downloadService(downloads).build();

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
            () -> registry.register(owner).registerModule("example", it -> it.downloads(request)));

        assertSame(failure, thrown);
        verify(dependencyRegistration).close();
        registry.close();
    }

    @Test
    void registryShutdownClosesRemainingPluginsAndDownloadsAfterAPluginFails() {
        Object firstOwner = new Object();
        Object secondOwner = new Object();
        CommandService commands = mock(CommandService.class);
        DownloadService downloads = mock(DownloadService.class);
        IllegalStateException failure = new IllegalStateException("plugin cleanup failed");
        doThrow(failure).when(commands).unregisterOwner(firstOwner);
        LoadedPlugins registry = PluginRegistryFixture.builder().commands(commands).downloadService(downloads).build();
        registry.register(firstOwner);
        registry.register(secondOwner);

        assertSame(failure, assertThrows(IllegalStateException.class, registry::close));

        verify(commands).unregisterOwner(firstOwner);
        verify(commands).unregisterOwner(secondOwner);
        verify(downloads).close();
        assertTrue(registry.all().isEmpty());
    }

    @Test
    void dependsCreatesAnImplicitComponentDescriptorWhenNoUpdateDeclarationExists() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> registerModule(PluginRegistryFixture.builder().libraryVersion("1.0.0").build(), new Object(), "example",
                it -> it.depends(Dependencies.managed("missing-component", "9.0.0", "pnFolder", "Missing"))));
        assertTrue(error.getMessage().contains("missing-component >= 9.0.0"));
    }

    @Test
    void optionalUnifiedDependencyDoesNotBlockRegistration() {
        ModuleContext context = registerModule(PluginRegistryFixture.builder().libraryVersion("1.0.0").build(), new Object(), "example",
            it -> it.depends(Dependencies.managed("missing-component", "9.0.0", "pnFolder", "Missing", false))
                .product(product("1.0.0", 1)));
        assertEquals("example", context.getId().getValue());
        context.close();
    }

    @Test
    void updatesDeclarationInfersComponentWithoutSeparateDescriptor() {
        PluginUpdateRequest request = PluginUpdateRequest.builder().repository("pnFolder", "Example")
            .supportedApi(1, 2).artifact("Example.jar", PlatformType.BUKKIT, 17).build();

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> registerModule(registry(), new Object(), "example",
                it -> it.updates(request).depends(Dependencies.managed("economy", "2.0.0", "pnFolder", "Economy"))));

        assertTrue(error.getMessage().contains("economy >= 2.0.0"));
    }

    @Test
    void ordinaryFileDeliveryDoesNotSatisfyMissingManagedDependencyGate() {
        PluginUpdateRequest request = PluginUpdateRequest.builder().repository("pnFolder", "Example")
            .supportedApi(1, 1).artifact("Example.jar", 17).build();
        FileDownloads downloads = FileDownloads.builder().dataDirectory(Paths.get("plugins/example"))
            .file("economy-data", it -> it.url("https://example.org/data.bin").destination(DownloadDestination.DATA_FOLDER, "data.bin"))
            .build();

        assertThrows(IllegalArgumentException.class, () -> registerModule(registry(), new Object(), "example",
            it -> it.updates(request).depends(Dependencies.managed("economy", "2.0.0", "pnFolder", "Economy")).downloads(downloads)));
    }

    @Test
    void updatesRegistrationInheritsComponentMetadata() {
        UpdateRecorder updates = new UpdateRecorder();
        ProductDescriptor descriptor = ProductDescriptor.builder().version("1.0.0").pnLibraryApi(1, 2).build();
        PluginUpdateRequest request = PluginUpdateRequest.builder().repository("pnFolder", "Example")
            .artifact("(?i)^Example-.*\\.jar$", 17).build();

        registerModule(PluginRegistryFixture.builder().updates(updates).libraryVersion("1.5.0").build(), new Object(), "example",
            it -> it.product(descriptor).depends(Dependencies.managed("pnlibrary", "1.5.0", "pnFolder", "pnLibrary")).updates(request));

        assertEquals("example", updates.product.getId().getValue());
        assertEquals(1, updates.request.getSupportedApi().getMinimum());
        assertEquals(2, updates.request.getSupportedApi().getMaximum());
        assertEquals(1, updates.dependencies.size());
        assertEquals("pnlibrary", updates.dependencies.get(0).getManaged().getProduct().getValue());
        assertEquals("1.5.0", updates.dependencies.get(0).getVersions().getMinimum().toString());
    }

    @Test
    void dependencyGateRunsBeforePluginScopesBecomeVisible() {
        Object owner = new Object();
        TaskScopeRecorder taskScope = new TaskScopeRecorder(owner);
        LoadedPlugins registry = PluginRegistryFixture.builder().tasks(new TaskServiceRecorder(taskScope))
            .libraryVersion("1.0.0").build();

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> registerModule(registry, owner, "example",
            it -> it.product(product("1.0.0", 1)).depends(Dependencies.managed("economy", "2.0.0", "pnFolder", "Economy"))));

        assertTrue(error.getMessage().contains("economy >= 2.0.0"));
        assertFalse(taskScope.closed);
        assertTrue(registry.all().get(0).all().isEmpty());
    }

    @Test
    void registeredCompatibleComponentSatisfiesALaterDependency() {
        LoadedPlugins registry = PluginRegistryFixture.builder().libraryVersion("1.0.0").build();
        registerModule(registry, new Object(), "economy", it -> it.product(product("2.1.0", 1)));

        ModuleContext dependent = registerModule(registry, new Object(), "example",
            it -> it.product(product("1.0.0", 1)).depends(Dependencies.managed("economy", "2.0.0", "pnFolder", "Economy")));

        assertEquals("example", dependent.getId().getValue());
        registry.close();
    }

    @Test
    void externalDependencyDiagnosticIncludesItsDownloadPage() {
        ExternalPluginDependency dependency = ExternalPluginDependency.builder("Vault", "1.7.3")
            .downloadPage("https://github.com/MilkBowl/Vault/releases").build();
        ProductDescriptor descriptor = product("1.0.0", 1);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> registerModule(registry(), new Object(), "example", it -> it.product(descriptor).depends(dependency)));

        assertTrue(error.getMessage().contains("Vault >= 1.7.3"));
        assertTrue(error.getMessage().contains("https://github.com/MilkBowl/Vault/releases"));
    }
}
