package org.pnfolder.pnlibrary.core.downloads;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.downloads.DownloadSnapshot;
import org.pnfolder.pnlibrary.api.downloads.DownloadState;
import org.pnfolder.pnlibrary.api.downloads.FileDownload;
import org.pnfolder.pnlibrary.api.downloads.FileDownloads;
import org.pnfolder.pnlibrary.api.downloads.source.DownloadDestination;

class DownloadBatchTest {
    private static final DownloadInstaller NOTHING = (declarations, beforePublish) -> { };
    private static final BiConsumer<List<FileDownload>, Throwable> IGNORE_FAILURE = (declarations, error) -> { };

    private static DownloadBatch registration(String key, Executor executor, DownloadInstaller install,
                                              BiConsumer<List<FileDownload>, Throwable> failed,
                                              Consumer<List<FileDownload>> dependenciesStaged,
                                              BooleanSupplier managerIsClosed) {
        FileDownloads request = FileDownloads.builder().file(key, file -> file
            .url("https://example.org/manual.bin").destination(DownloadDestination.DATA_FOLDER, "manual.bin")).build();
        return new DownloadBatch(request, DownloadConfiguration::new, executor, managerIsClosed, install,
            reason -> { }, dependenciesStaged, failed, batch -> { });
    }

    private static DownloadBatch registration(Executor executor) {
        return registration("manual", executor, NOTHING, IGNORE_FAILURE, declarations -> { }, () -> false);
    }

    private static DownloadState onlyState(List<DownloadSnapshot> snapshots) {
        assertEquals(1, snapshots.size());
        return snapshots.get(0).getState();
    }

    @Test
    void futureContinuationCanImmediatelyStartAFreshDownload() throws Exception {
        List<Runnable> queue = new ArrayList<Runnable>();
        DownloadBatch registration = registration(queue::add);
        CompletableFuture<List<DownloadSnapshot>> first = registration.downloadNow().toCompletableFuture();
        CompletableFuture<CompletableFuture<List<DownloadSnapshot>>> next =
            first.thenApply(result -> registration.downloadNow().toCompletableFuture());
        queue.get(0).run();
        CompletableFuture<List<DownloadSnapshot>> second = next.get(1, TimeUnit.SECONDS);
        assertNotSame(first, second);
        assertEquals(2, queue.size());
        queue.get(1).run();
        assertEquals(DownloadState.STAGED, onlyState(second.get(1, TimeUnit.SECONDS)));
        registration.close();
    }

    @Test
    void whenFinishedHearsTheResultOfADownloadHoweverItStarted() {
        List<Runnable> queue = new ArrayList<Runnable>();
        DownloadBatch registration = registration(queue::add);
        List<DownloadState> heard = new ArrayList<DownloadState>();
        registration.whenFinished(snapshots -> {
            for (DownloadSnapshot snapshot : snapshots) {
                heard.add(snapshot.getState());
            }
        });

        registration.downloadNow();
        queue.get(0).run();

        assertEquals(Arrays.asList(DownloadState.STAGED), heard);
        registration.close();
    }

    @Test
    void aListenerThatThrowsDoesNotStopTheOthers() {
        List<Runnable> queue = new ArrayList<Runnable>();
        DownloadBatch registration = registration(queue::add);
        AtomicInteger second = new AtomicInteger();
        registration.whenFinished(snapshots -> {
            throw new IllegalStateException("listener broke");
        });
        registration.whenFinished(snapshots -> second.incrementAndGet());

        registration.downloadNow();
        queue.get(0).run();

        assertEquals(1, second.get());
        registration.close();
    }

    @Test
    void closeBetweenInitialValidationAndJobReservationCannotReopenRegistration() {
        List<Runnable> queue = new ArrayList<Runnable>();
        AtomicInteger checks = new AtomicInteger();
        AtomicReference<DownloadBatch> holder = new AtomicReference<DownloadBatch>();
        DownloadBatch registration = registration("manual", queue::add, NOTHING, IGNORE_FAILURE, declarations -> { },
            () -> {
                if (checks.getAndIncrement() == 0) {
                    holder.get().close();
                }
                return false;
            });
        holder.set(registration);
        CompletableFuture<List<DownloadSnapshot>> result = registration.downloadNow().toCompletableFuture();
        assertTrue(result.isCompletedExceptionally());
        assertTrue(queue.isEmpty());
        assertEquals(DownloadState.CLOSED, onlyState(registration.snapshots()));
    }

