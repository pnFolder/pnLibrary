package org.pnfolder.pnlibrary.core.downloads.fetch;

import java.util.Objects;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.function.Supplier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.downloads.FileDownload;
import org.pnfolder.pnlibrary.api.downloads.FileDownloads;
import org.pnfolder.pnlibrary.api.downloads.source.DownloadDestination;
import org.pnfolder.pnlibrary.api.platform.PlatformType;
import org.pnfolder.pnlibrary.common.io.Digests;
import org.pnfolder.pnlibrary.common.jvm.JavaRuntime;
import org.pnfolder.pnlibrary.core.downloads.CleanupSteps;
import org.pnfolder.pnlibrary.core.downloads.DownloadConfiguration;
import org.pnfolder.pnlibrary.updates.engine.net.TrustedHttpClient;

/** Downloads, validates and stages one declared file without publishing it. */
public final class DownloadArtifactPreparer {
    private static final long DEFAULT_MAXIMUM_BYTES = 512L * 1024L * 1024L;

    private final PlatformType platform;
    private final Path libraryData;
    private final Supplier<DownloadConfiguration> configuration;
    private final long maximumBytes;
    private final SourceFetcher fetcher;

    /** Creates a preparer with the default size limit of 512 MiB. */
    public DownloadArtifactPreparer(@NotNull PlatformType platform, @NotNull Path libraryData,
                                    @NotNull Supplier<DownloadConfiguration> configuration,
                                    @NotNull TrustedHttpClient http) {
        this(platform, libraryData, configuration, http, DEFAULT_MAXIMUM_BYTES);
    }

    /** Creates a preparer that refuses files larger than {@code maximumBytes} unless the source sets its own limit. */
    public DownloadArtifactPreparer(@NotNull PlatformType platform, @NotNull Path libraryData,
                                    @NotNull Supplier<DownloadConfiguration> configuration,
                                    @NotNull TrustedHttpClient http, long maximumBytes) {
        this.platform = Objects.requireNonNull(platform, "platform");
        this.libraryData = Objects.requireNonNull(libraryData, "libraryData");
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        this.maximumBytes = maximumBytes;
        this.fetcher = new SourceFetcher(http);
    }

    /** Fetches and checks the file of {@code declaration} and leaves it in the staging folder. */
    public @NotNull PreparedDownload prepare(@NotNull FileDownloads request, @NotNull FileDownload declaration,
                                             @Nullable DownloadVerifier verifier) throws Exception {
        if (!declaration.getSource().supports(platform, JavaRuntime.getFeature())) {
            throw new IllegalArgumentException("файл не поддерживает " + platform.getDisplayName()
                + " / Java " + JavaRuntime.getFeature());
        }
        Path target = target(request, declaration);
        Long declared = declaration.getSource().getMaxBytes();
        long allowed = declared != null ? declared : maximumBytes;
        Long size = declaration.getSource().getSize();
        int limit = (int) (size != null ? Math.min(size, allowed) : allowed);
        byte[] bytes = fetcher.fetch(declaration.getSource(), limit);
        verifyIntegrity(declaration, bytes);
        return stage(declaration, target, bytes, verifier);
    }

    private PreparedDownload stage(FileDownload declaration, Path target, byte[] bytes, DownloadVerifier verifier)
        throws Exception {
        Path staging = libraryData.resolve("downloads/staging").resolve(UUID.randomUUID() + "-" + target.getFileName());
        Files.createDirectories(staging.getParent());
        try {
            Files.write(staging, bytes);
            if (verifier != null) {
                verifier.verify(declaration, staging);
            }
            return new PreparedDownload(staging, target);
        } catch (Throwable error) {
            CleanupSteps.suppressInto(error, () -> Files.deleteIfExists(staging));
            throw error;
        }
    }

    private void verifyIntegrity(FileDownload declaration, byte[] bytes) {
        Long expectedSize = declaration.getSource().getSize();
        if (expectedSize != null && bytes.length != expectedSize) {
            throw new IllegalArgumentException("размер файла не совпадает");
        }
        String expectedHash = declaration.getSource().getSha256();
        if (expectedHash != null && !Digests.sha256(bytes).equalsIgnoreCase(expectedHash)) {
            throw new IllegalArgumentException("SHA-256 файла не совпадает");
        }
    }

    private Path target(FileDownloads request, FileDownload declaration) {
        if (!configuration.get().getDestinations().contains(declaration.getDestination())) {
            throw new IllegalArgumentException("назначение " + declaration.getDestination() + " запрещено конфигурацией");
        }
        Path root = rootOf(request, declaration).toAbsolutePath().normalize();
        Path target = root.resolve(declaration.getRelativePath()).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("путь загрузки выходит за разрешённую папку");
        }
        return target;
    }

    private Path rootOf(FileDownloads request, FileDownload declaration) {
        if (declaration.getDestination() == DownloadDestination.CACHE) {
            return libraryData.resolve("downloads/cache");
        }
        Path directory = request.getDataDirectory();
        if (directory == null) {
            throw new IllegalArgumentException("для DATA_FOLDER передайте папку в downloads(dataDirectory, ...)");
        }
        return directory;
    }
}
