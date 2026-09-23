package ru.privatenull.pnlibrary.bootstrap.bukkit;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

final class GitHubReleaseClient {
    private static final int JSON_LIMIT = 2 * 1024 * 1024;
    private final BootstrapOptions options;

    GitHubReleaseClient(BootstrapOptions options) { this.options = options; }

    Release release() throws IOException {
        String root = "https://api.github.com/repos/" + options.repositoryOwner() + "/" + options.repositoryName();
        JsonObject stable = readJson(root + "/releases/latest");
        String stableVersion = version(stable);
        if (BootstrapVersion.isAtLeast(stableVersion, options.minimumVersion())) return parse(stable, true);
        return parse(readJson(root + "/releases/tags/v" + options.minimumVersion()), false);
    }

    void download(URI uri, java.nio.file.Path target) throws IOException {
        HttpURLConnection connection = open(uri.toString(), "application/octet-stream");
        try {
            long declared = connection.getContentLengthLong();
            if (declared > options.maximumBytes()) throw new IOException("pnLibrary exceeds configured size limit");
            try (InputStream input = connection.getInputStream(); java.io.OutputStream output = java.nio.file.Files.newOutputStream(target)) {
                byte[] buffer = new byte[16 * 1024];
                long total = 0;
                int count;
                while ((count = input.read(buffer)) != -1) {
                    total += count;
                    if (total > options.maximumBytes()) throw new IOException("pnLibrary exceeds configured size limit");
                    output.write(buffer, 0, count);
                }
            }
        } finally {
            connection.disconnect();
        }
    }

    private Release parse(JsonObject release, boolean stable) {
        JsonArray assets = release.getAsJsonArray("assets");
        if (assets == null) throw new IllegalStateException("GitHub release has no assets");
        for (JsonElement element : assets) {
            JsonObject asset = element.getAsJsonObject();
            String name = string(asset, "name");
            if (!name.matches("(?i)^pnLibrary-.*-bukkit-.*\\.jar$")) continue;
            String digest = string(asset, "digest");
            if (digest == null || !digest.matches("(?i)^sha256:[0-9a-f]{64}$")) continue;
            URI uri = URI.create(string(asset, "browser_download_url"));
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) continue;
            long size = asset.has("size") ? asset.get("size").getAsLong() : -1;
            if (size < 1 || size > options.maximumBytes()) continue;
            return new Release(version(release), name, uri, digest.substring(7).toLowerCase(Locale.ROOT), size, stable);
        }
        throw new IllegalStateException("release has no verified Bukkit pnLibrary JAR");
    }

    private JsonObject readJson(String url) throws IOException {
        HttpURLConnection connection = open(url, "application/vnd.github+json");
        try (InputStream input = connection.getInputStream()) {
            byte[] bytes = readLimited(input, JSON_LIMIT);
            JsonElement value = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8));
            if (!value.isJsonObject()) throw new IOException("GitHub returned invalid JSON");
            return value.getAsJsonObject();
        } finally {
            connection.disconnect();
        }
    }

    private HttpURLConnection open(String url, String accept) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
        connection.setConnectTimeout(8_000);
        connection.setReadTimeout(30_000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("Accept", accept);
        connection.setRequestProperty("User-Agent", "pnLibrary-bootstrap-bukkit");
        int status = connection.getResponseCode();
        if (status < 200 || status >= 300) {
            connection.disconnect();
            throw new IOException("GitHub returned HTTP " + status);
        }
        return connection;
    }

    private static byte[] readLimited(InputStream input, int limit) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        int count;
        while ((count = input.read(buffer)) != -1) {
            total += count;
            if (total > limit) throw new IOException("GitHub response is too large");
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }

    private static String version(JsonObject value) {
        String tag = string(value, "tag_name");
        if (tag == null) throw new IllegalStateException("release tag is missing");
        return BootstrapVersion.normalize(tag);
    }

    private static String string(JsonObject value, String name) {
        JsonElement element = value.get(name);
        return element == null || element.isJsonNull() ? null : element.getAsString();
    }

    static final class Release {
        final String version;
        final String fileName;
        final URI uri;
        final String sha256;
        final long size;
        final boolean stable;

        Release(String version, String fileName, URI uri, String sha256, long size, boolean stable) {
            this.version = version;
            this.fileName = fileName;
            this.uri = uri;
            this.sha256 = sha256;
            this.size = size;
            this.stable = stable;
        }
    }
}