    @Test
    void executorRejectionRecordsFailureAndReleasesTheJobReservation() {
        DownloadBatch registration = registration(command -> {
            throw new RejectedExecutionException("executor stopped");
        });
        CompletableFuture<List<DownloadSnapshot>> first = registration.downloadNow().toCompletableFuture();
        assertThrows(CompletionException.class, first::join);
        assertEquals(DownloadState.FAILED, onlyState(registration.snapshots()));
        assertEquals("executor stopped", registration.snapshots().get(0).getMessage());
        CompletableFuture<List<DownloadSnapshot>> second = registration.downloadNow().toCompletableFuture();
        assertNotSame(first, second);
        assertTrue(second.isCompletedExceptionally());
        registration.close();
        assertEquals(DownloadState.CLOSED, onlyState(registration.snapshots()));
    }

    @Test
    void notificationFailureCannotStrandAFailedDownload() throws Exception {
        List<Runnable> queue = new ArrayList<Runnable>();
        IllegalStateException original = new IllegalStateException("download failed");
        IllegalArgumentException notification = new IllegalArgumentException("notification failed");
        DownloadBatch registration = registration("manual", queue::add, (declarations, beforePublish) -> {
            throw original;
        }, (declarations, error) -> {
            throw notification;
        }, declarations -> { }, () -> false);
        CompletableFuture<List<DownloadSnapshot>> first = registration.downloadNow().toCompletableFuture();
        assertDoesNotThrow(() -> queue.get(0).run());
        assertEquals(DownloadState.FAILED, onlyState(first.get(1, TimeUnit.SECONDS)));
        assertEquals(Arrays.asList(notification), Arrays.asList(original.getSuppressed()));
        CompletableFuture<List<DownloadSnapshot>> second = registration.downloadNow().toCompletableFuture();
        assertNotSame(first, second);
        assertDoesNotThrow(() -> queue.get(1).run());
        assertTrue(second.isDone());
        registration.close();
    }

    @Test
    void stagingNotificationFailureDoesNotBlockLaterDownloads() throws Exception {
        List<Runnable> queue = new ArrayList<Runnable>();
        DownloadBatch registration = registration("dependency:example", queue::add, NOTHING, IGNORE_FAILURE,
            declarations -> {
                throw new IllegalStateException("notification failed");
            }, () -> false);
        CompletableFuture<List<DownloadSnapshot>> first = registration.downloadNow().toCompletableFuture();
        assertSame(first, registration.downloadNow().toCompletableFuture());
        assertEquals(1, queue.size());
        assertDoesNotThrow(() -> queue.get(0).run());
        assertEquals(DownloadState.STAGED, onlyState(first.get(1, TimeUnit.SECONDS)));
        CompletableFuture<List<DownloadSnapshot>> second = registration.downloadNow().toCompletableFuture();
        assertNotSame(first, second);
        assertDoesNotThrow(() -> queue.get(1).run());
        assertEquals(DownloadState.STAGED, onlyState(second.get(1, TimeUnit.SECONDS)));
        registration.close();
    }

    @Test
    void movesThroughDeclaredStagedAndClosedStates() {
        FileDownloads request = FileDownloads.builder().file("manual", file -> file
            .url("https://example.org/manual.bin")
            .destination(DownloadDestination.DATA_FOLDER, "manual.bin")).build();
        List<String> installed = new ArrayList<String>();
        AtomicBoolean removed = new AtomicBoolean();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            DownloadBatch registration = new DownloadBatch(request, DownloadConfiguration::new, executor, () -> false,
                (declarations, beforePublish) -> {
                    for (FileDownload declaration : declarations) {
                        installed.add(declaration.getKey());
                    }
                }, reason -> { }, declarations -> { }, (declarations, error) -> {
                    throw new IllegalStateException(error);
                }, batch -> removed.set(true));

            assertEquals(DownloadState.DECLARED, onlyState(registration.snapshots()));
            assertEquals(DownloadState.STAGED, onlyState(registration.downloadNow().toCompletableFuture().join()));
            assertEquals(Arrays.asList("manual"), installed);

            registration.close();
            assertTrue(registration.isClosed());
            assertTrue(removed.get());
            assertEquals(DownloadState.CLOSED, onlyState(registration.snapshots()));
        } finally {
            executor.shutdownNow();
        }
    }
}
