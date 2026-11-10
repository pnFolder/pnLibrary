package org.pnfolder.pnlibrary.core.updates.policy

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.pnfolder.pnlibrary.api.updates.*
import org.pnfolder.pnlibrary.api.updates.release.*
import org.pnfolder.pnlibrary.api.version.SemanticVersion
import java.nio.file.Path
import java.util.UUID

class UpdateAutomaticPolicyTest {
    @TempDir
    lateinit var directory: Path

    private fun overrides() = UpdatePolicyOverrides(directory.resolve("settings.json"))

    @Test
    fun `plugin automatic download does not opt the library into automatic replacement`() {
        val config = UpdateConfiguration(pluginAutomaticDownload = true, library = UpdateConfiguration.LibraryPolicy(automaticDownload = false))
        assertFalse(UpdateAutomaticPolicy(config) { emptyList() }.allows(plan("pnlibrary")))
        assertTrue(UpdateAutomaticPolicy(config.copy(library = UpdateConfiguration.LibraryPolicy(automaticDownload = true))) {
            emptyList()
        }.allows(plan("pnlibrary")))
    }

    @Test
    fun `every member of an atomic plan needs automatic permission`() {
        val config = UpdateConfiguration(pluginAutomaticDownload = false)
        val combined = plan("pnlibrary", "managed-module")
        assertFalse(UpdateAutomaticPolicy(config) { emptyList() }.allows(combined))
        assertTrue(UpdateAutomaticPolicy(config.copy(pluginAutomaticDownload = true)) { emptyList() }.allows(combined))
    }

    @Test
    fun `plugins update automatically unless an administrator switched it off`() {
        assertTrue(UpdateAutomaticPolicy(UpdateConfiguration()) { emptyList() }.allows(plan("managed-module")))
    }

    @Test
    fun `an explicitly disabled product cannot inherit a global automatic permission`() {
        val overrides = overrides().apply { setAutomatic("managed-module", false) }
        assertFalse(UpdateAutomaticPolicy(UpdateConfiguration(pluginAutomaticDownload = true), overrides) { emptyList() }.allows(plan("managed-module")))
    }

    @Test
    fun `switching all plugins off drops single choices and keeps the library choice`() {
        val overrides = overrides().apply {
            setAutomatic("managed-module", true)
            setAutomatic("pnlibrary", true)
            setAllPlugins(false, "pnlibrary")
        }
        val policy = UpdateAutomaticPolicy(UpdateConfiguration(), overrides) { emptyList() }
        assertFalse(policy.allows(plan("managed-module")))
        assertTrue(policy.allows(plan("pnlibrary")))
        assertEquals(false, overrides.allPlugins())
    }

    private fun plan(vararg products: String) = UpdatePlanSnapshot(UUID.randomUUID(), 1, UpdateState.UPDATE_AVAILABLE,
        UpdatePlan(1, products.map { ProductChange(ProductId.of(it), SemanticVersion.parse("1.0.0"), SemanticVersion.parse("2.0.0")) },
            emptyList()), emptyList(), null)
}
