package org.pnfolder.pnlibrary.core.downloads.dependencies;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.downloads.DownloadRegistration;
import org.pnfolder.pnlibrary.api.downloads.DownloadSnapshot;
import org.pnfolder.pnlibrary.api.downloads.DownloadState;

class CompositeDownloadRegistrationTest {
    private static List<String> keys(List<DownloadSnapshot> snapshots) {
        List<String> keys = new ArrayList<>();
        for (DownloadSnapshot snapshot : snapshots) {
            keys.add(snapshot.getKey());
        }
        return keys;
    }

    @Test
    void closesBothRegistrationsAndRetainsTheirFailures() {
        IllegalStateException primary = new IllegalStateException("first");
        IllegalArgumentException secondary = new IllegalArgumentException("second");
        StubRegistration first = new StubRegistration("first", primary);
        StubRegistration second = new StubRegistration("second", secondary);
        DownloadRegistration combined = CompositeDownloadRegistration.combine(first, second);
        IllegalStateException thrown = assertThrows(IllegalStateException.class, combined::close);
        assertSame(primary, thrown);
        assertEquals(Arrays.asList(secondary), Arrays.asList(thrown.getSuppressed()));
        assertTrue(first.isClosed());
        assertTrue(second.isClosed());
    }

    @Test
    void combinesSnapshotsDownloadsAndCloseLifecycle() {
        StubRegistration first = new StubRegistration("first", null);
        StubRegistration second = new StubRegistration("second", null);
        DownloadRegistration combined = CompositeDownloadRegistration.combine(first, second);

        assertEquals(Arrays.asList("first", "second"), keys(combined.snapshots()));
        assertEquals(Arrays.asList("first", "second"), keys(combined.downloadNow().toCompletableFuture().join()));

        combined.close();
        assertTrue(first.isClosed());
        assertTrue(second.isClosed());
    }

    @Test
    void returnsTheExistingRegistrationWhenThereIsNothingToCombine() {
        StubRegistration registration = new StubRegistration("only", null);

        assertSame(registration, CompositeDownloadRegistration.combine(registration, null));
        assertSame(registration, CompositeDownloadRegistration.combine(null, registration));
    }

    private static final class StubRegistration implements DownloadRegistration {
        private final String key;
        private final RuntimeException closeFailure;
        private boolean closed;

        StubRegistration(String key, RuntimeException closeFailure) {
            this.key = key;
            this.closeFailure = closeFailure;
        }

        @Override
        public boolean isClosed() {
            return closed;
        }

        @Override
        public @NotNull List<DownloadSnapshot> snapshots() {
            return Collections.singletonList(new DownloadSnapshot(key, DownloadState.DECLARED));
        }

        @Override
        public @NotNull CompletionStage<List<DownloadSnapshot>> downloadNow() {
            return CompletableFuture.completedFuture(snapshots());
        }

        @Override
        public void whenFinished(@NotNull Consumer<List<DownloadSnapshot>> handler) {
        }

        @Override
        public void close() {
            closed = true;
            if (closeFailure != null) {
                throw closeFailure;
            }
        }
    }
}
