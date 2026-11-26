package org.pnfolder.pnlibrary.core.observability.history;

import com.google.gson.Gson;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.common.io.AtomicFiles;
import org.pnfolder.pnlibrary.core.observability.report.EncryptedEnvelopeCodec;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Persists a bounded, encrypted rolling history of diagnostic incidents.
 *
 * <p>No private key or plaintext history is stored on disk. Each runtime instance owns
 * a unique session file, rewritten atomically as incidents change. Rotation applies
 * age, file-count, and aggregate-byte limits.
 */
public final class IncidentHistoryStore {
    private static final int MAX_FILES = 256;
    private static final long MILLIS_PER_DAY = 86_400_000L;
    private static final DateTimeFormatter SESSION_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);

    private final Path directory;
    private final EncryptedEnvelopeCodec codec;
    private final int retentionDays;
    private final long maxBytes;
    private final Clock clock;
    private final Gson gson = new Gson();
    private final String sessionStartedUtc;
    private final String session;

    /** A store on the system UTC clock. */
    public IncidentHistoryStore(@NotNull Path directory, @Nullable EncryptedEnvelopeCodec codec, int retentionDays,
                                long maxBytes) {
        this(directory, codec, retentionDays, maxBytes, Clock.systemUTC());
    }

    /**
     * Creates the store.
     *
     * @param directory dedicated history directory
     * @param codec public-key envelope codec; {@code null} disables persistence
     * @param retentionDays maximum file age in whole days
     * @param maxBytes aggregate encrypted history budget
     * @param clock time source used for timestamps and retention
     */
    public IncidentHistoryStore(@NotNull Path directory, @Nullable EncryptedEnvelopeCodec codec, int retentionDays,
                                long maxBytes, @NotNull Clock clock) {
        if (retentionDays <= 0) {
            throw new IllegalArgumentException("retentionDays must be positive");
        }
        if (maxBytes <= 0) {
            throw new IllegalArgumentException("maxBytes must be positive");
        }
        this.directory = directory;
        this.codec = codec;
        this.retentionDays = retentionDays;
        this.maxBytes = maxBytes;
        this.clock = clock;
        Instant sessionStarted = clock.instant();
        this.sessionStartedUtc = sessionStarted.toString();
        this.session = "session-" + SESSION_TIME_FORMAT.format(sessionStarted) + "-" + UUID.randomUUID() + ".pndlog";
    }

    /** Encrypts and atomically replaces the current runtime session snapshot. */
    public synchronized void save(@NotNull List<Map<String, Object>> logIncidents,
                                  @NotNull Map<String, Object> diagnosticEvents) throws IOException {
        if (codec == null) {
            return;
        }
        Files.createDirectories(directory);
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("format", "pnlibrary-incident-history");
        document.put("version", 1);
        document.put("sessionStartedUtc", sessionStartedUtc);
        document.put("updatedUtc", clock.instant().toString());
        document.put("logIncidents", logIncidents);
        document.put("pluginEvents", diagnosticEvents);
        byte[] encrypted = codec.encryptBinary(gson.toJson(document).getBytes(StandardCharsets.UTF_8), "incident-history");
        Path target = directory.resolve(session);
        Path temporary = Files.createTempFile(directory, session, ".tmp");
        try {
            Files.write(temporary, encrypted);
            AtomicFiles.move(temporary, target);
        } finally {
            Files.deleteIfExists(temporary);
        }
        rotate();
    }

    /** Applies the age, file-count and size limits without writing a new snapshot. */
    public synchronized void prune() throws IOException {
        if (Files.isDirectory(directory)) {
            rotate();
        }
    }

    /**
     * Returns retained encrypted files without exceeding the byte budget.
     *
     * <p>Files changed externally to exceed the per-directory budget are skipped before allocation.
     */
    @NotNull
    public List<Map.Entry<String, byte[]>> files() throws IOException {
        if (!Files.isDirectory(directory)) {
            return Collections.emptyList();
        }
        List<Path> candidates;
        try (Stream<Path> stream = Files.list(directory)) {
            candidates = stream.filter(IncidentHistoryStore::isHistoryFile).sorted().collect(Collectors.toList());
        }
        List<Map.Entry<String, byte[]>> result = new ArrayList<>();
        long remainingBytes = maxBytes;
        for (Path file : candidates) {
            Long size = fileSize(file);
            if (size == null || size <= 0 || size > remainingBytes) {
                continue;
            }
            result.add(new AbstractMap.SimpleImmutableEntry<>(file.getFileName().toString(), Files.readAllBytes(file)));
            remainingBytes -= size;
        }
        return result;
    }

    private void rotate() throws IOException {
        List<Path> retained = new ArrayList<>();
        long oldestAllowed = clock.millis() - retentionDays * MILLIS_PER_DAY;
        List<Path> newestFirst = newestFirst();
        for (int index = 0; index < newestFirst.size(); index++) {
            Path file = newestFirst.get(index);
            Long modified = lastModifiedMillis(file);
            boolean expired = modified != null && modified < oldestAllowed;
            if (expired || index >= MAX_FILES) {
                Files.deleteIfExists(file);
            } else {
                retained.add(file);
            }
        }
        long total = 0L;
        for (Path file : retained) {
            Long size = fileSize(file);
            total += size == null ? 0L : size;
        }
        for (int index = retained.size() - 1; index >= 0 && total > maxBytes; index--) {
            Long size = fileSize(retained.get(index));
            total -= size == null ? 0L : size;
            Files.deleteIfExists(retained.get(index));
        }
    }

    private List<Path> newestFirst() throws IOException {
        try (Stream<Path> stream = Files.list(directory)) {
            return stream.filter(IncidentHistoryStore::isHistoryFile)
                    .sorted(Comparator.comparing(IncidentHistoryStore::lastModifiedOrFail).reversed())
                    .collect(Collectors.toList());
        }
    }

    private static boolean isHistoryFile(Path path) {
        return Files.isRegularFile(path) && path.getFileName().toString().endsWith(".pndlog");
    }

    private static FileTime lastModifiedOrFail(Path file) {
        try {
            return Files.getLastModifiedTime(file);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    // Retention is best-effort: a file that vanished or is locked is skipped and retried on the next prune.
    private static Long fileSize(Path file) {
        try {
            return Files.size(file);
        } catch (IOException | RuntimeException skipped) {
            return null;
        }
    }

    private static Long lastModifiedMillis(Path file) {
        try {
            return Files.getLastModifiedTime(file).toMillis();
        } catch (IOException | RuntimeException skipped) {
            return null;
        }
    }
}
