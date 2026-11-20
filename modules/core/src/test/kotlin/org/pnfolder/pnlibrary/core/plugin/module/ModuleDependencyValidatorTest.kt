package org.pnfolder.pnlibrary.core.plugin.module

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.pnfolder.pnlibrary.api.dependencies.Dependencies
import org.pnfolder.pnlibrary.spi.platform.PlatformOwnership

class ModuleDependencyValidatorTest {
    private fun validator(installed: Map<String, String>, downloads: Boolean = false): ModuleDependencyValidator {
        val platform = mock(PlatformOwnership::class.java)
        `when`(platform.installedPlugins()).thenReturn(installed)
        return ModuleDependencyValidator(platform) { downloads }
    }

    private val vault = Dependencies.plugin("Vault", "1.7.0", "https://example.org/vault")

    @Test
    fun `an installed plugin of a high enough version passes, names ignore case`() {
        assertDoesNotThrow { validator(mapOf("vault" to "1.7.3")).validate(listOf(vault), emptyMap()) }
    }

    @Test
    fun `a missing plugin names its download page`() {
        val error = assertThrows(IllegalArgumentException::class.java) { validator(emptyMap()).validate(listOf(vault), emptyMap()) }

        assertTrue(error.message!!.contains("Vault"))
        assertTrue(error.message!!.contains("https://example.org/vault"))
    }

    @Test
    fun `a too old plugin is reported with both versions`() {
        val error = assertThrows(IllegalArgumentException::class.java) { validator(mapOf("Vault" to "1.5.0")).validate(listOf(vault), emptyMap()) }

        assertTrue(error.message!!.contains("1.5.0"))
        assertTrue(error.message!!.contains("1.7.0"))
    }

    @Test
    fun `an optional plugin never blocks`() {
        val optional = Dependencies.plugin("Vault", "1.7.0", "https://example.org/vault", required = false)

        assertDoesNotThrow { validator(emptyMap()).validate(listOf(optional), emptyMap()) }
    }

    @Test
    fun `a missing plugin that can be downloaded automatically does not block`() {
        val automatic = Dependencies.plugin("Vault", "1.7.0", "https://example.org/Vault.jar", "a".repeat(64))

        assertDoesNotThrow { validator(emptyMap(), downloads = true).validate(listOf(automatic), emptyMap()) }
        assertThrows(IllegalArgumentException::class.java) { validator(emptyMap(), downloads = false).validate(listOf(automatic), emptyMap()) }
    }
}
