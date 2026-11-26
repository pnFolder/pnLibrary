package org.pnfolder.pnlibrary.api.updates;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.dependencies.external.ExternalPluginDependency;
import org.pnfolder.pnlibrary.api.updates.product.ProductId;
import org.pnfolder.pnlibrary.api.updates.product.UpdateChannel;
import org.pnfolder.pnlibrary.api.updates.release.ArtifactDescriptor;
import org.pnfolder.pnlibrary.api.updates.release.ProductChange;
import org.pnfolder.pnlibrary.api.updates.release.ProductDependency;
import org.pnfolder.pnlibrary.api.updates.release.ProductRelease;
import org.pnfolder.pnlibrary.api.updates.release.UpdatePlan;
import org.pnfolder.pnlibrary.api.updates.state.BlockedReason;
import org.pnfolder.pnlibrary.api.updates.state.UpdatePlanSnapshot;
import org.pnfolder.pnlibrary.api.updates.state.UpdateState;
import org.pnfolder.pnlibrary.api.version.ApiVersionRange;
import org.pnfolder.pnlibrary.api.version.SemanticVersion;

class UpdatePlanSnapshotTest {
    @Test
    void snapshotDetachesTheGraphAndProtectsNestedCollections() {
        ProductId product = ProductId.of("example");
        SemanticVersion version = SemanticVersion.parse("2.0.0-beta.1");
        List<ProductDependency> dependencies = new ArrayList<ProductDependency>(
            Collections.singletonList(new ProductDependency(ProductId.of("dependency"), version)));
        List<ArtifactDescriptor> artifacts = new ArrayList<ArtifactDescriptor>();
        List<ExternalPluginDependency> external = new ArrayList<ExternalPluginDependency>();
        ProductRelease release = new ProductRelease(product, version, UpdateChannel.BETA, new ApiVersionRange(1, 1),
            null, dependencies, null, artifacts, external, null, null, "");
        List<ProductChange> changes = new ArrayList<ProductChange>(
            Collections.singletonList(new ProductChange(product, null, version)));
        List<ProductRelease> selected = new ArrayList<ProductRelease>(Collections.singletonList(release));
        UpdatePlanSnapshot snapshot = new UpdatePlanSnapshot(UUID.randomUUID(), 1, UpdateState.UPDATE_AVAILABLE,
            new UpdatePlan(1, changes, selected), Collections.<BlockedReason>emptyList(), null);
        dependencies.clear();
        changes.clear();
        selected.clear();

        UpdatePlan captured = snapshot.getPlan();
        assertNotNull(captured);
        assertEquals(1, captured.getChanges().size());
        assertEquals(1, captured.getSelected().get(0).getDependencies().size());
        ProductRelease frozenRelease = captured.getSelected().get(0);
        List<List<?>> lists = Arrays.<List<?>>asList(captured.getChanges(), captured.getSelected(),
            frozenRelease.getDependencies(), frozenRelease.getArtifacts(),
            frozenRelease.getExternalPluginDependencies(), version.getPrerelease());
        for (List<?> list : lists) {
            assertThrows(UnsupportedOperationException.class, list::clear);
        }
        assertEquals("2.0.0-beta.1", frozenRelease.getVersion().toString());
    }
}
