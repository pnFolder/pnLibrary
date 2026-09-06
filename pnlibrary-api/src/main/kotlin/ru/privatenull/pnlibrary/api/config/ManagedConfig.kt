package ru.privatenull.pnlibrary.api.config

import java.io.File
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

