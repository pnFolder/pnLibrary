package ru.privatenull.pnlibrary.api.config

import java.util.function.UnaryOperator

/** Ошибка значения конфигурации с полным путём до параметра. */
data class ConfigurationProblem(val path: String, val message: String)

/** Платформенно-независимая проверка типизированной конфигурации. */
fun interface ConfigurationValidator<T> {
    fun validate(value: T): List<ConfigurationProblem>
}

/**
 * Общий жизненный цикл конфигурации для Bukkit, прокси и standalone-приложений.
 *
 * Формат файла и способ хранения задаются платформенным адаптером. Потребитель
 * работает только с типом [T] и одинаковыми операциями.
 *
 * Kotlin:
 * ```kotlin
 * val settings = configuration.load()
 * configuration.update { it.copy(enabled = true) }
 * configuration.close()
 * ```
 *
 * Java:
 * ```java
 * Settings settings = configuration.load();
 * configuration.save(settings);
 * configuration.close();
 * ```
 */
interface Configuration<T> : AutoCloseable {
    val isLoaded: Boolean
    fun current(): T
    fun load(): T
    fun reload(): T
    fun save()
    fun save(value: T)
    fun update(updater: UnaryOperator<T>): T
    fun validate(): List<ConfigurationProblem>
    fun reset(): T
    fun unload()
    override fun close() = unload()
}

/** Фабрика конфигураций, которую предоставляет адаптер конкретной платформы. */
interface ConfigurationFactory {
    fun <T> create(definition: ConfigurationDefinition<T>): Configuration<T>
}

/** Полное платформенно-независимое описание одной конфигурации. */
class ConfigurationDefinition<T>(
    val id: String,
    val defaults: T,
    val codec: ConfigurationCodec<T>,
    val validator: ConfigurationValidator<T>,
)

/** Преобразует типизированную модель в текст и обратно. */
interface ConfigurationCodec<T> {
    fun encode(value: T): String
    fun decode(text: String): T
}
