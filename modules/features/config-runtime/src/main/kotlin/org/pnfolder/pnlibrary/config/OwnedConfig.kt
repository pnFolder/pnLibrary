package org.pnfolder.pnlibrary.config

import org.pnfolder.pnlibrary.api.config.ConfigApply
import org.pnfolder.pnlibrary.api.config.ConfigChange
import org.pnfolder.pnlibrary.api.config.ConfigChangeCause
import org.pnfolder.pnlibrary.api.config.ConfigEditException
import org.pnfolder.pnlibrary.api.config.ConfigEditResult
import org.pnfolder.pnlibrary.api.config.ConfigId
import org.pnfolder.pnlibrary.api.config.ConfigInfo
import org.pnfolder.pnlibrary.api.config.ConfigLoadResult
import org.pnfolder.pnlibrary.api.config.ConfigProblem
import org.pnfolder.pnlibrary.api.config.ConfigSetting
import org.pnfolder.pnlibrary.api.config.ConfigValidationException
import org.pnfolder.pnlibrary.api.config.ManagedConfig
import org.pnfolder.pnlibrary.api.config.ReloadRequest
import org.pnfolder.pnlibrary.api.logging.PnLogger
import org.pnfolder.pnlibrary.config.yaml.CodeFirstYaml
import org.pnfolder.pnlibrary.config.yaml.ConfigSettingsEditor
import org.pnfolder.pnlibrary.config.yaml.UnknownConfigTypeException
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList
import java.util.function.Consumer
import java.util.function.UnaryOperator

/**
 * A managed file that remembers its identifier and path. It tells when it was loaded and whether it is waiting for a type,
 * which is the case when its last read failed or skipped list elements because a `type` word was unknown.
 */
internal class OwnedConfig<T>(
    override val id: ConfigId,
    val file: Path,
    private val modelType: Class<*>,
    val readOnly: Boolean,
    private val delegate: CodeFirstYaml<T>,
    private val editor: ConfigSettingsEditor,
    private val logger: PnLogger,
) : ManagedConfig<T> by delegate {
    @Volatile
    private var loadedAt: Instant? = null

    /** Whether reading again could bring back what the last read left out. */
    @Volatile
    var waitingForTypes = false
        private set

    /** The strongest requirement of edits that do not work yet; a read clears [ConfigApply.RELOAD], a restart clears the rest. */
    @Volatile
    private var pending: ConfigApply? = null

    private val changeListeners = CopyOnWriteArrayList<Consumer<ConfigChange>>()
    private val reloadListeners = CopyOnWriteArrayList<Consumer<ReloadRequest>>()

    override val info: ConfigInfo
        get() = ConfigInfo(
            id = id,
            modelType = modelType,
            file = file,
            exists = Files.exists(file),
            isLoaded = isLoaded,
            loadedAt = loadedAt,
            readOnly = readOnly,
            skippedUnknownTypes = delegate.skippedUnknownTypes,
            problems = if (isLoaded) runCatching { delegate.validate() }.getOrDefault(emptyList<ConfigProblem>()) else emptyList(),
            pendingApply = pending,
        )

    override val settings: List<ConfigSetting>
        get() = editor.describe(if (isLoaded) delegate.get() else null)

    override fun edit(path: String, text: String): ConfigEditResult {
        check(!readOnly) { "Файл $id открыт только для чтения" }
        val applied = synchronized(delegate) {
            check(isLoaded) { "Файл $id не загружен" }
            val applied = editor.set(delegate.get() as Any, path, text)
            try {
                delegate.save()
            } catch (error: ConfigValidationException) {
                applied.restore()
                throw ConfigEditException(error.problems.joinToString("; ") { "${it.path}: ${it.message}" }, error)
            } catch (error: Exception) {
                applied.restore()
                throw ConfigEditException(error.message ?: "Не удалось записать файл", error)
            }
            applied
        }
        val result = ConfigEditResult(applied.setting, applied.previous)
        if (applied.setting.apply != ConfigApply.LIVE) {
            pending = strongest(pending, applied.setting.apply)
            fire(reloadListeners, ReloadRequest(id, applied.setting.apply, listOf(applied.setting)))
        }
        fire(changeListeners, ConfigChange(id, ConfigChangeCause.EDITED, listOf(path)))
        return result
    }

    override fun onChanged(vararg listeners: Consumer<ConfigChange>): AutoCloseable = subscribe(changeListeners, listeners.toList())

    override fun onReloadRequired(vararg listeners: Consumer<ReloadRequest>): AutoCloseable = subscribe(reloadListeners, listeners.toList())

    override fun load(): ConfigLoadResult<T> = track(ConfigChangeCause.LOADED) { delegate.load() }
    override fun reload(): ConfigLoadResult<T> = track(ConfigChangeCause.RELOADED) { delegate.reload() }
    override fun loadValue(): T = load().value
    override fun reloadValue(): T = reload().value

    override fun save() {
        delegate.save()
        fire(changeListeners, ConfigChange(id, ConfigChangeCause.SAVED, emptyList()))
    }

    override fun save(value: T) {
        delegate.save(value)
        fire(changeListeners, ConfigChange(id, ConfigChangeCause.SAVED, emptyList()))
    }

    override fun update(updater: UnaryOperator<T>): T = delegate.update(updater).also {
        fire(changeListeners, ConfigChange(id, ConfigChangeCause.SAVED, emptyList()))
    }

    override fun resetToDefaults(): T = delegate.resetToDefaults().also {
        fire(changeListeners, ConfigChange(id, ConfigChangeCause.RESET, emptyList()))
    }

    override fun unload() {
        delegate.unload()
        loadedAt = null
    }

    override fun close() = unload()

    private fun track(cause: ConfigChangeCause, action: () -> ConfigLoadResult<T>): ConfigLoadResult<T> = try {
        action().also {
            loadedAt = Instant.now()
            waitingForTypes = delegate.skippedUnknownTypes.isNotEmpty()
            if (pending == ConfigApply.RELOAD) pending = null
            fire(changeListeners, ConfigChange(id, cause, emptyList()))
        }
    } catch (error: Exception) {
        waitingForTypes = generateSequence<Throwable>(error) { it.cause }.any { it is UnknownConfigTypeException }
        throw error
    }

    private fun strongest(a: ConfigApply?, b: ConfigApply): ConfigApply = if (a == null || b.ordinal > a.ordinal) b else a

    private fun <E> subscribe(target: CopyOnWriteArrayList<Consumer<E>>, listeners: List<Consumer<E>>): AutoCloseable {
        target.addAll(listeners)
        return AutoCloseable { target.removeAll(listeners.toSet()) }
    }

    /** A failing listener is logged and never stops the others or the change itself. */
    private fun <E> fire(listeners: List<Consumer<E>>, event: E) {
        listeners.forEach { listener ->
            try {
                listener.accept(event)
            } catch (error: Exception) {
                logger.warning("Обработчик изменения $id завершился ошибкой: ${error.message}", error)
            }
        }
    }
}
