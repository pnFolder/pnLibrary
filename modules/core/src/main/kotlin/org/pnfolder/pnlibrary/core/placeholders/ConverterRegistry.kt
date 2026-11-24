package org.pnfolder.pnlibrary.core.placeholders

import org.pnfolder.pnlibrary.api.placeholders.ArgumentConverter
import org.pnfolder.pnlibrary.api.placeholders.Visibility
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/** The converters of argument types, by owner; the one nearest to the asking module is used. */
internal class ConverterRegistry(private val report: (String) -> Unit) {
    private class Entry(val owner: String, val visibility: Visibility, val converter: ArgumentConverter<Any>)

    private val byType = ConcurrentHashMap<Class<*>, CopyOnWriteArrayList<Entry>>()

    /** Registers [converter] for [type] and returns what takes it back. */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> add(owner: String, type: Class<T>, converter: ArgumentConverter<T>): AutoCloseable {
        val entry = Entry(owner, Visibility.ALL, converter as ArgumentConverter<Any>)
        val list = byType.computeIfAbsent(type) { CopyOnWriteArrayList() }
        list.firstOrNull { it.owner != owner }
            ?.let { report("$owner and ${it.owner} both read ${type.simpleName}; the nearer one is used") }
        list.add(entry)
        return AutoCloseable { list.remove(entry) }
    }

    /** The converter of [type] nearest to [consumer], or `null`. */
    fun find(type: Class<*>, consumer: String?): ArgumentConverter<Any>? = byType[type].orEmpty()
        .mapNotNull { entry -> nearness(entry.owner, entry.visibility, consumer)?.let { it to entry.converter } }
        .minByOrNull { it.first }?.second

    fun removeOwner(owner: String) {
        byType.values.forEach { list -> list.removeIf { it.owner == owner } }
    }
}
