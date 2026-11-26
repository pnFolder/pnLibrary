package org.pnfolder.pnlibrary.core.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.commands.CommandRegistration;
import org.pnfolder.pnlibrary.api.events.Event;
import org.pnfolder.pnlibrary.api.events.listener.EventHandler;
import org.pnfolder.pnlibrary.api.events.listener.Listener;
import org.pnfolder.pnlibrary.api.platform.PlatformType;
import org.pnfolder.pnlibrary.api.plugin.ModuleContext;
import org.pnfolder.pnlibrary.api.plugin.PluginRegistration;
import org.pnfolder.pnlibrary.api.tasks.TaskHandle;
import org.pnfolder.pnlibrary.api.tasks.spec.TaskSpec;
import org.pnfolder.pnlibrary.api.updates.product.ProductDescriptor;
import org.pnfolder.pnlibrary.core.events.EventDispatcher;
import org.pnfolder.pnlibrary.core.tasks.ScheduledTasks;
import org.pnfolder.pnlibrary.spi.platform.PlatformAdapter;
import org.pnfolder.pnlibrary.spi.tasks.PlatformTaskAdapter;
import org.pnfolder.pnlibrary.spi.tasks.PlatformTaskBuilder;

class LoadedPluginsModulesTest {
    private static PlatformAdapter platform(boolean accepts, String id) {
        Map<String, String> details = new HashMap<>();
        details.put("id", id);
        details.put("name", id);
        details.put("version", "1.0.0");
        return PluginRegistryFixture.proxy(PlatformAdapter.class, name -> {
            switch (name) {
                case "getType":
                    return PlatformType.BUKKIT;
                case "getImplementationName":
                    return "Paper";
                case "acceptsOwner":
                    return accepts;
                case "ownerDetails":
                    return details;
                case "installedPlugins":
                    return new HashMap<>();
                default:
                    return null;
            }
        });
    }

    @Test
    void contextIsGloballyAddressableByNormalizedPluginId() {
        Object owner = new Object();
        TaskScopeRecorder taskScope = new TaskScopeRecorder(owner);
        EventDispatcher events = new EventDispatcher((plugin, message, error) -> { });
        MetricsRecorder metrics = new MetricsRecorder();
        LoadedPlugins registry = PluginRegistryFixture.builder().events(events)
            .tasks(new TaskServiceRecorder(taskScope)).metrics(metrics).build();
        RecordingListener listener = new RecordingListener();

        PluginRegistration plugin = registry.register(owner);
        ModuleContext context = plugin.registerModule("pnClans", it -> it.metrics(builder -> builder.bStats(42).enabled(false)));
        context.getEvents().register(listener);

        assertSame(context, plugin.getModule("PNCLANS"));
        assertEquals("pnclans", context.getId().getValue());
        assertFalse(context.getMetrics().isEnabled());
        context.getMetrics().enable();
        assertTrue(context.getMetrics().isEnabled());
        assertEquals(42, metrics.lastProjectId);
        events.callEvent(new TestEvent());
        assertEquals(1, listener.calls);

        context.close();

        assertNull(plugin.getModule("pnclans"));
        assertTrue(context.isClosed());
        events.callEvent(new TestEvent());
        assertEquals(1, listener.calls);
        assertTrue(taskScope.closed);
    }

    @Test
    void duplicateModuleIdIsRejectedWithinOnePlugin() {
        Object owner = new Object();
        LoadedPlugins registry = PluginRegistryFixture.builder()
            .tasks(new TaskServiceRecorder(new TaskScopeRecorder(owner))).build();
        PluginRegistration plugin = registry.register(owner);
        plugin.registerModule("example", it -> { });

        assertThrows(IllegalArgumentException.class, () -> plugin.registerModule("EXAMPLE", it -> { }));
        registry.close();
    }

    @Test
    void oneNativeOwnerCanBeRegisteredUnderMultipleModuleIds() {
        Object owner = new Object();
        LoadedPlugins registry = PluginRegistryFixture.builder()
            .tasks(new TaskServiceRecorder(new TaskScopeRecorder(owner))).build();
        PluginRegistration plugin = registry.register(owner);
        ModuleContext first = plugin.registerModule("first", it -> { });
        ModuleContext second = plugin.registerModule("second", it -> { });

        assertEquals("first", first.getId().getValue());
        assertEquals("second", second.getId().getValue());
        assertEquals(1, registry.all().size());
        assertEquals(2, plugin.all().size());
        registry.close();
    }

    @Test
    void samePhysicalOwnerCannotBeRegisteredTwice() {
        Object owner = new Object();
        LoadedPlugins registry = PluginRegistryFixture.registry();
        registry.register(owner);

        assertThrows(IllegalArgumentException.class, () -> registry.register(owner));
    }

    @Test
    void differentOwnersMayUseTheSameLocalModuleId() {
        LoadedPlugins registry = PluginRegistryFixture.registry();

        ModuleContext first = registry.register(new Object()).registerModule("core", it -> { });
        ModuleContext second = registry.register(new Object()).registerModule("core", it -> { });

        assertEquals("core", first.getId().getValue());
        assertEquals("core", second.getId().getValue());
        assertEquals(2, registry.all().size());
    }

    @Test
    void componentIdentityRemainsUniqueAcrossDifferentPluginContexts() {
        LoadedPlugins registry = PluginRegistryFixture.registry();
        ProductDescriptor descriptor = ProductDescriptor.builder().version("1.0.0").pnLibraryApi(1, 1).build();
        registry.register(new Object()).registerModule("shared-component", it -> it.product(descriptor));

        assertThrows(IllegalArgumentException.class,
            () -> registry.register(new Object()).registerModule("shared-component", it -> it.product(descriptor)));
    }

