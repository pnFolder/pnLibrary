package org.pnfolder.pnlibrary.core.upload;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Objects;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.common.io.TextFiles;

/** Uploads a report to Gofile: first asks which server takes files, then posts the file there. */
public final class GofileUploader implements UploadProvider {
    private static final long MAX_FILE_BYTES = 1024L * 1024L * 1024L;
    private static final Pattern SERVER_NAME = Pattern.compile("[a-z0-9-]{1,32}");

    private final URI serversEndpoint;

    public GofileUploader() {
        this(URI.create("https://api.gofile.io/servers"));
    }

    /** @throws IllegalArgumentException if {@code serversEndpoint} is not HTTPS */
    public GofileUploader(@NotNull URI serversEndpoint) {
        UploadHttp.requireHttps(serversEndpoint, "Gofile endpoint");
        this.serversEndpoint = Objects.requireNonNull(serversEndpoint, "serversEndpoint");
    }

    @Override
    public @NotNull String getBackendId() {
        return "gofile";
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
            throw new IllegalArgumentException("Gofile report size must be between 1 byte and 1 GB");
        }
        URI upload = uploadEndpoint(serverName(fetchServers()));
        String answer = MultipartFileClient.post(upload, Collections.<String, String>emptyMap(), "file", file,
            contentType);
        URI page = UploadedLink.parse("Gofile", downloadPage(answer), "gofile.io");
        return UploadReceipt.permanent(page, UploadedLink.lastSegment(page), getBackendId());
    }

    private String fetchServers() throws IOException {
        HttpURLConnection connection = UploadHttp.open(serversEndpoint, "GET", 10_000, 30_000);
        try {
            int status = connection.getResponseCode();
            if (status != 200) {
                throw new IOException(serversEndpoint.getHost() + " HTTP " + status);
            }
            try (InputStream input = connection.getInputStream()) {
                return new String(UploadHttp.readBounded(input), StandardCharsets.UTF_8);
            }
        } finally {
            connection.disconnect();
        }
    }

    static String serverName(String json) throws IOException {
        try {
            JsonArray servers = JsonParser.parseString(json).getAsJsonObject().getAsJsonObject("data")
                .getAsJsonArray("servers");
            String name = servers.get(0).getAsJsonObject().get("name").getAsString();
            if (SERVER_NAME.matcher(name).matches()) {
                return name;
            }
        } catch (RuntimeException exception) {
            throw new IOException("Gofile sent an unreadable server list", exception);
        }
        throw new IOException("Gofile sent an unusable server name");
    }

    static URI uploadEndpoint(String server) {
        return URI.create("https://" + server + ".gofile.io/contents/uploadfile");
    }

    static String downloadPage(String json) throws IOException {
        if (json.isEmpty()) {
            return json;
        }
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonElement page = root.getAsJsonObject("data").get("downloadPage");
            return page == null ? "" : page.getAsString();
        } catch (RuntimeException exception) {
            throw new IOException("Gofile sent an unreadable answer", exception);
        }
    }
}
