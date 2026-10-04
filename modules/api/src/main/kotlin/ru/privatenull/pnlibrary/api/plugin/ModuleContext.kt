package ru.privatenull.pnlibrary.api.plugin

import java.util.Locale
import ru.privatenull.pnlibrary.api.actions.ActionService
import ru.privatenull.pnlibrary.api.config.ConfigScope
import ru.privatenull.pnlibrary.api.cooldowns.CooldownService
import ru.privatenull.pnlibrary.api.diagnostics.DiagnosticRegistration
import ru.privatenull.pnlibrary.api.downloads.DownloadRegistration
import ru.privatenull.pnlibrary.api.events.EventScope
import ru.privatenull.pnlibrary.api.logging.PnLogger
import ru.privatenull.pnlibrary.api.metrics.ErrorReporter
import ru.privatenull.pnlibrary.api.placeholders.PlaceholderService
import ru.privatenull.pnlibrary.api.services.ServiceManager
import ru.privatenull.pnlibrary.api.tasks.TaskScope
import ru.privatenull.pnlibrary.api.text.ComponentService
import ru.privatenull.pnlibrary.api.updates.UpdateRegistration

/** Owns the isolated pnLibrary capabilities of one logical plugin module. */
interface ModuleContext : AutoCloseable {
    /** Registry-wide stable identity used by shared services and diagnostics. */
    val key: PluginId
    /** Local identity unique within the owning platform plugin. */
    val id: ModuleId
    /** Immutable platform and display metadata for this module. */
    val metadata: PluginMetadata
    /** Buffered lifecycle-message factory. */
    val lifecycle: PluginLifecycle
    /** Buffered arbitrary-message factory. */
    val messages: PluginMessages
    /** Owner-bound event subscriptions. */
    val events: EventScope
    /** Owner-bound scheduled tasks. */
    val tasks: TaskScope
    /** Module-local service registry. */
    val services: ServiceManager
    /** Module-bound structured logger. */
    val logger: PnLogger
    /** Module-bound configuration scope. */
    val configs: ConfigScope
    /** Portable configurable-action service. */
    val actions: ActionService
    /** Module-local placeholder registry. */
    val placeholders: PlaceholderService
    /** Adventure component parsing and rendering service. */
    val components: ComponentService
    /** Module-local cooldown service. */
    val cooldowns: CooldownService
    /** Runtime metrics control for this module. */
    val metrics: MetricsController
    /** Normalized error capture shared by local diagnostics and remote providers. */
    val errors: ErrorReporter
    /** Diagnostics registration, or `null` when diagnostics were not configured. */
    val diagnostics: DiagnosticRegistration?
    /** Update registration, or `null` when updates were not configured. */
    val updates: UpdateRegistration?
    /** Auxiliary download registration, or `null` when none was configured. */
    val downloads: DownloadRegistration?
    /** Whether this module has released all owned resources. */
    val isClosed: Boolean
    /** Releases every capability and registration owned by this module. */
    override fun close()
}

/** Validated, case-insensitive local identity of a logical module. */
class ModuleId private constructor(
    /** Normalized lowercase module identifier. */
    val value: String,
) {
    /** Returns whether [other] contains the same normalized identifier. */
    override fun equals(other: Any?): Boolean = other is ModuleId && value == other.value
    /** Returns the normalized identifier hash. */
    override fun hashCode(): Int = value.hashCode()
    /** Returns [value] without additional formatting. */
    override fun toString(): String = value

    /** Creates validated module identities. */
    companion object {
        private val FORMAT = Regex("[a-z0-9][a-z0-9_.-]{0,63}")

        /** Normalizes and validates [value]. */
        @JvmStatic
        fun of(value: String): ModuleId {
            val normalized = value.trim().lowercase(Locale.ROOT)
            require(FORMAT.matches(normalized)) { "moduleId must match ${FORMAT.pattern}" }
            return ModuleId(normalized)
        }
    }
}
