package ru.privatenull.pnlibrary.bukkit.config

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.logging.Level
import java.util.logging.Logger
import java.util.function.UnaryOperator

/** Адаптер между типизированной code-first моделью [T] и текстом YAML. */
interface ConfigCodec<T> {
    /** Сериализует [value], включая default-поля и комментарии документации. */
    fun encode(value: T): String

    /** Десериализует YAML. Ошибка по возможности должна содержать путь неправильного поля. */
    fun decode(yaml: String): T
}

/** Выполняет дополнительные проверки после успешной десериализации YAML. */
fun interface ConfigValueValidator<T> {
    /** Возвращает найденные проблемы; пустой список означает корректное значение. */
    fun validate(value: T): List<ConfigProblem>
}

/** Одно типизированное условие корректности конфигурации. */
fun interface ConfigCondition<T> {
    /** Возвращает `true`, если проверяемое значение корректно. */
    fun test(value: T): Boolean
}

/**
 * Собирает несколько понятных правил проверки без ручного создания списков.
 *
 * ```kotlin
 * val validator = ConfigValidatorBuilder<Settings>()
 *     .require("database.port", "должен быть от 1 до 65535") { it.database.port in 1..65535 }
 *     .build()
 * ```
 */
class ConfigValidatorBuilder<T> {
    private val rules = mutableListOf<Rule<T>>()

    /** Добавляет правило для YAML-пути [path]. */
    fun require(path: String, message: String, condition: ConfigCondition<T>) = apply {
        require(path.isNotBlank()) { "Configuration path cannot be blank" }
        rules += Rule(path, message, condition)
    }

    /** Создаёт неизменяемый валидатор из добавленных правил. */
    fun build(): ConfigValueValidator<T> {
        val snapshot = rules.toList()
        return ConfigValueValidator { value ->
            snapshot.mapNotNull { rule ->
                if (rule.condition.test(value)) null else ConfigProblem(rule.path, rule.message)
            }
        }
    }

    private data class Rule<T>(val path: String, val message: String, val condition: ConfigCondition<T>)
}

/** Одна проблема конфигурации, связанная с полным YAML-путём [path]. */
data class ConfigProblem(val path: String, val message: String)

/** Результат загрузки и синхронизации одного code-first файла. */
data class ConfigLoadResult<T>(
    val value: T,
    val addedPaths: List<String>,
    val backup: File?,
)

/** Ошибка семантической проверки типизированной конфигурации. */
class ConfigValidationException(val problems: List<ConfigProblem>) : IllegalArgumentException(
    problems.joinToString(prefix = "Invalid configuration: ", separator = "; ") { "${it.path}: ${it.message}" }
)

/**
 * Высокоуровневый жизненный цикл одной типизированной конфигурации.
 * `unload()` очищает значение только из памяти и никогда не удаляет YAML-файл.
 */
interface ManagedConfig<T> : AutoCloseable {
    /** Загружена ли конфигурация в память. */
    val isLoaded: Boolean

    /** Kotlin-свойство с текущим значением; эквивалент [get]. */
    val value: T get() = get()

    /** Возвращает загруженное значение или сообщает, что сначала нужен [load]. */
    fun get(): T

    /** Загружает файл, синхронизирует defaults и сохраняет значение в памяти. */
    fun load(): ConfigLoadResult<T>

    /** Повторно читает файл. При ошибке прежнее рабочее значение остаётся в памяти. */
    fun reload(): ConfigLoadResult<T>

    /** Загружает файл и сразу возвращает только типизированное значение. */
    fun loadValue(): T = load().value

    /** Перезагружает файл и сразу возвращает только типизированное значение. */
    fun reloadValue(): T = reload().value

    /** Сохраняет текущее значение из памяти. */
    fun save()

    /** Проверяет, сохраняет [value] и делает его текущим значением в памяти. */
    fun save(value: T)

    /** Атомарно изменяет текущее значение, проверяет его и сохраняет на диск. */
    fun update(updater: UnaryOperator<T>): T

    /** Проверяет текущее значение без записи файла. */
    fun validate(): List<ConfigProblem>

    /** Возвращает файл к defaults из кода и делает их текущим значением. */
    fun resetToDefaults(): T

    /** Удаляет загруженное значение из памяти, сохраняя файл на диске. */
    fun unload()

    /** Эквивалент [unload], позволяющий использовать `use`/try-with-resources. */
    override fun close() = unload()
}

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
        file.absoluteFile.parentFile?.mkdirs()
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
        val target = File(file.parentFile, "${file.name}.before-sync.bak")
        if (!target.exists()) target.writeText(content)
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
}

