package org.pnfolder.pnlibrary.core.downloads;

import java.util.Objects;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.dependencies.PluginDependency;
import org.pnfolder.pnlibrary.api.downloads.DownloadRegistration;
import org.pnfolder.pnlibrary.api.downloads.DownloadSnapshot;
import org.pnfolder.pnlibrary.api.downloads.FileDownload;
import org.pnfolder.pnlibrary.api.downloads.FileDownloads;
import org.pnfolder.pnlibrary.api.platform.PlatformType;
import org.pnfolder.pnlibrary.core.downloads.dependencies.DependencyDownloadReporter;
import org.pnfolder.pnlibrary.core.downloads.dependencies.DependencyDownloads;
import org.pnfolder.pnlibrary.core.downloads.dependencies.DependencyModules;
import org.pnfolder.pnlibrary.core.downloads.dependencies.DependencyDownloadRequestFactory;
import org.pnfolder.pnlibrary.core.downloads.dependencies.DownloadedDependencyStore;
import org.pnfolder.pnlibrary.core.downloads.dependencies.MissingGroup;
import org.pnfolder.pnlibrary.core.downloads.fetch.AtomicDownloadPublisher;
import org.pnfolder.pnlibrary.core.downloads.fetch.DownloadArtifactPreparer;
import org.pnfolder.pnlibrary.core.downloads.fetch.DownloadVerifier;
import org.pnfolder.pnlibrary.core.downloads.fetch.PluginJarVerifier;
import org.pnfolder.pnlibrary.core.downloads.fetch.PreparedDownload;
import org.pnfolder.pnlibrary.spi.platform.PlatformIdentity;
import org.pnfolder.pnlibrary.spi.platform.logging.PlatformLogging;
import org.pnfolder.pnlibrary.spi.platform.plugin.PlatformOwnerMetadata;
import org.pnfolder.pnlibrary.spi.platform.plugin.PlatformPluginInventory;
import org.pnfolder.pnlibrary.spi.platform.lifecycle.PlatformReadiness;
import org.pnfolder.pnlibrary.updates.engine.net.TrustedHttpClient;
import org.pnfolder.pnlibrary.updates.paths.LibraryPaths;

/** Downloads, verifies and atomically publishes files declared by plugins, including missing plugin dependencies. */
public final class DownloadService implements AutoCloseable {
    private static final long MAX_BYTES = 512L * 1024L * 1024L;

    private final PlatformLogging logging;
    private final Supplier<DownloadConfiguration> configuration;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final ExecutorService executor = Executors.newSingleThreadExecutor(action -> {
        Thread thread = new Thread(action, "pnLibrary-direct-downloads");
        thread.setDaemon(true);
        return thread;
    });
    private final List<DownloadBatch> registrations = new CopyOnWriteArrayList<>();
    private final AtomicDownloadPublisher filePublisher;
    private final DownloadArtifactPreparer artifactPreparer;
    private final DependencyDownloads dependencies;

    /** Creates the service with the default network timeouts. */
    public DownloadService(@NotNull PlatformIdentity identity, @NotNull PlatformOwnerMetadata ownership,
                           @NotNull PlatformPluginInventory inventory, @NotNull PlatformReadiness readiness,
                           @NotNull PlatformLogging logging, @NotNull LibraryPaths paths,
                           @NotNull Supplier<DownloadConfiguration> configuration) {
        this(identity, ownership, inventory, readiness, logging, paths, configuration,
            new TrustedHttpClient(Duration.ofSeconds(8), Duration.ofSeconds(30)));
    }

