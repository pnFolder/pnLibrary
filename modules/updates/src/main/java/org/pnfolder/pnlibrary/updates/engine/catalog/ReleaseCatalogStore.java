package org.pnfolder.pnlibrary.updates.engine.catalog;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.common.io.AtomicFiles;
import org.pnfolder.pnlibrary.common.io.Digests;
import org.pnfolder.pnlibrary.common.lang.Failures;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Digest-protected, atomic on-disk cache for release catalogs. */
public final class ReleaseCatalogStore {
    private static final int KEPT_CORRUPT = 3;

    private final Path root;
    private final Object lock = new Object();

    public ReleaseCatalogStore(@NotNull Path root) {
        this.root = root;
    }

    /** Reads and verifies the cached catalog for {@code uri}, when present. */
    @Nullable
    public CatalogCacheHit read(@NotNull URI uri, @NotNull Duration ttl) {
        synchronized (lock) {
            Path data = dataPath(uri);
            Path digest = digestPath(uri);
            if (!Files.isRegularFile(data) || !Files.isRegularFile(digest)) {
                return null;
            }
            try {
                byte[] bytes = Files.readAllBytes(data);
                String expected = new String(Files.readAllBytes(digest), StandardCharsets.US_ASCII).trim();
                if (!Digests.sha256(bytes).equals(expected)) {
                    quarantine(uri);
                    return null;
                }
                long millis = Math.max(0L, System.currentTimeMillis() - Files.getLastModifiedTime(data).toMillis());
                return new CatalogCacheHit(bytes, Duration.ofMillis(millis).compareTo(ttl) <= 0);
            } catch (Exception error) {
                // An unreadable cache entry is treated as a miss and moved aside; the caller refetches it.
                quarantine(uri);
                return null;
            }
        }
    }

    /** Atomically writes catalog {@code bytes} and their digest for {@code uri}. */
    public void write(@NotNull URI uri, @NotNull byte[] bytes) throws IOException {
        synchronized (lock) {
            Path data = dataPath(uri);
            Files.createDirectories(data.getParent());
            AtomicFiles.write(data, bytes);
            AtomicFiles.write(digestPath(uri), Digests.sha256(bytes).getBytes(StandardCharsets.US_ASCII));
        }
    }

    /** Moves corrupt cache files for {@code uri} aside for later diagnosis. */
    public void quarantine(@NotNull URI uri) {
        synchronized (lock) {
            final String suffix = ".corrupt-" + System.currentTimeMillis();
            // A file that cannot be moved stays in place and fails verification again on the next read.
            for (final Path path : Arrays.asList(dataPath(uri), digestPath(uri))) {
                if (Files.exists(path)) {
                    Failures.runCatchingNonFatal(() -> {
                        Files.move(path, path.resolveSibling(path.getFileName().toString() + suffix),
                            StandardCopyOption.REPLACE_EXISTING);
                        return null;
                    });
                }
                pruneQuarantine(path);
            }
        }
    }

    /** Where the cached body of the catalog at {@code uri} lives. */
    @NotNull
    public Path dataPath(@NotNull URI uri) {
        return root.resolve(key(uri) + ".data");
    }

    private Path digestPath(URI uri) {
        return root.resolve(key(uri) + ".sha256");
    }

    /** Keeps the newest few quarantined copies of {@code path} so a flapping catalog cannot fill the disk. */
    private void pruneQuarantine(Path path) {
        final String prefix = path.getFileName().toString() + ".corrupt-";
        Failures.runCatchingNonFatal(() -> {
            List<Path> copies = new ArrayList<Path>();
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(root,
                entry -> entry.getFileName().toString().startsWith(prefix))) {
                for (Path entry : stream) {
                    copies.add(entry);
                }
            }
            Collections.sort(copies, Comparator.comparingLong((Path entry) -> stamp(entry, prefix)).reversed());
            for (Path stale : copies.subList(Math.min(KEPT_CORRUPT, copies.size()), copies.size())) {
                Failures.runCatchingNonFatal(() -> Files.deleteIfExists(stale));
            }
            return null;
        });
    }

    private static long stamp(Path entry, String prefix) {
        try {
            return Long.parseLong(entry.getFileName().toString().substring(prefix.length()));
        } catch (NumberFormatException error) {
            return 0L;
        }
    }

    private String key(URI uri) {
        return Digests.sha256(uri.toASCIIString().getBytes(StandardCharsets.UTF_8));
    }
}
