package ru.privatenull.pnlibrary.core.config.yaml

import ru.privatenull.pnlibrary.api.config.ConfigCodec
import ru.privatenull.pnlibrary.api.config.ConfigLoadResult
import ru.privatenull.pnlibrary.api.config.ConfigProblem
import ru.privatenull.pnlibrary.api.config.ConfigValidationException
import ru.privatenull.pnlibrary.api.config.ConfigValueValidator
import ru.privatenull.pnlibrary.api.config.ManagedConfig
import ru.privatenull.pnlibrary.api.config.ConfigOptions
import ru.privatenull.pnlibrary.api.config.MissingFilePolicy
import ru.privatenull.pnlibrary.api.config.MissingValuePolicy
import ru.privatenull.pnlibrary.api.config.UnknownValuePolicy
import ru.privatenull.pnlibrary.api.config.CommentPolicy
import java.io.File
import java.util.logging.Level
import java.util.logging.Logger
import java.util.function.UnaryOperator

/**
 * Loads typed YAML and synchronizes it with code-defined defaults.
 *
 * Missing keys and their comments are added recursively. Existing values,
 * ordering, comments, and extension keys are preserved. A `.bak` file is
 * created before synchronization, and the target is replaced atomically only
 * after successful decoding.
 *
 * ```kotlin
 * val config = CodeFirstYaml(file, Settings(), codec, logger).load().value
 * ```
 */
class CodeFirstYaml<T> @JvmOverloads constructor(
    private val file: File,
    private val defaults: T,
    private val codec: ConfigCodec<T>,
    private val logger: Logger,
    private val validator: ConfigValueValidator<T> = ConfigValueValidator { emptyList() },
    private val options: ConfigOptions = ConfigOptions.DEFAULT,
) : ManagedConfig<T> {
    @Volatile
    private var loadedValue: T? = null
    private val migrationEngine = YamlMigrationEngine()
    private val fileStore = ConfigurationFileStore(file) { content -> codec.decode(content) }

    /** `true` after a successful [load] or [reload] and before [unload]. */
    override val isLoaded: Boolean get() = loadedValue != null

    /** Returns the current in-memory value. */
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
        val defaultsYaml = options.migrations?.let {
            fileStore.normalize(migrationEngine.stampDefaults(rawDefaultsYaml, it))
        } ?: rawDefaultsYaml
        if (!file.exists()) {
            check(options.missingFile == MissingFilePolicy.CREATE) { "Configuration ${file.name} does not exist" }
            fileStore.write(defaultsYaml)
            val value = decodeAndValidate(defaultsYaml)
            loadedValue = value
            return ConfigLoadResult(value, YamlDefaultsMerger.paths(defaultsYaml), null)
        }

        val original = fileStore.normalize(file.readText())
        val migration = options.migrations?.let { migrationEngine.migrate(original, it) }
        val working = migration?.content?.let(fileStore::normalize) ?: original
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

        val syncResult = if (options.unknownValues == UnknownValuePolicy.REMOVE) {
            var canonical = fileStore.normalize(codec.encode(decodeAndValidate(working)))
            options.migrations?.let {
                canonical = fileStore.normalize(migrationEngine.stampDefaults(canonical, it))
            }
            YamlDefaultsMerger.Result(canonical, emptyList(), emptyList())
        } else {
            YamlDefaultsMerger.merge(
                working,
                defaultsYaml,
                addMissingValues = options.missingValues == MissingValuePolicy.ADD,
                addComments = options.comments == CommentPolicy.ADD_MISSING,
            )
        }
        val synchronized = syncResult.content
        val value = decodeAndValidate(synchronized)
        if (synchronized == original) {
            loadedValue = value
            return ConfigLoadResult(value, emptyList(), null, appliedMigrations = migration?.applied.orEmpty())
        }

        val backup = if (options.backups) fileStore.backup(original) else null
        fileStore.write(synchronized)
        if (missing.isNotEmpty()) logger.info("Добавлены новые параметры в ${file.name}: ${missing.joinToString()}")
        if (unknown.isNotEmpty() && options.unknownValues == UnknownValuePolicy.REMOVE)
            logger.info("Удалены неизвестные параметры из ${file.name}: ${unknown.joinToString()}")
        if (!migration?.applied.isNullOrEmpty())
            logger.info("Применены миграции ${file.name}: ${migration!!.applied.joinToString()}")
        loadedValue = value
        return ConfigLoadResult(
            value,
            if (options.missingValues == MissingValuePolicy.ADD) missing else emptyList(),
            backup,
            if (options.unknownValues == UnknownValuePolicy.REMOVE) unknown else emptyList(),
            syncResult.addedComments,
            migration?.applied.orEmpty(),
        )
    }

    /** Reloads the file and replaces the value only after full validation. */
    @Synchronized
    override fun reload(): ConfigLoadResult<T> = load()

    /** Saves the current in-memory value. */
    @Synchronized
    override fun save() = save(get())

    /** Atomically saves [value] after decoding and semantic validation. */
    @Synchronized
    override fun save(value: T) {
        val raw = fileStore.normalize(codec.encode(value))
        val encoded = options.migrations?.let {
            fileStore.normalize(migrationEngine.stampDefaults(raw, it))
        } ?: raw
        decodeAndValidate(encoded)
        fileStore.write(encoded)
        loadedValue = value
    }

    /** Updates and saves the configuration as one synchronized operation. */
    @Synchronized
    override fun update(updater: UnaryOperator<T>): T {
        val updated = updater.apply(get())
        save(updated)
        return updated
    }

    /** Runs the validator against the current value. */
    override fun validate(): List<ConfigProblem> = validator.validate(get())

    /** Saves and returns the code-defined defaults. */
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
            logger.log(Level.SEVERE, "Не удалось прочитать ${file.name}: ${error.message}", error)
            throw error
        }
        val problems = validator.validate(value)
        if (problems.isNotEmpty()) throw ConfigValidationException(problems)
        return value
    }

}
