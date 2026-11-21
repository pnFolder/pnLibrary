package org.pnfolder.pnlibrary.core.settings

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import org.pnfolder.pnlibrary.api.runtime.PnLibraryConfig
import org.pnfolder.pnlibrary.common.io.AtomicFiles
import org.pnfolder.pnlibrary.common.io.TextFiles
import org.pnfolder.pnlibrary.common.lang.runCatchingNonFatal
import java.io.IOException
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.CopyOnWriteArrayList

/** What changed when a setting was set or reset. */
internal data class SettingChange(val spec: SettingSpec, val before: String, val after: String)

/** A setting with its value now; [changed] says whether an administrator set it, as opposed to the default. */
internal data class SettingValue(val spec: SettingSpec, val current: String, val changed: Boolean)

/**
 * The values of the library's settings, kept in one file (`data/settings.json`).
 *
 * Only what an administrator changed is written, so a default that is improved later reaches every server that never
 * touched it. A value that cannot be read (unknown key, wrong type, outside its limits) is ignored with a warning and
 * the default is used; a file that is not JSON at all is moved aside as `settings.json.broken`. Loading never throws.
 *
 * @param warning receives the problems found while reading, in words for the administrator
 */
internal class SettingsStore(
    private val file: Path,
    private val warning: (String) -> Unit = {},
) {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val listeners = CopyOnWriteArrayList<(LibrarySettings) -> Unit>()

    /** Key to the value as text, for what was set; the single source of truth together with the registry. */
    @Volatile
    private var overrides: Map<String, String> = load()

    @Volatile
    private var settings: LibrarySettings = build(overrides)

    /** The values in force now. */
    fun current(): LibrarySettings = settings

    /** The runtime policy built from the values in force now. */
    fun config(): PnLibraryConfig = settings.toConfig()

    /** Every setting in registry order with its value now. */
    fun values(): List<SettingValue> = SettingsRegistry.all.map { SettingValue(it, it.valueIn(settings), it.key in overrides) }

    /** The setting named [key] with its value now.
     * @throws SettingException when there is no such setting */
    fun value(key: String): SettingValue {
        val spec = specOf(key)
        return SettingValue(spec, spec.valueIn(settings), key in overrides)
    }

    /** Calls [listener] with the new values after every change; it runs on the thread that changed them. */
    fun onChange(listener: (LibrarySettings) -> Unit) {
        listeners += listener
    }

    /**
     * Sets [key] to [text] and saves the file.
     *
     * @throws SettingException when the key is unknown, the value is not acceptable or the file cannot be written;
     * nothing changes then
     */
    @Synchronized
    fun set(key: String, text: String): SettingChange {
        val spec = specOf(key)
        val next = overrides + (key to spec.normalize(text))
        return commit(spec, next)
    }

    /** Returns [key] to its default and saves the file.
     * @throws SettingException when the key is unknown or the file cannot be written */
    @Synchronized
    fun reset(key: String): SettingChange {
        val spec = specOf(key)
        return commit(spec, overrides - key)
    }

    private fun specOf(key: String): SettingSpec =
        SettingsRegistry[key] ?: throw SettingException("Такой настройки нет: $key. Список: /pn settings list")

    private fun commit(spec: SettingSpec, next: Map<String, String>): SettingChange {
        val before = spec.valueIn(settings)
        val built = build(next)
        save(next)
        overrides = next
        settings = built
        listeners.forEach { listener ->
            runCatchingNonFatal { listener(built) }.onFailure { warning("Настройка ${spec.key} сохранена, но применить её сразу не вышло: ${it.message}") }
        }
        return SettingChange(spec, before, spec.valueIn(built))
    }

    private fun build(values: Map<String, String>): LibrarySettings =
        values.entries.fold(LibrarySettings()) { settings, (key, text) -> specOf(key).apply(settings, text) }

    private fun save(values: Map<String, String>) {
        val json = JsonObject()
        SettingsRegistry.all.filter { it.key in values }.forEach { spec ->
            val text = requireNotNull(values[spec.key])
            json.add(
                spec.key,
                when (spec.kind) {
                    SettingKind.SWITCH -> JsonPrimitive(text.toBoolean())
                    SettingKind.NUMBER -> JsonPrimitive(text.toLong())
                    SettingKind.TEXT, SettingKind.TIME, SettingKind.CHOICE -> JsonPrimitive(text)
                },
            )
        }
        try {
            AtomicFiles.write(file, gson.toJson(json).toByteArray(UTF_8))
        } catch (error: IOException) {
            throw SettingException("Не удалось сохранить ${file.fileName}: ${error.message}")
        }
    }

    private fun load(): Map<String, String> {
        if (!Files.isRegularFile(file)) return emptyMap()
        val root = try {
            JsonParser.parseString(TextFiles.read(file, UTF_8)).asJsonObject
        } catch (_: Exception) {
            moveAside()
            return emptyMap()
        }
        val accepted = linkedMapOf<String, String>()
        root.entrySet().forEach { (key, element) ->
            val spec = SettingsRegistry[key]
            when {
                spec == null -> warning("${file.fileName}: настройки «$key» нет, значение пропущено.")
                !element.isJsonPrimitive -> warning("${file.fileName}: у настройки $key не то значение, берётся ${spec.default}.")
                else -> try {
                    accepted[key] = spec.normalize(element.asString)
                } catch (error: SettingException) {
                    warning("${file.fileName}: настройка $key: ${error.message} Берётся значение по умолчанию: ${spec.default}.")
                }
            }
        }
        return accepted
    }

    private fun moveAside() {
        val broken = file.resolveSibling("${file.fileName}.broken")
        val outcome = runCatchingNonFatal { Files.move(file, broken, StandardCopyOption.REPLACE_EXISTING) }
        warning(
            if (outcome.isSuccess) "${file.fileName} не читается, значения по умолчанию; файл сохранён как ${broken.fileName}."
            else "${file.fileName} не читается, значения по умолчанию.",
        )
    }
}
