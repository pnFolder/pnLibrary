package org.pnfolder.pnlibrary.api.updates;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.dependencies.PluginDependency;
import org.pnfolder.pnlibrary.api.updates.product.ProductDescriptor;
import org.pnfolder.pnlibrary.api.updates.product.UpdateChannel;
import org.pnfolder.pnlibrary.api.updates.release.ReleaseSummary;
import org.pnfolder.pnlibrary.api.updates.request.PluginUpdateRequest;
import org.pnfolder.pnlibrary.api.updates.state.UpdateSnapshot;
import org.pnfolder.pnlibrary.api.updates.state.UpdateState;
import org.pnfolder.pnlibrary.api.version.ApiVersionRange;

class UpdateApiConsistencyTest {
    @Test
    void releaseHistoryIsAnImmutableSnapshotOfCallerData() {
        ReleaseSummary release = new ReleaseSummary("2.0.0", UpdateChannel.STABLE);
        List<ReleaseSummary> source = new ArrayList<>(Collections.singletonList(release));
        UpdateSnapshot snapshot = new UpdateSnapshot("product", "1.0.0", "2.0.0", UpdateChannel.STABLE,
            UpdateState.UPDATE_AVAILABLE, 17, 17, false, null, null, null, (ApiVersionRange) null, source,
            Collections.emptyList(), null, null);
        source.clear();
        assertEquals(Collections.singletonList(release), snapshot.getAvailableReleases());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.getAvailableReleases().clear());
    }

    @Test
    void registryVocabularyReturnsImmutableSnapshots() {
        final UpdateSnapshot snapshot = new UpdateSnapshot("product", "1.0.0", null, UpdateChannel.STABLE,
            UpdateState.UP_TO_DATE, 17, 17, false, null, null);
        final UpdateRegistration registration = new UpdateRegistration() {
            @Override
            public boolean isClosed() {
                return false;
            }

            @Override
            public String getRepository() {
                return "owner/repository";
            }

            @Override
            public UpdateSnapshot getSnapshot() {
                return snapshot;
            }

            @Override
            public void checkNow() {
            }

            @Override
            public void downloadNow() {
            }

            @Override
            public void close() {
            }
        };
        final UpdateProducts products = new UpdateProducts() {
            @Override
            public List<UpdateRegistration> all() {
                return Collections.unmodifiableList(Collections.singletonList(registration));
            }

            @Override
            public UpdateRegistration get(String product) {
                return product.equalsIgnoreCase("product") ? registration : null;
            }
        };
        UpdateService service = new UpdateService() {
            @Override
            public UpdateRegistration register(Object owner, ProductDescriptor product, PluginUpdateRequest request,
                                               List<PluginDependency> dependencies) {
                return registration;
            }

            @Override
            public UpdateProducts getProducts() {
                return products;
            }

            @Override
            public UpdatePlans getPlans() {
                throw new IllegalStateException("not used");
            }

            @Override
            public UpdateSettings getSettings() {
                throw new IllegalStateException("not used");
            }

            @Override
            public UpdateBackups getBackups() {
                throw new IllegalStateException("not used");
            }

            @Override
            public UpdateHistory getHistory() {
                throw new IllegalStateException("not used");
            }
        };

        assertSame(registration, service.getProducts().get("PRODUCT"));
        assertSame(registration, service.getProducts().require("product"));
        assertThrows(IllegalStateException.class, () -> service.getProducts().require("missing"));
        assertThrows(UnsupportedOperationException.class, () -> service.getProducts().all().clear());
    }
}
