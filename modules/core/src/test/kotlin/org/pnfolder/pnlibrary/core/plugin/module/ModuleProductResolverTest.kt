package org.pnfolder.pnlibrary.core.plugin.module

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.pnfolder.pnlibrary.api.dependencies.Dependencies
import org.pnfolder.pnlibrary.api.plugin.ModuleId
import org.pnfolder.pnlibrary.spi.platform.PlatformOwnership

class ModuleProductResolverTest {
    private val owner = Any()
    private val id = ModuleId.of("shop")

    private fun resolver(version: String?): ModuleProductResolver {
        val platform = mock(PlatformOwnership::class.java)
        `when`(platform.ownerDetails(owner)).thenReturn(if (version == null) emptyMap() else mapOf("version" to version))
        return ModuleProductResolver(platform)
    }

    @Test
    fun `a module with no updates, product or dependencies has no descriptor`() {
        assertNull(resolver("1.0.0").resolve(owner, id, ModuleDefinitionBuilder()))
    }

    @Test
    fun `dependencies alone give a descriptor with the plugin's version`() {
        val definition = ModuleDefinitionBuilder().apply { depends(Dependencies.plugin("Vault", "1.7.0", "https://example.org")) }

        val descriptor = resolver("2.3.4").resolve(owner, id, definition)!!

        assertEquals("shop", descriptor.id.value)
        assertEquals("2.3.4", descriptor.version.toString())
    }

    @Test
    fun `the version of the definition is used when the platform has none`() {
        val definition = ModuleDefinitionBuilder().apply {
            metadataVersion = "0.9.0"
            depends(Dependencies.plugin("Vault", "1.7.0", "https://example.org"))
        }

        assertEquals("0.9.0", resolver(null).resolve(owner, id, definition)!!.version.toString())
    }

    @Test
    fun `no version anywhere is an error that names the module`() {
        val definition = ModuleDefinitionBuilder().apply { depends(Dependencies.plugin("Vault", "1.7.0", "https://example.org")) }

        val error = assertThrows(IllegalStateException::class.java) { resolver(null).resolve(owner, id, definition) }

        assertEquals(true, error.message!!.contains("shop"))
    }
}
