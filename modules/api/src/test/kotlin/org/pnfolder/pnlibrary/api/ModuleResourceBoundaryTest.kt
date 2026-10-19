package org.pnfolder.pnlibrary.api

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.pnfolder.pnlibrary.api.plugin.ModuleContext
import org.pnfolder.pnlibrary.api.runtime.PnLibrary
import org.pnfolder.pnlibrary.api.tasks.TaskScope

class ModuleResourceBoundaryTest {
    @Test
    fun `consumer facade does not expose owner based command or task factories`() {
        val names = PnLibrary::class.java.methods.map { it.name }.toSet()
        listOf("getTasks", "getCommands", "runtimeTaskScope", "registerRuntimeCommand")
            .forEach { assertFalse(it in names, "Public facade must not expose $it") }
        assertEquals(TaskScope::class.java, ModuleContext::class.java.getMethod("getTasks").returnType)
    }
}
