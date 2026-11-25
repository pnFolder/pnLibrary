package org.pnfolder.pnlibrary.core.actions

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.pnfolder.pnlibrary.api.registry.Visibility
import org.pnfolder.pnlibrary.api.scenario.actions.Action
import org.pnfolder.pnlibrary.api.scenario.actions.DefinitionConflict
import org.pnfolder.pnlibrary.core.plugin.module.ScenarioDefinitions

class ActionRegistryTest {
    private val definitions = ScenarioDefinitions()
    private val action = Action { error("not run") }

    @Test
    fun `a module sees its own and public actions, not the private actions of another plugin`() {
        definitions.defineAction("give-kit", action, "shop/main", DefinitionConflict.FAIL, Visibility.ALL)
        definitions.defineAction("secret", action, "shop/main", DefinitionConflict.FAIL, Visibility.MODULE)

        val own = definitions.actionEntries("shop/main").map { it.key }
        val other = definitions.actionEntries("clans/main")

        assertEquals(listOf("give-kit", "secret"), own)
        assertEquals(listOf("give-kit"), other.map { it.key })
        assertEquals("shop/main", other.single().owner)
    }

    @Test
    fun `closing a definition removes it from the list`() {
        val handle = definitions.defineAction("temp", action, "shop/main", DefinitionConflict.FAIL)
        handle.close()

        assertEquals(emptyList<String>(), definitions.actionEntries("shop/main").map { it.key })
    }
}
