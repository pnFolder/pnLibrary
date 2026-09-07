package ru.privatenull.pnlibrary.core.config.yaml

import ru.privatenull.pnlibrary.api.config.ConfigCodec
import ru.privatenull.pnlibrary.api.config.ConfigLoadResult
import ru.privatenull.pnlibrary.api.config.ConfigProblem
import ru.privatenull.pnlibrary.api.config.ConfigValidationException
import ru.privatenull.pnlibrary.api.config.ConfigValueValidator
import ru.privatenull.pnlibrary.api.config.ManagedConfig
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
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
) : ManagedConfig<T> {
    @Volatile private var loadedValue: T? = null

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
        val defaultsYaml = normalize(codec.encode(defaults))
        if (!file.exists()) {
            writeAtomic(defaultsYaml)
            val value = decodeAndValidate(defaultsYaml)
            loadedValue = value
            return ConfigLoadResult(value, YamlDefaultsMerger.paths(defaultsYaml), null)
        }

        val original = normalize(file.readText())
        val merged = YamlDefaultsMerger.merge(original, defaultsYaml)
        val value = decodeAndValidate(merged.content)
        if (!merged.changed) {
            loadedValue = value
            return ConfigLoadResult(value, emptyList(), null)
        }

        val backup = backup(original)
        writeAtomic(merged.content)
        logger.info("Добавлены новые параметры в ${file.name}: ${merged.addedPaths.joinToString()}")
        loadedValue = value
        return ConfigLoadResult(value, merged.addedPaths, backup)
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
        val encoded = normalize(codec.encode(value))
        decodeAndValidate(encoded)
        writeAtomic(encoded)
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
        } catch (error: Throwable) {
            logger.log(Level.SEVERE, "Не удалось прочитать ${file.name}: ${error.message}", error)
            throw error
        }
        val problems = validator.validate(value)
        if (problems.isNotEmpty()) throw ConfigValidationException(problems)
        return value
    }

    private fun backup(content: String): File {
        val target = Files.createTempFile(file.absoluteFile.parentFile.toPath(), "${file.name}.before-sync-", ".bak").toFile()
        target.writeText(content, Charsets.UTF_8)
        val backups = file.absoluteFile.parentFile.listFiles { candidate ->
            candidate.name.startsWith("${file.name}.before-sync-") && candidate.name.endsWith(".bak")
        }?.sortedByDescending(File::lastModified).orEmpty()
        backups.drop(MAX_BACKUPS).forEach { runCatching { it.delete() } }
        return target
    }

    private fun writeAtomic(content: String) {
        val temporary = Files.createTempFile(file.absoluteFile.parentFile.toPath(), "${file.name}.", ".tmp")
        try {
            Files.write(temporary, content.toByteArray(Charsets.UTF_8))
            codec.decode(content)
            try {
                Files.move(temporary, file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                Files.move(temporary, file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    private fun normalize(value: String): String = value.replace("\r\n", "\n").trimEnd() + "\n"

    private companion object {
        const val MAX_BACKUPS = 5
    }
}
