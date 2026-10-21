package org.pnfolder.pnlibrary.core.runtime

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.pnfolder.pnlibrary.api.runtime.PnLibrary
import org.pnfolder.pnlibrary.core.runtime.lifecycle.registerRuntimeCommand
import org.pnfolder.pnlibrary.core.runtime.lifecycle.runtimeTaskScope
import java.lang.reflect.Proxy

class RuntimeResourcesTest {
    private val foreignLibrary = Proxy.newProxyInstance(
        PnLibrary::class.java.classLoader,
        arrayOf(PnLibrary::class.java),
    ) { _, _, _ -> throw UnsupportedOperationException() } as PnLibrary

    @Test
    fun `runtime bridges reject a library that is not the core runtime`() {
        val command = assertThrows<IllegalStateException> { foreignLibrary.registerRuntimeCommand("demo") {} }
        val tasks = assertThrows<IllegalStateException> { foreignLibrary.runtimeTaskScope() }

        assertTrue(command.message.orEmpty().contains("core runtime"))
        assertTrue(tasks.message.orEmpty().contains("core runtime"))
    }
}
