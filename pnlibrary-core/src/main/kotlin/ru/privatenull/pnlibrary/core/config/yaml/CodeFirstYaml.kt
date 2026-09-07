package ru.privatenull.pnlibrary.core.config.yaml

import ru.privatenull.pnlibrary.api.config.*
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.logging.Level
import java.util.logging.Logger
import java.util.function.UnaryOperator

/**
 * Загружает типизированный YAML и синхронизирует его с defaults из кода.
 *
 * Отсутствующие ключи добавляются рекурсивно вместе с комментариями. Существующие
 * значения, порядок, комментарии и неизвестные ключи расширений сохраняются.
 * Перед первым изменением создаётся `.bak`, а файл заменяется атомарно только
 * после успешного декодирования.
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

    /** `true`, когда [load] или [reload] завершились успешно и значение не выгружено. */
    override val isLoaded: Boolean get() = loadedValue != null

    /** Возвращает текущее значение из памяти. */
    override fun get(): T = loadedValue ?: error("Configuration ${file.name} is not loaded")

    /**
     * Создаёт отсутствующий файл, дополняет существующий новыми полями, проверяет
     * значение и возвращает подробный результат синхронизации.
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

    /** Повторно загружает файл; присваивание происходит только после полной успешной проверки. */
    @Synchronized
    override fun reload(): ConfigLoadResult<T> = load()

    /** Сохраняет текущее значение из памяти. */
    @Synchronized
    override fun save() = save(get())

    /** Атомарно сохраняет [value] после декодирования и семантической проверки. */
    @Synchronized
    override fun save(value: T) {
        val encoded = normalize(codec.encode(value))
        decodeAndValidate(encoded)
        writeAtomic(encoded)
        loadedValue = value
    }

    /** Изменяет и сохраняет конфигурацию одной операцией. */
    @Synchronized
    override fun update(updater: UnaryOperator<T>): T {
        val updated = updater.apply(get())
        save(updated)
        return updated
    }

    /** Запускает валидатор для текущего значения. */
    override fun validate(): List<ConfigProblem> = validator.validate(get())

    /** Сохраняет code-first defaults и возвращает их. */
    @Synchronized
    override fun resetToDefaults(): T {
        save(defaults)
        return defaults
    }

    /** Очищает значение из памяти. Файл остаётся без изменений. */
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
