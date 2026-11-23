package org.pnfolder.pnlibrary.core.plugin.module

import org.pnfolder.pnlibrary.api.placeholders.Visibility
import org.pnfolder.pnlibrary.api.scenario.actions.Action
import org.pnfolder.pnlibrary.api.scenario.actions.DefinitionConflict
import org.pnfolder.pnlibrary.api.scenario.actions.ScenarioLookup
import org.pnfolder.pnlibrary.api.scenario.expression.ExpressionFunction
import org.pnfolder.pnlibrary.api.scenario.requirements.Requirement
import org.pnfolder.pnlibrary.core.placeholders.nearness
import org.pnfolder.pnlibrary.core.placeholders.ownerFits
import org.pnfolder.pnlibrary.core.scenario.ScenarioDefinitionInfo

/**
 * Named actions, requirements and expression functions of the whole library. Every definition has an owner
 * (`plugin/module`) and a [Visibility]; [view] gives the lookup of one module, which finds the nearest one of a name.
 * A name that two modules define while they can see each other is reported to the `warn` of the later one.
 */
internal class ScenarioDefinitions {
    private class Entry<T>(val value: T, val owner: String, val visibility: Visibility)

    private val actions = HashMap<String, MutableList<Entry<Action>>>()
    private val requirements = HashMap<String, MutableList<Entry<Requirement>>>()
    private val functions = HashMap<String, MutableList<Entry<ExpressionFunction>>>()

    fun defineAction(
        name: String,
        value: Action,
        owner: String,
        conflict: DefinitionConflict,
        visibility: Visibility = Visibility.ALL,
        warn: (String) -> Unit = {},
    ) =
        define(actions, "action", name, value, owner, conflict, visibility, warn)

    fun defineRequirement(
        name: String,
        value: Requirement,
        owner: String,
        conflict: DefinitionConflict,
        visibility: Visibility = Visibility.ALL,
        warn: (String) -> Unit = {},
    ) =
        define(requirements, "requirement", name, value, owner, conflict, visibility, warn)

    fun defineFunction(
        name: String,
        value: ExpressionFunction,
        owner: String,
        conflict: DefinitionConflict,
        visibility: Visibility = Visibility.ALL,
        warn: (String) -> Unit = {},
    ) =
        define(functions, "function", name, value, owner, conflict, visibility, warn)

    /** Removes everything [owner] defined; called when its module closes. */
    @Synchronized
    fun removeOwner(owner: String) {
        listOf(actions, requirements, functions).forEach { map -> map.values.forEach { list -> list.removeIf { it.owner == owner } } }
    }

    /** What the module [consumer] (`plugin/module`) can see, nearest first. */
    fun view(consumer: String): ScenarioLookup = object : ScenarioLookup {
        override fun action(name: String) = find(actions, name, consumer)
        override fun requirement(name: String) = find(requirements, name, consumer)
        override fun function(name: String) = find(functions, name, consumer)
    }

    /** The overview for commands and menus: every definition with its owner and who may use it. */
    @Synchronized
    fun infos(): List<ScenarioDefinitionInfo> = listOf("action" to actions, "requirement" to requirements, "function" to functions)
        .flatMap { (kind, map) ->
            map.flatMap { (name, entries) -> entries.map { ScenarioDefinitionInfo(kind, name, it.owner, it.visibility.name.lowercase()) } }
        }.sortedWith(compareBy({ it.kind }, { it.name }, { it.owner }))

    @Synchronized
    private fun <T> find(map: Map<String, List<Entry<T>>>, written: String, consumer: String): T? {
        val at = written.indexOf('@')
        val owner = if (at > 0) written.substring(0, at).trim().lowercase() else null
        val name = (if (at > 0) written.substring(at + 1) else written).trim().lowercase()
        return map[name].orEmpty()
            .filter { ownerFits(owner, it.owner, consumer) }
            .mapNotNull { entry -> nearness(entry.owner, entry.visibility, consumer)?.let { it to entry.value } }
            .minByOrNull { it.first }?.second
    }

    @Synchronized
    private fun <T> define(
        map: MutableMap<String, MutableList<Entry<T>>>,
        kind: String,
        rawName: String,
        value: T,
        owner: String,
        conflict: DefinitionConflict,
        visibility: Visibility,
        warn: (String) -> Unit,
    ): AutoCloseable {
        val name = rawName.trim().lowercase()
        require(NAME.matches(name)) { "A $kind name may contain only letters, digits, dots, hyphens and underscores, but was '$rawName'" }
        val list = map.getOrPut(name) { ArrayList() }
        val entry = Entry(value, owner, visibility)
        val mine = list.firstOrNull { it.owner == owner }
        if (mine != null) when (conflict) {
            DefinitionConflict.KEEP -> return AutoCloseable { }
            DefinitionConflict.FAIL -> throw IllegalArgumentException("The $kind '$name' is already defined by $owner")
            DefinitionConflict.REPLACE -> list.remove(mine)
        }
        list.firstOrNull {
            it.owner != owner && (nearness(it.owner, it.visibility, owner) != null || nearness(owner, visibility, it.owner) != null)
        }?.let { warn("The $kind '$name' is defined by $owner and by ${it.owner}; the nearer one is used") }
        list += entry
        return AutoCloseable { synchronized(this) { list.remove(entry) } }
    }

    private companion object {
        val NAME = Regex("[a-z0-9][a-z0-9_.-]{0,63}")
    }
}
