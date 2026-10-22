package org.pnfolder.pnlibrary.bootstrap;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.util.Locale;

/**
 * The list of releases a repository keeps in {@code .pnlibrary/releases.json}.
 *
 * It is the first place the installer asks which version to fetch and, when a release has an {@code installer} block,
 * where its file is and what its checksum is. GitHub's own releases are used only when the list has nothing suitable.
 * The file format is described in {@code docs/repository.md}.
 */
final class RepositoryCatalog {
    /** Where the list lives inside a repository. */
    static final String PATH = ".pnlibrary/releases.json";

    private RepositoryCatalog() {}

    /** A release of the list, with the file to download when the list describes it completely. */
    static final class Listed {
        private final String version;
        private final boolean stable;
        private final LibraryRelease release;

        private Listed(String version, boolean stable, LibraryRelease release) {
            this.version = version;
            this.stable = stable;
            this.release = release;
        }

        /** @return the version of the release */
        String version() { return version; }

        /** @return whether the list calls the release stable */
        boolean stable() { return stable; }

        /** @return the file to download, or {@code null} when the list does not describe it completely */
        LibraryRelease release() { return release; }
    }

    /**
     * Picks the newest stable release of the list that is at least {@code minimum}.
     *
     * @param json content of {@code releases.json}
     * @param minimum lowest acceptable version
     * @return the version, or {@code null} when the list is empty, malformed or has no such release
     */
    static String newestStable(String json, String minimum) {
        Listed listed = pick(json, minimum, true, Long.MAX_VALUE);
        return listed == null ? null : listed.version();
    }

    /**
     * Picks the newest release of any channel that is at least {@code minimum}; used when there is no stable one.
     *
     * @param json content of {@code releases.json}
     * @param minimum lowest acceptable version
     * @return the version, or {@code null} when the list is empty, malformed or has no such release
     */
    static String newestAny(String json, String minimum) {
        Listed listed = pick(json, minimum, false, Long.MAX_VALUE);
        return listed == null ? null : listed.version();
    }

    /**
     * Picks the newest release of the list that is at least {@code minimum}, with its file when the list has it.
     *
     * @param json content of {@code releases.json}
     * @param minimum lowest acceptable version
     * @param stableOnly {@code true} to consider only stable releases
     * @param maximumBytes the largest file the plugin accepts; a larger described file counts as not described
     * @return the release, or {@code null} when the list is empty, malformed or has no such release
     */
    static Listed pick(String json, String minimum, boolean stableOnly, long maximumBytes) {
        JsonObject root;
        try {
            JsonElement parsed = JsonParser.parseString(json);
            if (!parsed.isJsonObject()) return null;
            root = parsed.getAsJsonObject();
        } catch (RuntimeException invalid) {
            return null;
        }
        JsonElement releases = root.get("releases");
        if (releases == null || !releases.isJsonArray()) return null;

        Listed newest = null;
        for (JsonElement element : releases.getAsJsonArray()) {
            if (!element.isJsonObject()) continue;
            JsonObject release = element.getAsJsonObject();
            String channel = text(release, "channel");
            if (stableOnly && !"stable".equalsIgnoreCase(channel)) continue;
            String version;
            try {
                version = BootstrapVersion.normalize(text(release, "version"));
            } catch (RuntimeException invalid) {
                continue;
            }
            if (!BootstrapVersion.isAtLeast(version, minimum)) continue;
            if (newest != null && BootstrapVersion.isAtLeast(newest.version, version)) continue;
            boolean stable = "stable".equalsIgnoreCase(channel) && !version.contains("-");
            newest = new Listed(version, stable, installer(release, version, stable, maximumBytes));
        }
        return newest;
    }

    /** The {@code installer} block of a release, or {@code null} when it is missing or incomplete. */
    private static LibraryRelease installer(JsonObject release, String version, boolean stable, long maximumBytes) {
        JsonElement block = release.get("installer");
        if (block == null || !block.isJsonObject()) return null;
        JsonObject installer = block.getAsJsonObject();
        String file = text(installer, "file");
        String url = text(installer, "url");
        String sha256 = text(installer, "sha256");
        if (file == null || !BootstrapPlatform.isUniversal(file)) return null;
        if (sha256 == null || !sha256.matches("(?i)^[0-9a-f]{64}$")) return null;
        URI uri;
        try {
            uri = URI.create(url);
        } catch (RuntimeException invalid) {
            return null;
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) return null;
        long size = -1;
        JsonElement declared = installer.get("size");
        if (declared != null && declared.isJsonPrimitive()) {
            try {
                size = declared.getAsLong();
            } catch (RuntimeException invalid) {
                return null;
            }
            if (size < 1 || size > maximumBytes) return null;
        }
        return new LibraryRelease(version, file, uri, sha256.toLowerCase(Locale.ROOT), size, stable);
    }

    private static String text(JsonObject object, String name) {
        JsonElement value = object.get(name);
        return value == null || !value.isJsonPrimitive() ? null : value.getAsString();
    }
}
