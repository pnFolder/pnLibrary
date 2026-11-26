package org.pnfolder.pnlibrary.core.observability.report;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.common.io.Digests;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Collects the files of one support archive within a size budget and zips them.
 *
 * <p>Content is copied at insertion and {@link #build()} does not mutate state, so repeated builds are equivalent.
 */
public final class SupportArchiveBuilder {
    private static final String CHECKSUM_PATH = "checksums.json";

    private final long maximumUncompressedBytes;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private final Map<String, byte[]> entries = new LinkedHashMap<>();
    private long payloadBytes;

    /** A builder without a size budget. */
    public SupportArchiveBuilder() {
        this(Long.MAX_VALUE);
    }

    /** @param maximumUncompressedBytes the most all entries may hold together; must be positive */
    public SupportArchiveBuilder(long maximumUncompressedBytes) {
        if (maximumUncompressedBytes <= 0) {
            throw new IllegalArgumentException("maximumUncompressedBytes must be positive");
        }
        this.maximumUncompressedBytes = maximumUncompressedBytes;
    }

    /** Adds {@code value} as pretty-printed JSON. */
    public void json(@NotNull String path, @Nullable Object value) {
        bytes(path, gson.toJson(value).getBytes(StandardCharsets.UTF_8));
    }

    /** Adds {@code value} as UTF-8 text. */
    public void text(@NotNull String path, @NotNull String value) {
        bytes(path, value.getBytes(StandardCharsets.UTF_8));
    }

    /** Adds a copy of {@code value}, replacing an entry of the same path. */
    public void bytes(@NotNull String path, @NotNull byte[] value) {
        String safe = safePath(path);
        byte[] previous = entries.get(safe);
        long nextSize = payloadBytes - (previous == null ? 0 : previous.length) + value.length;
        if (nextSize > maximumUncompressedBytes) {
            throw new IllegalArgumentException("Diagnostic payload exceeds configured limit (" + nextSize + " > "
                    + maximumUncompressedBytes + " bytes)");
        }
        entries.put(safe, Arrays.copyOf(value, value.length));
        payloadBytes = nextSize;
    }

    /** The zip of every entry plus a {@code checksums.json} with the SHA-256 of each. */
    @NotNull
    public byte[] build() {
        Map<String, String> checksums = new LinkedHashMap<>();
        for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
            checksums.put(entry.getKey(), Digests.sha256(entry.getValue()));
        }
        Map<String, byte[]> archiveEntries = new LinkedHashMap<>(entries);
        archiveEntries.put(CHECKSUM_PATH, gson.toJson(checksums).getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            for (Map.Entry<String, byte[]> entry : archiveEntries.entrySet()) {
                ZipEntry zipEntry = new ZipEntry("pn-diagnostic/" + entry.getKey());
                zipEntry.setTime(0L);
                zip.putNextEntry(zipEntry);
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
        return output.toByteArray();
    }

    private static String safePath(String path) {
        String safe = path.replace('\\', '/');
        int start = 0;
        while (start < safe.length() && safe.charAt(start) == '/') {
            start++;
        }
        safe = safe.substring(start);
        if (safe.trim().isEmpty() || Arrays.asList(safe.split("/", -1)).contains("..")) {
            throw new IllegalArgumentException("Invalid diagnostic archive path: " + path);
        }
        return safe;
    }
}
