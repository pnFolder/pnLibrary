package org.pnfolder.pnlibrary.core.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.platform.PlatformType;
import org.pnfolder.pnlibrary.api.plugin.ModuleContext;
import org.pnfolder.pnlibrary.api.plugin.lifecycle.LifecycleRow;

class LoadedPluginsLifecycleTest {
    @Test
    void nativeRegistrationResolvesMetadataAndLifecycleMessagesAreExplicit() {
        Object owner = new Object();
        TaskScopeRecorder taskScope = new TaskScopeRecorder(owner);
        LoggingRecorder logging = new LoggingRecorder();
        LoadedPlugins registry = PluginRegistryFixture.builder()
            .tasks(new TaskServiceRecorder(taskScope)).logging(logging).build();

        ModuleContext context = registry.register(owner).registerModule("example", plugin ->
            plugin.metadata(metadata -> metadata.name("Custom Example").version("2.0.0").authors("Library Team")));

        assertCustomMetadata(context);
        assertEquals(0, logging.startupMessages);

        org.pnfolder.pnlibrary.api.logging.MessageBox operationMessage = context.getMessages().box("Cache refresh")
            .ok("Loaded", "25 entries");
        assertEquals(0, logging.genericMessages);
        operationMessage.show();
        assertEquals(1, logging.genericMessages);

        context.getLifecycle().enabled().ok("Configuration", "loaded").show();

        assertEquals(1, logging.startupMessages);

        context.close();

        assertEquals(0, logging.shutdownMessages);

        context.getLifecycle().disabled().ok("Storage", "saved: 7").show();

        assertEquals(1, logging.shutdownMessages);
    }

    private static void assertCustomMetadata(ModuleContext context) {
        assertEquals("example", context.getId().getValue());
        assertEquals("Custom Example", context.getMetadata().getName());
        assertEquals("2.0.0", context.getMetadata().getVersion());
        assertEquals("Library Team", context.getMetadata().getAuthors());
        assertEquals(PlatformType.BUKKIT, context.getMetadata().getPlatform());
        assertEquals("Paper", context.getMetadata().getPlatformImplementation());
    }

    @Test
    void anEnabledCardCanLeaveOutTheRowsPnLibraryAddsByItself() {
        LoggingRecorder logging = new LoggingRecorder();
        ModuleContext context = PluginRegistryFixture.builder().logging(logging).build().register(new Object())
            .registerModule("example", it -> { });

        context.getLifecycle().enabled().ok("Mine", "x").show();
        assertEquals(Arrays.asList("Identifier", "Platform", "Metrics", "Updates", "Diagnostics", "PlaceholderAPI", "Mine"), logging.rows);

        logging.rows.clear();
        context.getLifecycle().enabled().hide(LifecycleRow.IDENTIFIER, LifecycleRow.PLATFORM).ok("Mine", "x").show();
        assertEquals(Arrays.asList("Metrics", "Updates", "Diagnostics", "PlaceholderAPI", "Mine"), logging.rows);

        logging.rows.clear();
        context.getLifecycle().enabled().hide(LifecycleRow.NOT_CONFIGURED).ok("Mine", "x").show();
        assertEquals(Arrays.asList("Identifier", "Platform", "PlaceholderAPI", "Mine"), logging.rows);
    }
}
