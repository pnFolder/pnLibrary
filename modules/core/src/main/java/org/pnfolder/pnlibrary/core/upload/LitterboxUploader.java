package org.pnfolder.pnlibrary.core.upload;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.common.io.TextFiles;

/** Uploads a report to Litterbox (temporary Catbox storage); the file disappears by itself after 72 hours. */
public final class LitterboxUploader implements UploadProvider {
    private static final long MAX_FILE_BYTES = 1024L * 1024L * 1024L;
    private static final long LIFETIME_SECONDS = 72L * 3600L;

    private final URI endpoint;

    public LitterboxUploader() {
        this(URI.create("https://litterbox.catbox.moe/resources/internals/api.php"));
    }

    /** @throws IllegalArgumentException if {@code endpoint} is not HTTPS */
    public LitterboxUploader(@NotNull URI endpoint) {
        UploadHttp.requireHttps(endpoint, "Litterbox endpoint");
        this.endpoint = Objects.requireNonNull(endpoint, "endpoint");
    }

    @Override
    public @NotNull String getBackendId() {
        return "litterbox";
    }

    @Override
    public @NotNull UploadReceipt upload(@NotNull String payload) throws IOException {
        Path temporary = Files.createTempFile("pnlibrary-report-", ".pnsupport");
        try {
            TextFiles.write(temporary, payload, StandardCharsets.UTF_8);
            return uploadFile(temporary, "application/vnd.pnfolder.support");
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    @Override
    public @NotNull UploadReceipt uploadFile(@NotNull Path file, @NotNull String contentType) throws IOException {
        long size = Files.size(file);
        if (size < 1 || size > MAX_FILE_BYTES) {
            throw new IllegalArgumentException("Litterbox report size must be between 1 byte and 1 GB");
        }
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("reqtype", "fileupload");
        fields.put("time", "72h");
        String answer = MultipartFileClient.post(endpoint, fields, "fileToUpload", file, contentType);
        URI uri = UploadedLink.parse("Litterbox", answer, "litter.catbox.moe");
        long now = Instant.now().getEpochSecond();
        return new UploadReceipt(uri, UploadedLink.lastSegment(uri), "", now, now + LIFETIME_SECONDS, getBackendId());
    }
}
