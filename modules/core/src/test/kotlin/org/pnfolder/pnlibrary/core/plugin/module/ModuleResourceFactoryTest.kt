package org.pnfolder.pnlibrary.core.plugin.module

import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.pnfolder.pnlibrary.api.config.ConfigScope
import org.pnfolder.pnlibrary.api.currency.CurrencyService
import org.pnfolder.pnlibrary.api.currency.CurrencyStorageFactory
import org.pnfolder.pnlibrary.api.diagnostics.DiagnosticsService
import org.pnfolder.pnlibrary.api.events.EventScope
import org.pnfolder.pnlibrary.api.events.EventService
import org.pnfolder.pnlibrary.api.logging.LoggingService
import org.pnfolder.pnlibrary.api.logging.PnLogger
import org.pnfolder.pnlibrary.api.metrics.MetricsService
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderService
import org.pnfolder.pnlibrary.api.plugin.ModuleId
import org.pnfolder.pnlibrary.api.plugin.PluginId
import org.pnfolder.pnlibrary.api.tasks.TaskScope
import org.pnfolder.pnlibrary.api.updates.UpdateService
import org.pnfolder.pnlibrary.config.ConfigurationRuntime
import org.pnfolder.pnlibrary.core.placeholders.PlaceholderHub
import org.pnfolder.pnlibrary.core.services.ServiceManagerImpl
import org.pnfolder.pnlibrary.core.services.ServiceScope
import org.pnfolder.pnlibrary.core.tasks.TaskService
import org.pnfolder.pnlibrary.core.text.ComponentCache
import org.pnfolder.pnlibrary.currency.CurrencyRuntime

/** Verifies module ownership and rollback using substitute implementations of runtime contracts. */
class ModuleResourceFactoryTest {
    private val owner = Any()
    private val id = ModuleId.of("shop")
    private val key = PluginId.of("example-shop")
    private val events = mock(EventService::class.java)
    private val tasks = mock(TaskService::class.java)
    private val configurations = mock(ConfigurationRuntime::class.java)
    private val placeholders = mock(PlaceholderHub::class.java)
    private val currency = mock(CurrencyRuntime::class.java)
    private val logging = mock(LoggingService::class.java)
    private val diagnostics = mock(DiagnosticsService::class.java)
    private val eventScope = mock(EventScope::class.java)
    private val taskScope = mock(TaskScope::class.java)
    private val configScope = mock(ConfigScope::class.java)
    private val placeholderScope = mock(PlaceholderService::class.java)
    private val currencyScope = mock(CurrencyService::class.java)
    private val serviceScope = mock(ServiceScope::class.java)
    private val serviceManager = mock(ServiceManagerImpl::class.java)
    private val storageFactory = mock(CurrencyStorageFactory::class.java)
    private val logger = mock(PnLogger::class.java)

    private fun factory(): ModuleResourceFactory {
        `when`(events.scope(key, PluginId.of("native"), id)).thenReturn(eventScope)
        `when`(tasks.scope(owner, key)).thenReturn(taskScope)
        `when`(configurations.scope(owner, key)).thenReturn(configScope)
        `when`(placeholders.scope(key, false)).thenReturn(placeholderScope)
        `when`(currency.scope(key, placeholderScope)).thenReturn(currencyScope)
        `when`(currency.storages).thenReturn(storageFactory)
        `when`(logging.logger(owner, id.value)).thenReturn(logger)
        `when`(serviceManager.ownedBy(key)).thenReturn(serviceScope)
        return ModuleResourceFactory(
            events = events,
            tasks = tasks,
            services = serviceManager,
            logging = logging,
            metrics = mock(MetricsService::class.java),
            diagnostics = diagnostics,
            updates = mock(UpdateService::class.java),
            placeholders = placeholders,
            currency = currency,
            configurations = configurations,
            directDownloads = null,
            componentCache = ComponentCache(),
        )
    }

    private fun ModuleResourceFactory.create() =
        create(owner, id, key, PluginId.of("native"), ModuleDefinitionBuilder().apply { placeholderApiEnabled = false }, product = null)

    @Test
    fun `substitute runtimes supply module scopes and registrations that end with the module`() {
        val resources = factory().create()
        assertSame(taskScope, resources.tasks)
        assertSame(configScope, resources.configs)
        assertSame(placeholderScope, resources.placeholders)
        assertSame(currencyScope, resources.currency)
        assertSame(serviceScope, resources.services)
        assertSame(logger, resources.logger)
        verify(serviceScope).register(CurrencyService::class.java, currencyScope)
        verify(serviceScope).register(CurrencyStorageFactory::class.java, storageFactory)

        resources.close(diagnostics, key)
        verify(currencyScope).close()
        verify(placeholderScope).close()
        verify(configScope).close()
        verify(serviceScope).close()
        verify(eventScope).close()
        verify(taskScope).close()
        verify(diagnostics).clearPlugin(key.value)
    }

    @Test
    fun `late creation failure closes all acquired scopes despite a cleanup failure`() {
        val factory = factory()
        val failure = IllegalStateException("Logger is unavailable")
        val cleanupFailure = IllegalStateException("Service cleanup failed")
        `when`(logging.logger(owner, id.value)).thenThrow(failure)
        doThrow(cleanupFailure).`when`(serviceScope).close()

        assertSame(failure, assertThrows(IllegalStateException::class.java) { factory.create() })
        assertTrue(failure.suppressed.contains(cleanupFailure))
        val order = inOrder(currencyScope, placeholderScope, configScope, serviceScope, eventScope, taskScope)
        order.verify(currencyScope).close()
        order.verify(placeholderScope).close()
        order.verify(configScope).close()
        order.verify(eventScope).close()
        order.verify(serviceScope).close()
        order.verify(taskScope).close()
    }

    @Test
    fun `partial creation failure closes acquired scopes without closing an unacquired service`() {
        val factory = factory()
        val failure = IllegalStateException("Currency is unavailable")
        `when`(currency.scope(key, placeholderScope)).thenThrow(failure)

        assertSame(failure, assertThrows(IllegalStateException::class.java) { factory.create() })
        verify(currencyScope, never()).close()
        verify(placeholderScope).close()
        verify(configScope).close()
        verify(serviceScope).close()
        verify(eventScope).close()
        verify(taskScope).close()
    }
}