    /** Creates the service with every part given. */
    public DownloadService(@NotNull PlatformIdentity identity, @NotNull PlatformOwnerMetadata ownership,
                           @NotNull PlatformPluginInventory inventory, @NotNull PlatformReadiness readiness,
                           @NotNull PlatformLogging logging, @NotNull LibraryPaths paths,
                           @NotNull Supplier<DownloadConfiguration> configuration, @NotNull TrustedHttpClient http) {
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        PlatformType type = identity.getType();
        this.filePublisher = new AtomicDownloadPublisher(paths.getData());
        this.artifactPreparer = new DownloadArtifactPreparer(type, paths.getData(), configuration, http, MAX_BYTES);
        boolean proxy = type == PlatformType.VELOCITY || type == PlatformType.BUNGEECORD;
        DependencyModules dependencyModules = new DependencyModules();
        this.dependencies = new DependencyDownloads(ownership, inventory, readiness, logging,
            new DependencyDownloadRequestFactory(paths.getServerUpdateFolder(), type, identity::getMinecraftVersion),
            new DependencyDownloadReporter(logging, ownership, proxy ? "pnproxy" : "pn", dependencyModules),
            new DownloadedDependencyStore(paths.getData()), new PluginJarVerifier(identity), dependencyModules);
        this.logging = Objects.requireNonNull(logging, "logging");
    }

    /** Registers the files of {@code request} for {@code owner} and starts those that may start by themselves. */
    public @NotNull DownloadRegistration register(@NotNull Object owner, @NotNull FileDownloads request) {
        return register(owner, request, null);
    }

    /** Registers the missing external dependencies; returns null when nothing can be downloaded. */
    public @Nullable DownloadRegistration registerDependencies(@NotNull Object owner, @NotNull String module,
                                                               @NotNull List<? extends PluginDependency> required) {
        return dependencies.register(owner, module, required, this::register);
    }

    /** Dependencies still missing, with the code that installs each group. */
    public @NotNull List<MissingGroup> missingGroups() {
        return dependencies.groups();
    }

    /**
     * Installs the group with this {@code code} now, whatever the automatic switch says: typing the code is the
     * approval. Returns null when no pending group has the code.
     */
    public @Nullable CompletionStage<List<DownloadSnapshot>> installGroup(@NotNull String code) {
        return dependencies.install(code);
    }

    /** Downloads and publishes one file at once. */
    public void install(@NotNull FileDownloads request, @NotNull FileDownload declaration) throws Exception {
        installBatch(request, Collections.singletonList(declaration));
    }

    /** Downloads and publishes {@code declarations} as one transaction. */
    public void installBatch(@NotNull FileDownloads request, @NotNull List<FileDownload> declarations)
        throws Exception {
        installBatch(request, declarations, () -> { }, null);
    }

    /**
     * Downloads {@code declarations}, runs {@code beforePublish}, then publishes them as one transaction. A failure at
     * any point removes what was staged and publishes nothing.
     */
    public void installBatch(@NotNull FileDownloads request, @NotNull List<FileDownload> declarations,
                             @NotNull Runnable beforePublish, @Nullable DownloadVerifier verifier) throws Exception {
        checkOpen();
        List<PreparedDownload> prepared = new ArrayList<>();
        try {
            for (FileDownload declaration : declarations) {
                prepared.add(artifactPreparer.prepare(request, declaration, verifier));
            }
        } catch (Throwable error) {
            deleteStaged(prepared);
            throw error;
        }
        if (prepared.isEmpty()) {
            return;
        }
        try {
            beforePublish.run();
            checkOpen();
            filePublisher.publish(prepared);
        } catch (Throwable error) {
            deleteStaged(prepared);
            throw error;
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        for (DownloadBatch batch : new ArrayList<>(registrations)) {
            batch.close();
        }
        registrations.clear();
        executor.shutdownNow();
    }

    private DownloadRegistration register(Object owner, FileDownloads request, DownloadVerifier verifier) {
        DownloadBatch batch = new DownloadBatch(
            request,
            executor,
            closed::get,
            (declarations, beforePublish) -> {
                installBatch(request, declarations, beforePublish, verifier);
                dependencies.remember(declarations);
            },
            declarations -> dependencies.reportStaged(owner, declarations),
            (declarations, error) -> dependencies.reportFailure(owner, declarations, error),
            registrations::remove);
        registrations.add(batch);
        batch.startAutomatic();
        return batch;
    }

    private void checkOpen() {
        if (closed.get()) {
            throw new IllegalStateException("Download manager is closed");
        }
    }

    private static void deleteStaged(List<PreparedDownload> prepared) throws IOException {
        for (PreparedDownload download : prepared) {
            Files.deleteIfExists(download.getStaging());
        }
    }
}
