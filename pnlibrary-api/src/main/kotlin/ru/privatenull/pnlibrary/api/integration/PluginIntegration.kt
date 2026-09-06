package ru.privatenull.pnlibrary.api.integration

import ru.privatenull.pnlibrary.api.DiagnosticContainer
import ru.privatenull.pnlibrary.api.DiagnosticRegistration
import ru.privatenull.pnlibrary.api.PluginMetrics
import ru.privatenull.pnlibrary.api.PluginUpdateRequest
import ru.privatenull.pnlibrary.api.PnLibrary
import ru.privatenull.pnlibrary.api.TaskScope
import ru.privatenull.pnlibrary.api.UpdateRegistration
import java.nio.file.Path
import java.util.function.Consumer

/**
 * Единая регистрация плагина в pnLibrary.
 *
 * Объект объединяет задачи, метрики, диагностику и обновления. Владельцу не
 * требуется хранить и закрывать каждую регистрацию отдельно: достаточно вызвать
 * [close] при выключении плагина.
 *
 * Kotlin:
 * ```kotlin
 * val integration = PluginIntegration.builder(library, plugin, "pnclans")
 *     .metrics(33208) { it.simplePie("storage") { "SQLITE" } }
 *     .diagnostics(plugin.dataFolder.toPath(), container)
 *     .updates(updateRequest)
 *     .build()
 * ```
 *
 * Java:
 * ```java
 * PluginIntegration integration = PluginIntegration
 *     .builder(library, plugin, "example")
 *     .metrics(12345, metrics -> metrics.simplePie("mode", () -> "DEFAULT"))
 *     .diagnostics(plugin.getDataFolder().toPath(), container)
 *     .updates(updateRequest)
 *     .build();
 * ```
 */
class PluginIntegration private constructor(
    /** Область задач, автоматически отменяемая при [close]. */
    val tasks: TaskScope,
    /** Метрики плагина или `null`, если они не регистрировались. */
    val metrics: PluginMetrics?,
    /** Диагностическая регистрация или `null`. */
    val diagnostics: DiagnosticRegistration?,
    /** Регистрация обновлений или `null`. */
    val updates: UpdateRegistration?,
) : AutoCloseable {

    /** Закрывает обновления, диагностику, метрики и задачи в безопасном порядке. */
    override fun close() {
        runCatching { updates?.close() }
        runCatching { diagnostics?.close() }
        runCatching { metrics?.close() }
        runCatching { tasks.close() }
    }

    /** Конструктор единой интеграции, доступный одинаково на всех платформах. */
    class Builder internal constructor(
        private val library: PnLibrary,
        private val owner: Any,
        private val pluginId: String,
    ) {
        private var metricsProjectId: Int? = null
        private var metricsConfigurer: Consumer<PluginMetrics>? = null
        private var diagnosticsDirectory: Path? = null
        private var diagnosticContainer: DiagnosticContainer? = null
        private var updateRequest: PluginUpdateRequest? = null

        /** Включает метрики и настраивает графики через переданный callback. */
        fun metrics(projectId: Int, configure: Consumer<PluginMetrics>): Builder = apply {
            require(projectId > 0) { "projectId must be positive" }
            metricsProjectId = projectId
            metricsConfigurer = configure
        }

        /** Регистрирует диагностический контейнер и разрешённую папку данных. */
        fun diagnostics(dataDirectory: Path, container: DiagnosticContainer): Builder = apply {
            diagnosticsDirectory = dataDirectory
            diagnosticContainer = container
        }

        /** Регистрирует проверку и загрузку обновлений плагина. */
        fun updates(request: PluginUpdateRequest): Builder = apply { updateRequest = request }

        /** Создаёт все выбранные подсистемы как одну атомарную регистрацию. */
        fun build(): PluginIntegration {
            require(pluginId.matches(Regex("[A-Za-z0-9_.-]+"))) { "invalid pluginId" }
            val tasks = library.tasks.scope(owner)
            var metrics: PluginMetrics? = null
            var diagnostics: DiagnosticRegistration? = null
            var updates: UpdateRegistration? = null
            try {
                metricsProjectId?.let { projectId ->
                    metrics = library.metrics.open(owner, projectId).also { metricsConfigurer?.accept(it) }
                }
                diagnosticContainer?.let { container ->
                    diagnostics = library.diagnostics.register(
                        pluginId,
                        requireNotNull(diagnosticsDirectory),
                        container,
                    )
                }
                updateRequest?.let { updates = library.updates.register(owner, it) }
                return PluginIntegration(tasks, metrics, diagnostics, updates)
            } catch (error: Throwable) {
                runCatching { updates?.close() }
                runCatching { diagnostics?.close() }
                runCatching { metrics?.close() }
                runCatching { tasks.close() }
                throw error
            }
        }
    }

    companion object {
        /** Начинает описание интеграции [pluginId] для объекта-владельца [owner]. */
        @JvmStatic
        fun builder(library: PnLibrary, owner: Any, pluginId: String): Builder =
            Builder(library, owner, pluginId)
    }
}
