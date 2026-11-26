package org.pnfolder.pnlibrary.core.observability.report;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.common.io.AtomicFiles;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Persists generated diagnostic reports and enforces local retention.
 *
 * <p>Only files created in the {@code report-*} namespace with a supported report
 * extension participate in cleanup. Unrelated files in the same directory are
 * never removed.
 */
public final class SupportReportStore {
    private static final String REPORT_PREFIX = "report-";
    private static final String ENCRYPTED_EXTENSION = ".pnsupport";
    private static final String ARCHIVE_EXTENSION = ".zip";

    private final Path directory;

    /** @param directory directory that owns local report artifacts */
    public SupportReportStore(@NotNull Path directory) {
        this.directory = directory;
    }

    /**
     * Atomically stores {@code payload}, then retains at most {@code keepCount} reports.
     *
     * @param encrypted selects {@code .pnsupport}; plaintext archives use {@code .zip}
     * @return final report path
     */
    @NotNull
    public Path save(@NotNull byte[] payload, boolean encrypted, int keepCount) throws IOException {
        if (keepCount <= 0) {
            throw new IllegalArgumentException("keepCount must be positive");
        }
        Files.createDirectories(directory);
        String extension = encrypted ? ENCRYPTED_EXTENSION : ARCHIVE_EXTENSION;
        Path target = Files.createTempFile(directory, REPORT_PREFIX, extension);
        Path staging = target.resolveSibling(target.getFileName() + ".tmp");
        try {
            Files.write(staging, payload);
            AtomicFiles.move(staging, target);
        } catch (IOException | RuntimeException failure) {
            Files.deleteIfExists(target);
            throw failure;
        } finally {
            Files.deleteIfExists(staging);
        }
        removeExpiredReports(keepCount);
        return target;
    }

    private void removeExpiredReports(int keepCount) throws IOException {
        List<Path> reports;
        try (Stream<Path> stream = Files.list(directory)) {
            reports = stream.filter(SupportReportStore::isOwnedReport)
                    .sorted(Comparator.comparing(SupportReportStore::lastModified).reversed())
                    .collect(Collectors.toList());
        }
        for (Path report : reports.subList(Math.min(keepCount, reports.size()), reports.size())) {
            try {
                Files.deleteIfExists(report);
            } catch (IOException locked) {
                // Retention is best-effort; a locked old report must not lose the new one.
            }
        }
    }

    private static FileTime lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private static boolean isOwnedReport(Path path) {
        if (!Files.isRegularFile(path)) {
            return false;
        }
        String name = path.getFileName().toString();
        return name.startsWith(REPORT_PREFIX) && (name.endsWith(ENCRYPTED_EXTENSION) || name.endsWith(ARCHIVE_EXTENSION));
    }
}
