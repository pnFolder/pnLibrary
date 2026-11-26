package org.pnfolder.pnlibrary.core.upload;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.common.io.TextFiles;

/** Uploads an encrypted {@code .pnsupport} file through Catbox's multipart API. */
public final class CatboxUploader implements UploadProvider {
    private static final long MAX_FILE_BYTES = 200L * 1024L * 1024L;

    private final URI endpoint;

    public CatboxUploader() {
        this(URI.create("https://catbox.moe/user/api.php"));
    }

    /**
     * @param endpoint HTTPS API endpoint; configurable primarily for integration tests
     * @throws IllegalArgumentException if {@code endpoint} is not HTTPS
     */
    public CatboxUploader(@NotNull URI endpoint) {
        UploadHttp.requireHttps(endpoint, "Catbox endpoint");
        this.endpoint = endpoint;
    }

    @Override
    public @NotNull String getBackendId() {
        return "catbox";
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
        if (!Files.isRegularFile(file)) {
            throw new IllegalArgumentException("Diagnostic report is not a regular file: " + file);
        }
        long size = Files.size(file);
        if (size < 1 || size > MAX_FILE_BYTES) {
            throw new IllegalArgumentException("Catbox report size must be between 1 byte and 200 MB");
        }
        String link = MultipartFileClient.post(endpoint, Collections.singletonMap("reqtype", "fileupload"),
            "fileToUpload", file, contentType);
        URI uri;
        try {
            uri = URI.create(link);
        } catch (IllegalArgumentException exception) {
            throw new IOException("Invalid Catbox response", exception);
        }
        String path = uri.getPath();
        if (!"https".equals(uri.getScheme()) || !"files.catbox.moe".equals(uri.getHost())
            || path == null || path.trim().isEmpty()) {
            throw new IOException("Catbox rejected the report: " + (link.length() > 256 ? link.substring(0, 256) : link));
        }
        return UploadReceipt.permanent(uri, path.substring(path.lastIndexOf('/') + 1), getBackendId());
    }
}
