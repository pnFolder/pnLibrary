package ru.privatenull.pnlibrary.api.plugin

import java.util.Locale
import ru.privatenull.pnlibrary.api.actions.ActionService
import ru.privatenull.pnlibrary.api.config.ConfigScope
import ru.privatenull.pnlibrary.api.cooldowns.CooldownService
import ru.privatenull.pnlibrary.api.currency.CurrencyService
import ru.privatenull.pnlibrary.api.currency.CurrencyStorageFactory
import ru.privatenull.pnlibrary.api.diagnostics.DiagnosticRegistration
import ru.privatenull.pnlibrary.api.downloads.DownloadRegistration
import ru.privatenull.pnlibrary.api.events.EventScope
import ru.privatenull.pnlibrary.api.logging.PnLogger
import ru.privatenull.pnlibrary.api.placeholders.PlaceholderService
import ru.privatenull.pnlibrary.api.services.ServiceManager
import ru.privatenull.pnlibrary.api.tasks.TaskScope
import ru.privatenull.pnlibrary.api.text.ComponentService
import ru.privatenull.pnlibrary.api.updates.UpdateRegistration

/** Owns the isolated pnLibrary capabilities of one logical plugin module. */
interface ModuleContext : AutoCloseable {
    val id: ModuleId
    val metadata: PluginMetadata
    val lifecycle: PluginLifecycle
    val messages: PluginMessages
    val events: EventScope
    val tasks: TaskScope
    val services: ServiceManager
    val logger: PnLogger
    val configs: ConfigScope
    val actions: ActionService
    val placeholders: PlaceholderService
    val components: ComponentService
    val cooldowns: CooldownService
    val currencies: CurrencyService
    val currencyStorages: CurrencyStorageFactory
    val metrics: MetricsController
    val diagnostics: DiagnosticRegistration?
    val updates: UpdateRegistration?
    val downloads: DownloadRegistration?
    val isClosed: Boolean
    override fun close()
}

/** Validated, case-insensitive local identity of a logical module. */
class ModuleId private constructor(val value: String) {
    override fun equals(other: Any?): Boolean = other is ModuleId && value == other.value
    override fun hashCode(): Int = value.hashCode()
    override fun toString(): String = value

    companion object {
        private val FORMAT = Regex("[a-z0-9][a-z0-9_.-]{0,63}")

        @JvmStatic
        fun of(value: String): ModuleId {
            val normalized = value.trim().lowercase(Locale.ROOT)
            require(FORMAT.matches(normalized)) { "moduleId must match ${FORMAT.pattern}" }
            return ModuleId(normalized)
        }
    }
}
