package org.pnfolder.pnlibrary.config.yaml

import org.pnfolder.pnlibrary.api.config.ConfigCodec
import org.pnfolder.pnlibrary.api.config.ConfigLoadResult
import org.pnfolder.pnlibrary.api.config.ConfigOptions
import org.pnfolder.pnlibrary.api.config.ConfigProblem
import org.pnfolder.pnlibrary.api.config.ConfigValidationException
import org.pnfolder.pnlibrary.api.config.ConfigValueValidator
import org.pnfolder.pnlibrary.api.config.ManagedConfig
import org.pnfolder.pnlibrary.api.config.MissingFilePolicy
import org.pnfolder.pnlibrary.api.config.MissingValuePolicy
import org.pnfolder.pnlibrary.api.config.UnknownValuePolicy
import org.pnfolder.pnlibrary.api.logging.PnLogger
import java.io.File
import java.util.function.UnaryOperator

/**
 * Loads typed YAML and synchronizes it with code-defined defaults.
 *
 * Missing keys and their comments are added recursively. Existing values,
 * ordering, comments, and extension keys are preserved. A `.before-sync-*.bak`
 * copy is made before a rewrite when [ConfigOptions.backups] is on, and the target
 * is replaced atomically only after successful decoding.
 *
 * ```kotlin
 * val config = CodeFirstYaml(file, Settings(), codec, logger).load().value
 * ```
 */
