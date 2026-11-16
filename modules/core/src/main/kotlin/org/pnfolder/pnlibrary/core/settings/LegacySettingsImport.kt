package org.pnfolder.pnlibrary.core.settings

import org.pnfolder.pnlibrary.common.io.AtomicFiles
import org.pnfolder.pnlibrary.common.io.TextFiles
import org.pnfolder.pnlibrary.common.lang.runCatchingNonFatal
import org.pnfolder.pnlibrary.updates.paths.LibraryPaths
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import java.nio.file.Files
import java.nio.file.Path

/**
 * Takes the values of the old `config.yml` and `downloads.yml` (and the download choice kept in `downloads/preferences.state`)
 * into the [SettingsStore] once, then renames the files to `*.yml.old` so nothing is imported twice.
 *
 * A key that the store already holds is never replaced: what an administrator set with a command wins over an old file.
 * It never throws; what could not be read is reported through [warning] and the file is left where it is.
 */
internal class LegacySettingsImport(
    private val paths: LibraryPaths,
    private val warning: (String) -> Unit = {},
) {
    /** Imports into [store] every old file that exists. */
    fun run(store: SettingsStore) {
        val held = store.values().filter { it.changed }.map { it.spec.key }.toSet()
        importYaml(paths.legacyConfig, "", store, held)
        importYaml(paths.legacyDownloadsConfig, "downloads.", store, held)
        importDownloadChoice(store, held)
    }

    private fun importYaml(file: Path, prefix: String, store: SettingsStore, held: Set<String>) {
        if (!Files.isRegularFile(file)) return
        val root = try {
            Yaml(SafeConstructor(LoaderOptions())).load<Any?>(TextFiles.read(file)) as? Map<*, *> ?: emptyMap<Any, Any>()
        } catch (error: Exception) {
            warning("${file.fileName} не прочитан и остался на месте: ${error.message?.lineSequence()?.firstOrNull()}")
            return
        }
        flatten(root, prefix).forEach { (key, value) -> take(store, held, file.fileName.toString(), key, value) }
        retire(file)
    }

    /** `destinations: {cache: true}` becomes `destinations.cache`; keys of the old files were already in the kebab case. */
    private fun flatten(map: Map<*, *>, prefix: String): Map<String, Any?> = buildMap {
        map.forEach { (name, value) ->
            val key = prefix + name
            if (value is Map<*, *>) putAll(flatten(value, "$key.")) else put(key, value)
        }
    }

    private fun take(store: SettingsStore, held: Set<String>, source: String, key: String, value: Any?) {
        val spec = SettingsRegistry[key]
        if (spec == null) {
            warning("$source: параметр «$key» больше не существует, пропущен.")
            return
        }
        if (key in held || value == null) return
        try {
            val text = value.toString()
            if (spec.normalize(text) != spec.default) store.set(key, text)
        } catch (error: SettingException) {
            warning("$source: ${spec.key} не перенесён: ${error.message}")
        }
    }

    /** The old `/pn deps auto` choice beat `downloads.yml`, so it beats it here too. */
    private fun importDownloadChoice(store: SettingsStore, held: Set<String>) {
        val file = paths.downloads.resolve("preferences.state")
        if (!Files.isRegularFile(file)) return
        val choice = runCatchingNonFatal {
            Files.readAllLines(file).firstOrNull { it.startsWith("automatic=") }?.substringAfter('=')?.trim()?.toBooleanStrictOrNull()
        }.getOrNull()
        if (choice != null && "downloads.automatic" !in held) {
            runCatchingNonFatal { store.set("downloads.automatic", choice.toString()) }
                .onFailure { warning("Выбор автозагрузки не перенесён: ${it.message}") }
        }
        runCatchingNonFatal { Files.deleteIfExists(file) }
    }

    private fun retire(file: Path) {
        val old = file.resolveSibling("${file.fileName}.old")
        runCatchingNonFatal { AtomicFiles.move(file, old) }
            .onSuccess { warning("${file.fileName} больше не используется: настройки перенесены в data/settings.json и меняются командой /pn settings. Файл сохранён как ${old.fileName}.") }
            .onFailure { warning("${file.fileName} не удалось переименовать в ${old.fileName}: ${it.message}") }
    }
}
