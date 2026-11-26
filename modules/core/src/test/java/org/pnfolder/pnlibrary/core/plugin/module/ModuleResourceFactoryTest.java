package org.pnfolder.pnlibrary.core.plugin.module;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.pnfolder.pnlibrary.api.config.ConfigScope;
import org.pnfolder.pnlibrary.api.currency.CurrencyService;
import org.pnfolder.pnlibrary.api.currency.CurrencyStorageFactory;
import org.pnfolder.pnlibrary.api.diagnostics.DiagnosticsService;
import org.pnfolder.pnlibrary.api.events.bus.EventScope;
import org.pnfolder.pnlibrary.api.events.bus.EventService;
import org.pnfolder.pnlibrary.api.logging.LoggingService;
import org.pnfolder.pnlibrary.api.logging.PnLogger;
import org.pnfolder.pnlibrary.api.metrics.MetricsService;
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderService;
import org.pnfolder.pnlibrary.api.plugin.ModuleId;
import org.pnfolder.pnlibrary.api.plugin.PluginId;
import org.pnfolder.pnlibrary.api.tasks.TaskScope;
import org.pnfolder.pnlibrary.api.updates.UpdateService;
import org.pnfolder.pnlibrary.config.ConfigurationRuntime;
import org.pnfolder.pnlibrary.core.placeholders.PlaceholderEngine;
import org.pnfolder.pnlibrary.core.services.ServiceRegistry;
import org.pnfolder.pnlibrary.core.services.ServiceScope;
import org.pnfolder.pnlibrary.core.tasks.TaskService;
import org.pnfolder.pnlibrary.core.text.ComponentCache;
import org.pnfolder.pnlibrary.currency.CurrencyRuntime;

/** Verifies module ownership and rollback using substitute implementations of runtime contracts. */
class ModuleResourceFactoryTest {
    private final Object owner = new Object();
    private final ModuleId id = ModuleId.of("shop");
    private final PluginId key = PluginId.of("example-shop");
    private final EventService events = mock(EventService.class);
    private final TaskService tasks = mock(TaskService.class);
    private final ConfigurationRuntime configurations = mock(ConfigurationRuntime.class);
    private final PlaceholderEngine placeholders = mock(PlaceholderEngine.class);
    private final CurrencyRuntime currency = mock(CurrencyRuntime.class);
    private final LoggingService logging = mock(LoggingService.class);
    private final DiagnosticsService diagnostics = mock(DiagnosticsService.class);
    private final EventScope eventScope = mock(EventScope.class);
    private final TaskScope taskScope = mock(TaskScope.class);
    private final ConfigScope configScope = mock(ConfigScope.class);
    private final PlaceholderService placeholderScope = mock(PlaceholderService.class);
    private final CurrencyService currencyScope = mock(CurrencyService.class);
    private final ServiceScope serviceScope = mock(ServiceScope.class);
    private final ServiceRegistry serviceManager = mock(ServiceRegistry.class);
    private final CurrencyStorageFactory storageFactory = mock(CurrencyStorageFactory.class);
    private final PnLogger logger = mock(PnLogger.class);

    private ModuleResourceFactory factory() {
        when(events.scope(key, PluginId.of("native"), id)).thenReturn(eventScope);
        when(tasks.scope(owner, key)).thenReturn(taskScope);
        when(configurations.scope(owner, key, PluginId.of("native"), id)).thenReturn(configScope);
        when(placeholders.scope(anyString(), anyBoolean())).thenReturn(placeholderScope);
        when(currency.scope(key, placeholderScope)).thenReturn(currencyScope);
        when(currency.getStorages()).thenReturn(storageFactory);
        when(logging.logger(owner, id.getValue())).thenReturn(logger);
        when(serviceManager.ownedBy(key)).thenReturn(serviceScope);
        return new ModuleResourceFactory(events, tasks, serviceManager, logging, mock(MetricsService.class), diagnostics,
            mock(UpdateService.class), placeholders, currency, configurations, null, new ComponentCache());
    }

    private ModuleResources create(ModuleResourceFactory factory) {
        ModuleDefinitionBuilder definition = new ModuleDefinitionBuilder();
        definition.options(options -> options.placeholderApi(false));
        return factory.create(owner, id, key, PluginId.of("native"), definition, null);
    }

    @Test
    void substituteRuntimesSupplyModuleScopesAndRegistrationsThatEndWithTheModule() {
        ModuleResources resources = create(factory());
        assertSame(taskScope, resources.getTasks());
        assertSame(configScope, resources.getConfigs());
        assertSame(placeholderScope, resources.getPlaceholders());
        assertSame(currencyScope, resources.getCurrency());
        assertSame(serviceScope, resources.getServices());
        assertSame(logger, resources.getLogger());
        verify(serviceScope).register(CurrencyService.class, currencyScope);
        verify(serviceScope).register(CurrencyStorageFactory.class, storageFactory);

        resources.close(diagnostics);
        verify(currencyScope).close();
        verify(placeholderScope).close();
        verify(configScope).close();
        verify(serviceScope).close();
        verify(eventScope).close();
        verify(taskScope).close();
        verify(diagnostics).clearPlugin("native/shop");
    }

    @Test
    void lateCreationFailureClosesAllAcquiredScopesDespiteACleanupFailure() {
        ModuleResourceFactory factory = factory();
        IllegalStateException failure = new IllegalStateException("Logger is unavailable");
        IllegalStateException cleanupFailure = new IllegalStateException("Service cleanup failed");
        when(logging.logger(owner, id.getValue())).thenThrow(failure);
        doThrow(cleanupFailure).when(serviceScope).close();

        assertSame(failure, assertThrows(IllegalStateException.class, () -> create(factory)));
        assertTrue(Arrays.asList(failure.getSuppressed()).contains(cleanupFailure));
        InOrder order = inOrder(currencyScope, placeholderScope, configScope, serviceScope, eventScope, taskScope);
        order.verify(currencyScope).close();
        order.verify(placeholderScope).close();
        order.verify(configScope).close();
        order.verify(eventScope).close();
        order.verify(serviceScope).close();
        order.verify(taskScope).close();
    }

    @Test
    void partialCreationFailureClosesAcquiredScopesWithoutClosingAnUnacquiredService() {
        ModuleResourceFactory factory = factory();
        IllegalStateException failure = new IllegalStateException("Currency is unavailable");
        when(currency.scope(key, placeholderScope)).thenThrow(failure);

        assertSame(failure, assertThrows(IllegalStateException.class, () -> create(factory)));
        verify(currencyScope, never()).close();
        verify(placeholderScope).close();
        verify(configScope).close();
        verify(serviceScope).close();
        verify(eventScope).close();
        verify(taskScope).close();
    }
}