/**
 * Группа конфигураций одного плагина с единым жизненным циклом.
 * Handles остаются типизированными у владельца, а группа только вызывает их
 * общие операции.
 *
 * ```kotlin
 * val configs = ConfigGroup().add(settings).add(messages)
 * configs.loadAll()
 * ```
 */
class ConfigGroup : AutoCloseable {
    private val configs = linkedSetOf<ManagedConfig<*>>()

    /** Регистрирует [config] и возвращает группу для fluent-вызовов. */
    fun add(config: ManagedConfig<*>) = apply { configs += config }

    /** Загружает все файлы в порядке регистрации. */
    fun loadAll() { configs.forEach { it.load() } }

    /** Перезагружает все файлы; каждый handle сохраняет старое значение при своей ошибке. */
    fun reloadAll() { configs.forEach { it.reload() } }

    /** Сохраняет все загруженные конфигурации. */
    fun saveAll() { configs.filter { it.isLoaded }.forEach { it.save() } }

    /** Выгружает все значения из памяти, не удаляя файлы. */
    fun unloadAll() { configs.forEach { it.unload() } }

    /** Количество зарегистрированных handles. */
    fun size(): Int = configs.size

    /** Выгружает всю группу. */
    override fun close() = unloadAll()
}

/** Рекурсивное слияние YAML с сохранением комментариев для [CodeFirstYaml]. */
internal object YamlDefaultsMerger {
    data class Result(val content: String, val addedPaths: List<String>) {
        val changed: Boolean get() = addedPaths.isNotEmpty()
    }

    fun merge(existing: String, defaults: String): Result {
        val target = existing.lines().dropLastWhile(String::isEmpty).toMutableList()
        val source = defaults.lines().dropLastWhile(String::isEmpty)
        val added = mutableListOf<String>()
        collect(source, 0, source.size, 0, emptyList()).forEach { candidate ->
            if (find(target, candidate.path) != null) return@forEach
            val parent = candidate.path.dropLast(1)
            val insertion = if (parent.isEmpty()) target.size else find(target, parent)?.end ?: return@forEach
            val block = source.subList(candidate.start, candidate.end)
            if (insertion == target.size && target.lastOrNull()?.isNotBlank() == true) target.add("")
            val index = if (insertion == target.size - 1 && target.lastOrNull()?.isEmpty() == true) target.size else insertion
            target.addAll(index, block)
            added += candidate.path.joinToString(".")
        }
        return Result(target.joinToString("\n").trimEnd() + "\n", added)
    }

    fun paths(yaml: String): List<String> = collect(yaml.lines(), 0, yaml.lines().size, 0, emptyList())
        .map { it.path.joinToString(".") }

    private fun find(lines: List<String>, path: List<String>): Block? {
        var start = 0; var end = lines.size; var indent = 0; var found: Block? = null
        path.forEach { key ->
            found = blocks(lines, start, end, indent).firstOrNull { it.key == key } ?: return null
            start = found!!.keyLine + 1; end = found!!.end; indent += 2
        }
        return found
    }

    private fun collect(lines: List<String>, start: Int, end: Int, indent: Int, parent: List<String>): List<Block> {
        val direct = blocks(lines, start, end, indent)
        return direct.flatMap { block ->
            val pathBlock = block.copy(path = parent + block.key)
            listOf(pathBlock) + collect(lines, block.keyLine + 1, block.end, indent + 2, pathBlock.path)
        }
    }

    private fun blocks(lines: List<String>, start: Int, end: Int, indent: Int): List<Block> {
        val keys = (start until end).mapNotNull { index -> key(lines[index], indent)?.let { index to it } }
        return keys.mapIndexed { position, (keyLine, key) ->
            val decoratedStart = decoratedStart(lines, keyLine, start)
            val next = keys.getOrNull(position + 1)?.first?.let { decoratedStart(lines, it, keyLine + 1) } ?: end
            Block(key, decoratedStart, keyLine, next, emptyList())
        }
    }

    private fun decoratedStart(lines: List<String>, keyLine: Int, lowerBound: Int): Int {
        var result = keyLine
        while (result > lowerBound) {
            val previous = lines[result - 1].trim()
            if (previous.isEmpty() || previous.startsWith('#')) result-- else break
        }
        return result
    }

    private fun key(line: String, indent: Int): String? {
        if (line.takeWhile { it == ' ' }.length != indent) return null
        val trimmed = line.trim()
        if (trimmed.isEmpty() || trimmed.startsWith('#') || trimmed.startsWith('-')) return null
        val colon = trimmed.indexOf(':')
        if (colon <= 0) return null
        return trimmed.substring(0, colon).trim().trim('"', '\'').takeIf { it.isNotEmpty() }
    }

    private data class Block(
        val key: String,
        val start: Int,
        val keyLine: Int,
        val end: Int,
        val path: List<String>,
    )
}
