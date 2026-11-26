package org.pnfolder.pnlibrary.core.downloads;

import java.util.Objects;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.downloads.DownloadPolicy;
import org.pnfolder.pnlibrary.api.downloads.DownloadRegistration;
import org.pnfolder.pnlibrary.api.downloads.DownloadSnapshot;
import org.pnfolder.pnlibrary.api.downloads.DownloadState;
import org.pnfolder.pnlibrary.api.downloads.FileDownload;
import org.pnfolder.pnlibrary.api.downloads.FileDownloads;
import org.pnfolder.pnlibrary.common.lang.Attempt;
import org.pnfolder.pnlibrary.common.lang.Failures;

/** Tracks the files of one download request and runs at most one download of them at a time. */
public final class DownloadBatch implements DownloadRegistration {
    private static final String DEPENDENCY_PREFIX = "dependency:";
    private static final String CLOSED_MESSAGE = "Download registration is closed";

    private final FileDownloads request;
    private final Supplier<DownloadConfiguration> configuration;
    private final Executor executor;
    private final BooleanSupplier managerIsClosed;
    private final DownloadInstaller install;
    private final Consumer<String> onBlocked;
    private final Consumer<List<FileDownload>> dependenciesStaged;
    private final BiConsumer<List<FileDownload>, Throwable> failed;
    private final Consumer<DownloadBatch> remove;
    private final BooleanSupplier automaticAllowed;
    private final Object stateLock = new Object();
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicReference<CompletableFuture<List<DownloadSnapshot>>> activeDownload =
        new AtomicReference<>();
    private final AtomicReference<List<DownloadSnapshot>> currentSnapshots;
    private final BatchSnapshots snapshotBoard;

    /** Creates a batch whose automatic start follows {@code configuration}. */
    public DownloadBatch(@NotNull FileDownloads request, @NotNull Supplier<DownloadConfiguration> configuration,
                         @NotNull Executor executor, @NotNull BooleanSupplier managerIsClosed,
                         @NotNull DownloadInstaller install, @NotNull Consumer<String> onBlocked,
                         @NotNull Consumer<List<FileDownload>> dependenciesStaged,
                         @NotNull BiConsumer<List<FileDownload>, Throwable> failed,
                         @NotNull Consumer<DownloadBatch> remove) {
        this(request, configuration, executor, managerIsClosed, install, onBlocked, dependenciesStaged, failed, remove,
            () -> configuration.get().isAutomatic());
    }

    /** Creates a batch; {@code automaticAllowed} says whether downloads that start by themselves may run. */
    public DownloadBatch(@NotNull FileDownloads request, @NotNull Supplier<DownloadConfiguration> configuration,
                         @NotNull Executor executor, @NotNull BooleanSupplier managerIsClosed,
                         @NotNull DownloadInstaller install, @NotNull Consumer<String> onBlocked,
                         @NotNull Consumer<List<FileDownload>> dependenciesStaged,
                         @NotNull BiConsumer<List<FileDownload>, Throwable> failed,
                         @NotNull Consumer<DownloadBatch> remove, @NotNull BooleanSupplier automaticAllowed) {
        this.request = Objects.requireNonNull(request, "request");
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.managerIsClosed = Objects.requireNonNull(managerIsClosed, "managerIsClosed");
        this.install = Objects.requireNonNull(install, "install");
        this.onBlocked = Objects.requireNonNull(onBlocked, "onBlocked");
        this.dependenciesStaged = Objects.requireNonNull(dependenciesStaged, "dependenciesStaged");
        this.failed = Objects.requireNonNull(failed, "failed");
        this.remove = Objects.requireNonNull(remove, "remove");
        this.automaticAllowed = automaticAllowed;
        this.snapshotBoard = new BatchSnapshots(request.getFiles());
        this.currentSnapshots = new AtomicReference<>(
            snapshotBoard.all(DownloadState.DECLARED, null));
    }

    @Override
    public boolean isClosed() {
        return closed.get();
    }

    @Override
    public @NotNull List<DownloadSnapshot> snapshots() {
        return Collections.unmodifiableList(new ArrayList<>(currentSnapshots.get()));
    }

    @Override
    public @NotNull CompletionStage<List<DownloadSnapshot>> downloadNow() {
        return start(request.getFiles(), false);
    }

    /** Starts the files whose policy allows it without a command. */
    public void startAutomatic() {
        List<FileDownload> automaticFiles = new ArrayList<>();
        for (FileDownload file : request.getFiles()) {
            if (file.getPolicy() != DownloadPolicy.MANUAL) {
                automaticFiles.add(file);
            }
        }
        if (!automaticFiles.isEmpty()) {
            start(automaticFiles, true);
        }
    }

    @Override
    public void whenFinished(@NotNull Consumer<List<DownloadSnapshot>> handler) {
        snapshotBoard.whenFinished(handler);
    }

    @Override
    public void close() {
        CompletableFuture<List<DownloadSnapshot>> pending;
        synchronized (stateLock) {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            currentSnapshots.set(snapshotBoard.all(DownloadState.CLOSED, null));
            pending = activeDownload.getAndSet(null);
        }
        if (pending != null) {
            pending.completeExceptionally(new IllegalStateException(CLOSED_MESSAGE));
        }
        remove.accept(this);
    }