    @Test
    void maximumLengthOwnerAndModuleIdsProduceAValidServiceNamespace() {
        String longId = repeat('a');
        LoadedPlugins registry = PluginRegistryFixture.builder().platform(platform(true, longId)).build();

        ModuleContext module = registry.register(new Object()).registerModule(repeat('b'), it -> { });

        assertEquals(repeat('b'), module.getId().getValue());
    }

    @Test
    void closingOneModuleKeepsItsSiblingLive() {
        PluginRegistration plugin = PluginRegistryFixture.registry().register(new Object());
        ModuleContext first = plugin.registerModule("first", it -> { });
        ModuleContext second = plugin.registerModule("second", it -> { });

        first.close();

        assertTrue(first.isClosed());
        assertFalse(second.isClosed());
        assertNull(plugin.getModule("first"));
        assertSame(second, plugin.getModule("second"));
    }

    @Test
    void siblingModulesReceiveIsolatedConfigurationScopes() {
        PluginRegistration plugin = PluginRegistryFixture.registry().register(new Object());
        ModuleContext first = plugin.registerModule("first", it -> { });
        ModuleContext second = plugin.registerModule("second", it -> { });

        assertNotSame(first.getConfigs(), second.getConfigs());
        first.close();
        assertEquals(0, second.getConfigs().getSize());
    }

    @Test
    void siblingModulesReceiveIsolatedTaskScopes() {
        PlatformTaskAdapter nativeTasks = () -> new PlatformTaskBuilder(request -> () -> true);
        ScheduledTasks tasks = new ScheduledTasks(nativeTasks);
        PluginRegistration plugin = PluginRegistryFixture.builder().tasks(tasks).build().register(new Object());
        ModuleContext first = plugin.registerModule("first", it -> { });
        ModuleContext second = plugin.registerModule("second", it -> { });

        assertNotSame(first.getTasks(), second.getTasks());
        TaskSpec spec = TaskSpec.builder().action(context -> { }).build();
        TaskHandle firstTask = first.getTasks().schedule(spec);
        TaskHandle secondTask = second.getTasks().schedule(spec);
        first.close();
        assertTrue(firstTask.isCancelled());
        assertFalse(secondTask.isCancelled());
        assertThrows(IllegalStateException.class, () -> first.getTasks().schedule(spec));
        plugin.close();
        assertTrue(secondTask.isCancelled());
        tasks.close();
    }

    @Test
    void moduleCommandsUseNativePluginAndCloseIndependently() {
        Object owner = new Object();
        CommandRecorder commands = new CommandRecorder();
        PluginRegistration plugin = PluginRegistryFixture.builder().commands(commands).build().register(owner);
        ModuleContext first = plugin.registerModule("first", it -> { });
        ModuleContext second = plugin.registerModule("second", it -> { });
        CommandRegistration firstCommand = first.registerCommand("first", it -> { });
        CommandRegistration secondCommand = second.registerCommand("second", it -> { });

        assertSame(owner, commands.registeredOwners.get(0));
        assertSame(owner, commands.registeredOwners.get(1));
        first.close();
        assertTrue(firstCommand.isClosed());
        assertFalse(secondCommand.isClosed());
        assertThrows(IllegalStateException.class, () -> first.registerCommand("late", it -> { }));
        plugin.close();
        assertTrue(secondCommand.isClosed());
    }

    @Test
    void pluginContextRegistersAndClosesMultipleLogicalModules() {
        LoadedPlugins registry = PluginRegistryFixture.registry();
        PluginRegistration modules = registry.register(new Object());

        ModuleContext core = modules.registerModule("example-core", it -> { });
        ModuleContext economy = modules.registerModule("example-economy", it -> { });

        Set<String> ids = new HashSet<>();
        for (ModuleContext module : modules.all()) {
            ids.add(module.getId().getValue());
        }
        assertEquals(new HashSet<>(Arrays.asList("example-core", "example-economy")), ids);
        modules.close();
        assertTrue(core.isClosed());
        assertTrue(economy.isClosed());
        assertTrue(registry.all().isEmpty());
    }

    @Test
    void unsupportedOwnerIsRejectedBeforeModuleContextIsCreated() {
        LoadedPlugins registry = PluginRegistryFixture.builder().platform(platform(false, "example")).build();

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> registry.register(new Object()));

        assertTrue(error.getMessage().contains("not a supported owner"));
        assertTrue(registry.all().isEmpty());
    }

    @Test
    void closingPhysicalPluginContextUnregistersCommandsOwnedByItsNativePlugin() {
        Object owner = new Object();
        CommandRecorder commands = new CommandRecorder();
        PluginRegistration plugin = PluginRegistryFixture.builder().commands(commands).build().register(owner);
        plugin.registerModule("example", it -> { });

        plugin.close();

        assertEquals(Arrays.asList(owner), commands.unregisteredOwners);
    }

    @Test
    void platformOwnerCleanupRemovesItsGlobalContext() {
        Object owner = new Object();
        TaskScopeRecorder taskScope = new TaskScopeRecorder(owner);
        LoadedPlugins registry = PluginRegistryFixture.builder().tasks(new TaskServiceRecorder(taskScope)).build();
        ModuleContext context = registry.register(owner).registerModule("example", it -> { });

        registry.unregister(owner);

        assertTrue(context.isClosed());
        assertNull(registry.get(owner));
        assertTrue(taskScope.closed);
    }

    private static String repeat(char letter) {
        char[] text = new char[64];
        Arrays.fill(text, letter);
        return new String(text);
    }

    /** Counts the events it handled. */
    public static final class RecordingListener implements Listener {
        private int calls;

        @EventHandler
        public void handle(TestEvent event) {
            calls++;
        }
    }

    /** An event with nothing in it. */
    public static final class TestEvent extends Event {
    }
}
