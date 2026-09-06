package ru.privatenull.pnlibrary.api.config

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

