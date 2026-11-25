package org.pnfolder.pnlibrary.core.placeholders

import org.pnfolder.pnlibrary.api.placeholders.info.ViewerInfo
import org.pnfolder.pnlibrary.api.registry.Visibility
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/** A format: takes a value of [input], the words after its colon and the viewer, gives the next value. An empty [owner] is the library. */
internal class FormatDef(
    val owner: String,
    val name: String,
    val input: Class<*>,
    val apply: (Any, List<String>, ViewerInfo) -> Any?,
)

/**
 * The formats of the library. The nearest format of a name wins: the own module, the own plugin, other plugins,
 * and last the built-in ones. [onConflict] hears when two modules bring a format of the same name for the same type.
 */
internal class FormatRegistry(private val onConflict: (String) -> Unit = {}) {
    private val byName = ConcurrentHashMap<String, CopyOnWriteArrayList<FormatDef>>()

    fun add(format: FormatDef) {
        val list = byName.computeIfAbsent(format.name.lowercase()) { CopyOnWriteArrayList() }
        if (format.owner.isNotEmpty()) {
            list.firstOrNull { it.owner.isNotEmpty() && it.owner != format.owner && it.input == format.input && reachable(it, format) }?.let {
                onConflict("Format '${format.name}' of ${format.owner} and of ${it.owner} fit the same value; the nearer one is used")
            }
        }
        list.add(format)
    }

    private fun reachable(a: FormatDef, b: FormatDef): Boolean =
        true

    fun removeOwner(owner: String) {
        byName.values.forEach { list -> list.removeIf { it.owner == owner } }
    }

    /** The format called [name] that [consumer] may use and that accepts [value]; the nearest one. */
    fun find(name: String, value: Any, consumer: String?, written: String? = null): FormatDef? = byName[name.lowercase()]
        ?.filter { it.input.isInstance(value) && ownerFits(written, it.owner, consumer) }
        ?.mapNotNull { format -> (if (format.owner.isEmpty()) BUILT_IN else nearness(format.owner, Visibility.ALL, consumer))?.let { it to format } }
        ?.minByOrNull { it.first }?.second

    fun all(): List<FormatDef> = byName.values.flatten()

    fun names(): List<String> = byName.filterValues { it.isNotEmpty() }.keys.sorted()

    private companion object {
        const val BUILT_IN = 3
    }
}
