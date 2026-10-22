package org.pnfolder.pnlibrary.bootstrap;

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
    private final Fetcher fetcher;

    /** Where the text of a web address comes from; replaced in tests. */
    interface Fetcher {
        /**
         * Returns the body of {@code url}.
         *
         * @param url the address to read
         * @param accept the media type to ask for
         * @return the text of the answer
         * @throws IOException when the answer is not a success
         */
        String get(String url, String accept) throws IOException;
    }

    GitHubReleaseClient(BootstrapOptions options) { this(options, null); }

    GitHubReleaseClient(BootstrapOptions options, Fetcher fetcher) {
        this.options = options;
        this.fetcher = fetcher != null ? fetcher : this::fetch;
    }

    /**
     * Finds the release to install. Each step only counts a version that is at least the lowest one the plugin asked
     * for (any version, when it asked for none):
     * <ol>
     * <li>the repository's own list {@code .pnlibrary/releases.json}: its newest stable release, or, when it has no
     * stable one, its newest release of any kind (beta, alpha, dev, release candidate);</li>
     * <li>when that list has nothing usable, GitHub's releases directly: the newest stable one, or, when GitHub has
     * no stable one, the newest of any kind;</li>
     * <li>the GitHub release of the exact lowest version, if it was asked for.</li>
     * </ol>
     * The file and its checksum come from the list when it describes them in an {@code installer} block, otherwise
     * from the GitHub release of the chosen version.
     *
     * @return the release to download
     * @throws IOException when GitHub cannot be reached
     * @throws IllegalStateException when the repository has no usable release at all
     */
    LibraryRelease release() throws IOException {
        String root = "https://api.github.com/repos/" + options.repositoryOwner() + "/" + options.repositoryName();
        LibraryRelease listed = fromRepositoryList(root);
        if (listed != null) return listed;
        LibraryRelease onGitHub = newestOfAll(root);
        if (onGitHub != null) return onGitHub;
        if (options.hasMinimum()) {
            try {
                return parse(readJson(root + "/releases/tags/v" + options.minimumVersion()));
            } catch (IOException | RuntimeException absent) {
                // Not even the lowest version exists: report that there is nothing to install.
            }
        }
        throw new IllegalStateException("no " + BootstrapDefaults.PRODUCT_NAME + " release with a verified universal JAR in "
                + options.repositoryOwner() + "/" + options.repositoryName()
                + (options.hasMinimum() ? " at version " + options.minimumVersion() + " or newer" : ""));
    }

    /**
     * The release the repository's own list points at. The file and checksum come from the list when it describes
     * them; otherwise from the GitHub release of that version. {@code null} when the list has nothing usable.
     */
    private LibraryRelease fromRepositoryList(String root) {
        String json = listJson();
        if (json == null) return null;
        RepositoryCatalog.Listed listed = RepositoryCatalog.pick(json, options.minimumVersion(), true, options.maximumBytes());
        if (listed == null) listed = RepositoryCatalog.pick(json, options.minimumVersion(), false, options.maximumBytes());
        if (listed == null) return null;
        if (listed.release() != null) return listed.release();
        try {
            return parse(readJson(root + "/releases/tags/v" + listed.version()));
        } catch (IOException | RuntimeException unavailable) {
            return null;
        }
    }

    /** The newest stable release GitHub lists, or the newest of any kind when it lists no stable one. Drafts never count. */
    private LibraryRelease newestOfAll(String root) throws IOException {
        JsonElement listing = readJsonValue(root + "/releases?per_page=30");
        if (!listing.isJsonArray()) return null;
        LibraryRelease newest = null;
        for (JsonElement element : listing.getAsJsonArray()) {
            if (!element.isJsonObject()) continue;
            JsonObject release = element.getAsJsonObject();
            if (flag(release, "draft")) continue;
            try {
                if (!BootstrapVersion.isAtLeast(version(release), options.minimumVersion())) continue;
                LibraryRelease candidate = parse(release);
                if (newest == null || better(candidate, newest)) newest = candidate;
            } catch (RuntimeException unusable) {
                // A release without a verified universal JAR, or with an odd tag: skip it.
            }
        }
        return newest;
    }

    private static boolean better(LibraryRelease candidate, LibraryRelease current) {
        if (candidate.stable() != current.stable()) return candidate.stable();
        return !BootstrapVersion.isAtLeast(current.version(), candidate.version());
    }

    private static boolean flag(JsonObject value, String name) {
        JsonElement element = value.get(name);
        return element != null && element.isJsonPrimitive() && element.getAsBoolean();
    }

    /** The text of the repository's own list, or {@code null} when it is missing or unreachable. */
    private String listJson() {
        String url = "https://raw.githubusercontent.com/" + options.repositoryOwner() + "/" + options.repositoryName()
                + "/refs/heads/main/" + RepositoryCatalog.PATH;
        try {
            return fetcher.get(url, "application/json");
        } catch (IOException | RuntimeException unavailable) {
            return null;
        }
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

    /** The universal jar of the release; it works on every platform. */
    private LibraryRelease parse(JsonObject release) {
        // Stable means GitHub does not call it a pre-release and the version has no suffix such as -beta.1.
        boolean stable = !flag(release, "prerelease") && !version(release).contains("-");
        JsonArray assets = release.getAsJsonArray("assets");
        if (assets == null) throw new IllegalStateException("GitHub release has no assets");
        for (JsonElement element : assets) {
            JsonObject asset = element.getAsJsonObject();
            String name = string(asset, "name");
            if (!BootstrapPlatform.isUniversal(name)) continue;
            String digest = string(asset, "digest");
            if (digest == null || !digest.matches("(?i)^sha256:[0-9a-f]{64}$")) continue;
            URI uri = URI.create(string(asset, "browser_download_url"));
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) continue;
            long size = asset.has("size") ? asset.get("size").getAsLong() : -1;
            if (size < 1 || size > options.maximumBytes()) continue;
            return new LibraryRelease(version(release), name, uri, digest.substring(7).toLowerCase(Locale.ROOT), size, stable);
        }
        throw new IllegalStateException("release " + version(release) + " has no verified universal pnLibrary JAR");
    }

    private JsonObject readJson(String url) throws IOException {
        JsonElement value = readJsonValue(url);
        if (!value.isJsonObject()) throw new IOException("GitHub returned invalid JSON");
        return value.getAsJsonObject();
    }

    private JsonElement readJsonValue(String url) throws IOException {
        try {
            return JsonParser.parseString(fetcher.get(url, "application/vnd.github+json"));
        } catch (RuntimeException invalid) {
            throw new IOException("GitHub returned invalid JSON", invalid);
        }
    }

    private String fetch(String url, String accept) throws IOException {
        HttpURLConnection connection = open(url, accept);
        try (InputStream input = connection.getInputStream()) {
            return new String(readLimited(input, JSON_LIMIT), StandardCharsets.UTF_8);
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
        connection.setRequestProperty("User-Agent", BootstrapDefaults.PRODUCT_NAME + "-bootstrap");
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
}
