package org.pnfolder.pnlibrary.core.plugin.module

import org.pnfolder.pnlibrary.api.actions.Action
import org.pnfolder.pnlibrary.api.actions.DefinitionConflict
import org.pnfolder.pnlibrary.api.actions.ScenarioLookup
import org.pnfolder.pnlibrary.api.requirements.Requirement
import org.pnfolder.pnlibrary.api.requirements.expression.ExpressionFunction

/** Named actions, requirements and expression functions of one level (one module, or the whole library). */
internal class ScenarioDefinitions : ScenarioLookup {
    private class Entry<T>(val value: T, val owner: String)

    private val actions = HashMap<String, Entry<Action>>()
    private val requirements = HashMap<String, Entry<Requirement>>()
    private val functions = HashMap<String, Entry<ExpressionFunction>>()

    @Synchronized override fun action(name: String): Action? = actions[name]?.value
    @Synchronized override fun requirement(name: String): Requirement? = requirements[name]?.value
    @Synchronized override fun function(name: String): ExpressionFunction? = functions[name]?.value

    fun defineAction(name: String, value: Action, owner: String, conflict: DefinitionConflict) =
        define(actions, "action", name, value, owner, conflict)

    fun defineRequirement(name: String, value: Requirement, owner: String, conflict: DefinitionConflict) =
        define(requirements, "requirement", name, value, owner, conflict)

    fun defineFunction(name: String, value: ExpressionFunction, owner: String, conflict: DefinitionConflict) =
        define(functions, "function", name, value, owner, conflict)

    @Synchronized
    private fun <T> define(
        map: MutableMap<String, Entry<T>>, kind: String, rawName: String, value: T, owner: String, conflict: DefinitionConflict,
    ): AutoCloseable {
        val name = rawName.trim().lowercase()
        require(NAME.matches(name)) { "A $kind name may contain only letters, digits, dots, hyphens and underscores, but was '$rawName'" }
        val entry = Entry(value, owner)
        val existing = map[name]
        if (existing != null) when (conflict) {
            DefinitionConflict.KEEP -> return AutoCloseable { }
            DefinitionConflict.FAIL -> throw IllegalArgumentException("The $kind '$name' is already defined by $owner")
            DefinitionConflict.REPLACE -> require(existing.owner == owner) {
                "The $kind '$name' belongs to ${existing.owner} and cannot be replaced by $owner"
            }
        }
        map[name] = entry
        return AutoCloseable { synchronized(this) { if (map[name] === entry) map.remove(name) } }
    }

    private companion object {
        val NAME = Regex("[a-z0-9][a-z0-9_.-]{0,63}")
    }
}

/** Looks in the module's own definitions first, then in the ones shared by the whole library. */
internal class LayeredLookup(private val own: ScenarioLookup, private val shared: ScenarioLookup) : ScenarioLookup {
    override fun action(name: String) = own.action(name) ?: shared.action(name)
    override fun requirement(name: String) = own.requirement(name) ?: shared.requirement(name)
    override fun function(name: String) = own.function(name) ?: shared.function(name)
}
