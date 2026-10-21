package org.pnfolder.pnlibrary.api.updates

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.pnfolder.pnlibrary.api.dependencies.ExternalPluginDependency
import org.pnfolder.pnlibrary.api.updates.release.ArtifactDescriptor
import org.pnfolder.pnlibrary.api.updates.release.ProductChange
import org.pnfolder.pnlibrary.api.updates.release.ProductDependency
import org.pnfolder.pnlibrary.api.updates.release.ProductRelease
import org.pnfolder.pnlibrary.api.updates.release.UpdatePlan
import org.pnfolder.pnlibrary.api.version.ApiVersionRange
import org.pnfolder.pnlibrary.api.version.SemanticVersion
import java.util.UUID

class UpdatePlanSnapshotTest {
    @Test
    fun `snapshot detaches the graph and protects nested collections`() {
        val product = ProductId.of("example")
        val version = SemanticVersion.parse("2.0.0-beta.1")
        val dependencies = mutableListOf(ProductDependency(ProductId.of("dependency"), version))
        val artifacts = mutableListOf<ArtifactDescriptor>()
        val external = mutableListOf<ExternalPluginDependency>()
        val release = ProductRelease(
            product, version, UpdateChannel.BETA, ApiVersionRange(1, 1),
            dependencies = dependencies, artifacts = artifacts, externalPluginDependencies = external,
        )
        val changes = mutableListOf(ProductChange(product, null, version))
        val selected = mutableListOf(release)
        val snapshot = UpdatePlanSnapshot(
            UUID.randomUUID(), 1, UpdateState.UPDATE_AVAILABLE,
            UpdatePlan(1, changes, selected), emptyList(), null,
        )
        dependencies.clear()
        changes.clear()
        selected.clear()
        val captured = requireNotNull(snapshot.plan)
        assertEquals(1, captured.changes.size)
        assertEquals(1, captured.selected.single().dependencies.size)
        val frozenRelease = captured.selected.single()
        for (list in listOf(captured.changes, captured.selected, frozenRelease.dependencies,
            frozenRelease.artifacts, frozenRelease.externalPluginDependencies, version.prerelease)) {
            assertThrows(UnsupportedOperationException::class.java) { (list as MutableList<*>).clear() }
        }
        assertEquals("2.0.0-beta.1", frozenRelease.version.toString())
    }
}
