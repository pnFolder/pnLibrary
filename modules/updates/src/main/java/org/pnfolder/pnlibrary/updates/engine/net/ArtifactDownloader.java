package org.pnfolder.pnlibrary.updates.engine.net;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.common.io.AtomicFiles;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/** Streams artifacts into atomic temporary files under a strict byte limit. */
public final class ArtifactDownloader {
    private final long maximumBytes;

    public ArtifactDownloader(long maximumBytes) {
        if (maximumBytes <= 0) {
            throw new IllegalArgumentException("maximumBytes must be positive");
        }
        this.maximumBytes = maximumBytes;
    }

    /** Downloads into {@code destination} and returns the number of bytes written. */
    public long download(@NotNull InputStreamSource openStream, @NotNull Path destination) throws IOException {
        Path absolute = destination.toAbsolutePath().normalize();
        Path parent = absolute.getParent();
        if (parent == null) {
            throw new ArtifactDownloadException("Download destination must have a parent");
        }
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, absolute.getFileName().toString(), ".part");
        try {
            long total;
            try (InputStream input = openStream.open(); OutputStream output = Files.newOutputStream(temporary)) {
                total = copyBounded(input, output);
            }
            AtomicFiles.move(temporary, absolute);
            return total;
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private long copyBounded(InputStream input, OutputStream output) throws IOException {
        long total = 0L;
        byte[] buffer = new byte[8192];
        while (true) {
            int count = input.read(buffer);
            if (count < 0) {
                return total;
            }
            total += count;
            if (total > maximumBytes) {
                throw new ArtifactDownloadException("Download exceeds the configured size limit");
            }
            output.write(buffer, 0, count);
        }
    }
}