class CodeFirstYaml<T> @JvmOverloads constructor(
    private val file: File,
    private val defaults: T,
    private val codec: ConfigCodec<T>,
    private val logger: PnLogger,
    private val validator: ConfigValueValidator<T> = ConfigValueValidator { emptyList() },
    private val options: ConfigOptions = ConfigOptions.DEFAULT,
) : ManagedConfig<T> {
    @Volatile
    private var loadedValue: T? = null
    private val migrationEngine = YamlMigrationEngine()
    private val fileStore = ConfigurationFileStore(file) { content -> codec.decode(content) }

    /** `true` after a successful [load] or [reload] and before [unload]. */
    override val isLoaded: Boolean get() = loadedValue != null

    /** Returns the loaded value or fails when the file is not loaded. */
    override fun get(): T = loadedValue ?: error("Configuration ${file.name} is not loaded")

    /**
     * Creates a missing file, adds new fields to an existing file, validates the
     * value, and returns synchronization details.
     */
    @Synchronized
    override fun load(): ConfigLoadResult<T> {
        val parent = file.absoluteFile.parentFile
        require(parent.isDirectory || parent.mkdirs()) { "Cannot create configuration directory $parent" }
        val rawDefaultsYaml = fileStore.normalize(codec.encode(defaults))
        val defaultsYaml = SupportHeader.apply(
            options.migrations?.let { fileStore.normalize(migrationEngine.stampDefaults(rawDefaultsYaml, it)) } ?: rawDefaultsYaml,
        )
        if (!file.exists()) return createFromDefaults(defaultsYaml)

        val original = fileStore.normalize(file.readText())
        val migration = options.migrations?.let { migrationEngine.migrate(original, it) }
        if (migration?.newerThanCode == true) return readNewerFile(original)
        // Re-dumped YAML loses administrator comments, so it is used only when a migration step ran.
        val working = migration?.takeIf { it.applied.isNotEmpty() }?.content?.let(fileStore::normalize) ?: original
        val differences = differences(defaultsYaml, working)
        val syncResult = synchronize(working, defaultsYaml)
        val synchronized = SupportHeader.apply(syncResult.content)
        val value = decodeAndValidate(synchronized)
        if (synchronized == original) {
            loadedValue = value
            return ConfigLoadResult(value, emptyList(), null, appliedMigrations = migration?.applied.orEmpty())
        }
        return writeChanges(original, synchronized, value, differences, migration?.applied.orEmpty(), syncResult)
    }

    private fun createFromDefaults(defaultsYaml: String): ConfigLoadResult<T> {
        check(options.missingFile == MissingFilePolicy.CREATE) { "Configuration ${file.name} does not exist" }
        val value = decodeAndValidate(defaultsYaml)
        fileStore.writeDecoded(defaultsYaml)
        loadedValue = value
        return ConfigLoadResult(value, YamlDefaultsMerger.paths(defaultsYaml), null)
    }

    /** A file written by a newer plugin version is read as it is and never rewritten. */
    private fun readNewerFile(original: String): ConfigLoadResult<T> {
        logger.warning("Файл ${file.name} создан более новой версией плагина; он читается без изменений.")
        val value = decodeAndValidate(original)
        loadedValue = value
        return ConfigLoadResult(value, emptyList(), null)
    }

    /** Paths missing from the file and paths unknown to the code. */
    private class Differences(val missing: List<String>, val unknown: List<String>)

    /** The paths the file lacks and the paths the code does not know; fails when the policy forbids either. */
    private fun differences(defaultsYaml: String, working: String): Differences {
        val defaultPaths = YamlDefaultsMerger.paths(defaultsYaml).toSet()
        val workingPaths = YamlDefaultsMerger.paths(working).toSet()
        val missing = (defaultPaths - workingPaths).sorted()
        val unknown = (workingPaths - defaultPaths).sorted()
        val requiredMissing = ((codec as? ConfigSchema)?.requiredPaths.orEmpty() - workingPaths).sorted()
        check(requiredMissing.isEmpty()) {
            "Required configuration values are missing from ${file.name}: ${requiredMissing.joinToString()}"
        }
        check(options.missingValues != MissingValuePolicy.FAIL || missing.isEmpty()) {
            "Missing configuration values in ${file.name}: ${missing.joinToString()}"
        }
        check(options.unknownValues != UnknownValuePolicy.FAIL || unknown.isEmpty()) {
            "Unknown configuration values in ${file.name}: ${unknown.joinToString()}"
        }
        return Differences(missing, unknown)
    }

    private fun synchronize(working: String, defaultsYaml: String): YamlDefaultsMerger.Result {
        if (options.unknownValues != UnknownValuePolicy.REMOVE) {
            return YamlDefaultsMerger.merge(working, defaultsYaml, options.missingValues, options.comments)
        }
        var canonical = fileStore.normalize(codec.encode(decodeAndValidate(working)))
        options.migrations?.let { canonical = fileStore.normalize(migrationEngine.stampDefaults(canonical, it)) }
        return YamlDefaultsMerger.Result(canonical, emptyList(), emptyList())
    }

    private fun writeChanges(
        original: String,
        synchronized: String,
        value: T,
        differences: Differences,
        migrations: List<String>,
        syncResult: YamlDefaultsMerger.Result,
    ): ConfigLoadResult<T> {
        val backup = if (options.backups) fileStore.backup(original) else null
        fileStore.writeDecoded(synchronized)
        val removeUnknown = options.unknownValues == UnknownValuePolicy.REMOVE
        if (differences.missing.isNotEmpty()) logger.info("Добавлены новые параметры в ${file.name}: ${differences.missing.joinToString()}")
        if (differences.unknown.isNotEmpty() && removeUnknown) {
            logger.info("Удалены неизвестные параметры из ${file.name}: ${differences.unknown.joinToString()}")
        }
        if (migrations.isNotEmpty()) logger.info("Применены миграции ${file.name}: ${migrations.joinToString()}")
        loadedValue = value
        return ConfigLoadResult(
            value,
            if (options.missingValues == MissingValuePolicy.ADD) differences.missing else emptyList(),
            backup,
            if (removeUnknown) differences.unknown else emptyList(),
            syncResult.addedComments,
            migrations,
        )
    }

    /** Reloads the file and replaces the value only after full validation. */
    @Synchronized
    override fun reload(): ConfigLoadResult<T> = load()

    /** Saves the value that is currently loaded. */
    @Synchronized
    override fun save() = save(get())

    /** Atomically saves [value] after decoding and semantic validation. */
    @Synchronized
    override fun save(value: T) {
        val raw = fileStore.normalize(codec.encode(value))
        val encoded = SupportHeader.apply(
            options.migrations?.let { fileStore.normalize(migrationEngine.stampDefaults(raw, it)) } ?: raw,
        )
        decodeAndValidate(encoded)
        fileStore.writeDecoded(encoded)
        loadedValue = value
    }

    /** Applies [updater] to the loaded value and saves the result. */
    @Synchronized
    override fun update(updater: UnaryOperator<T>): T {
        val updated = updater.apply(get())
        save(updated)
        return updated
    }

    /** Runs the validator on the loaded value without writing anything. */
    override fun validate(): List<ConfigProblem> = validator.validate(get())

    /** Writes the code defaults to the file and makes them the loaded value. */
    @Synchronized
    override fun resetToDefaults(): T {
        save(defaults)
        return defaults
    }

    /** Clears the in-memory value without changing the file. */
    @Synchronized
    override fun unload() { loadedValue = null }

    private fun decodeAndValidate(content: String): T {
        val value = try {
            codec.decode(content)
        } catch (error: Exception) {
            logger.error("Не удалось прочитать ${file.name}: ${error.message}", error)
            throw error
        }
        val problems = validator.validate(value)
        if (problems.isNotEmpty()) throw ConfigValidationException(problems)
        return value
    }

}