    private CompletableFuture<List<DownloadSnapshot>> start(List<FileDownload> declarations, boolean automaticPolicy) {
        CompletableFuture<List<DownloadSnapshot>> promise = new CompletableFuture<>();
        if (isShutDown()) {
            promise.completeExceptionally(new IllegalStateException(CLOSED_MESSAGE));
            return promise;
        }
        List<FileDownload> selected = selectFiles(declarations, automaticPolicy);
        if (!configuration.get().isEnabled()) {
            return blocked(promise, "система загрузок отключена");
        }
        if (selected.isEmpty()) {
            if (automaticPolicy && !declarations.isEmpty()) {
                return blocked(promise, "автозагрузка выключена (включить: /pn deps auto on)");
            }
            promise.complete(snapshots());
            return promise;
        }
        CompletableFuture<List<DownloadSnapshot>> reserved = reserve(promise, selected);
        if (reserved != promise || promise.isDone()) {
            return reserved;
        }
        try {
            executor.execute(() -> execute(selected, promise));
        } catch (Throwable error) {
            rejected(promise, error);
        }
        return promise;
    }

    /** Takes the single job slot for {@code promise}; returns the running job's future when the slot is taken. */
    private CompletableFuture<List<DownloadSnapshot>> reserve(CompletableFuture<List<DownloadSnapshot>> promise,
                                                              List<FileDownload> selected) {
        synchronized (stateLock) {
            if (isShutDown()) {
                promise.completeExceptionally(new IllegalStateException(CLOSED_MESSAGE));
                return promise;
            }
            CompletableFuture<List<DownloadSnapshot>> running = activeDownload.get();
            if (running != null) {
                return running;
            }
            activeDownload.set(promise);
            updateSnapshots(selected, DownloadState.DOWNLOADING);
            return promise;
        }
    }

    private void rejected(CompletableFuture<List<DownloadSnapshot>> promise, Throwable error) {
        synchronized (stateLock) {
            if (!isShutDown()) {
                currentSnapshots.set(snapshotBoard.all(DownloadState.FAILED, error.getMessage()));
            }
            activeDownload.compareAndSet(promise, null);
        }
        promise.completeExceptionally(error);
    }

    private List<FileDownload> selectFiles(List<FileDownload> declarations, boolean automaticPolicy) {
        if (!automaticPolicy || automaticAllowed.getAsBoolean()) {
            return declarations;
        }
        List<FileDownload> forced = new ArrayList<>();
        for (FileDownload file : declarations) {
            if (file.getPolicy() == DownloadPolicy.FORCED) {
                forced.add(file);
            }
        }
        return forced;
    }

    private CompletableFuture<List<DownloadSnapshot>> blocked(CompletableFuture<List<DownloadSnapshot>> promise,
                                                             String reason) {
        List<DownloadSnapshot> result;
        synchronized (stateLock) {
            if (isShutDown()) {
                promise.completeExceptionally(new IllegalStateException(CLOSED_MESSAGE));
                return promise;
            }
            currentSnapshots.set(snapshotBoard.all(DownloadState.BLOCKED, reason));
            result = snapshots();
        }
        promise.complete(result);
        snapshotBoard.notifyFinished(result);
        Failures.runCatchingNonFatal(() -> {
            onBlocked.accept(reason);
            return null;
        });
        return promise;
    }

    private void execute(List<FileDownload> declarations, CompletableFuture<List<DownloadSnapshot>> promise) {
        if (isShutDown()) {
            activeDownload.compareAndSet(promise, null);
            promise.complete(snapshots());
            return;
        }
        Attempt<Object> outcome = Failures.runCatchingNonFatal(() -> {
            install.install(declarations, () -> {
                if (isShutDown()) {
                    throw new IllegalStateException(CLOSED_MESSAGE);
                }
            });
            return null;
        });
        List<DownloadSnapshot> result = record(declarations, promise, outcome);
        promise.complete(result);
        if (isShutDown()) {
            return;
        }
        snapshotBoard.notifyFinished(result);
        if (outcome.isSuccess()) {
            if (containsDependencies(declarations)) {
                Failures.runCatchingNonFatal(() -> {
                    dependenciesStaged.accept(declarations);
                    return null;
                });
            }
            return;
        }
        Throwable error = outcome.exceptionOrNull();
        CleanupSteps.suppressInto(error, () -> failed.accept(declarations, error));
    }

    private List<DownloadSnapshot> record(List<FileDownload> declarations,
                                          CompletableFuture<List<DownloadSnapshot>> promise, Attempt<Object> outcome) {
        try {
            synchronized (stateLock) {
                if (!isShutDown()) {
                    Throwable error = outcome.exceptionOrNull();
                    if (error == null) {
                        updateSnapshots(declarations, DownloadState.STAGED);
                    } else {
                        currentSnapshots.set(snapshotBoard.all(DownloadState.FAILED, error.getMessage()));
                    }
                }
                return snapshots();
            }
        } finally {
            // Future callbacks may immediately start another download.
            activeDownload.compareAndSet(promise, null);
        }
    }

    private void updateSnapshots(List<FileDownload> activeFiles, DownloadState state) {
        currentSnapshots.set(snapshotBoard.marking(activeFiles, state));
    }

    private boolean isShutDown() {
        return closed.get() || managerIsClosed.getAsBoolean();
    }

    private static boolean containsDependencies(List<FileDownload> declarations) {
        for (FileDownload file : declarations) {
            if (file.getKey().startsWith(DEPENDENCY_PREFIX)) {
                return true;
            }
        }
        return false;
    }
}
