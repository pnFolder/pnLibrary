package org.pnfolder.pnlibrary.core.updates.policy

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.pnfolder.pnlibrary.api.updates.*
import org.pnfolder.pnlibrary.api.updates.release.*
import org.pnfolder.pnlibrary.api.version.SemanticVersion
import java.util.UUID

class UpdateAutomaticPolicyTest {
    @Test
    fun `generic downloads permission does not opt the library into automatic replacement`() {
        val config = UpdateConfiguration(downloads = UpdateConfiguration.Downloads(automatic = true))
        assertFalse(UpdateAutomaticPolicy(config) { emptyList() }.allows(plan("pnlibrary")))
        assertTrue(UpdateAutomaticPolicy(config.copy(library = UpdateConfiguration.LibraryPolicy(automaticDownload = true))) {
            emptyList()
        }.allows(plan("pnlibrary")))
    }

    @Test
    fun `every member of an atomic plan needs automatic permission`() {
        val config = UpdateConfiguration(library = UpdateConfiguration.LibraryPolicy(automaticDownload = true))
        val combined = plan("pnlibrary", "managed-module")
        assertFalse(UpdateAutomaticPolicy(config) { emptyList() }.allows(combined))
        val allowed = config.copy(pluginAutomaticDownload = true)
        assertTrue(UpdateAutomaticPolicy(allowed) { emptyList() }.allows(combined))
        assertFalse(UpdateAutomaticPolicy(allowed.copy(pluginUpdatesEnabled = false)) { emptyList() }.allows(combined))
    }

    @Test
    fun `an explicitly disabled product cannot inherit a global automatic permission`() {
        val config = UpdateConfiguration(pluginAutomaticDownload = true, ignoredPlugins = setOf("managed-module"))
        assertFalse(UpdateAutomaticPolicy(config) { emptyList() }.allows(plan("managed-module")))
    }

    private fun plan(vararg products: String) = UpdatePlanSnapshot(UUID.randomUUID(), 1, UpdateState.UPDATE_AVAILABLE,
        UpdatePlan(1, products.map { ProductChange(ProductId.of(it), SemanticVersion.parse("1.0.0"), SemanticVersion.parse("2.0.0")) }, emptyList()), emptyList(), null)
}
