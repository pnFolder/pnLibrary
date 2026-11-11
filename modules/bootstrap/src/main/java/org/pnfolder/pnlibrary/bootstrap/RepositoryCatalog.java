package org.pnfolder.pnlibrary.bootstrap;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The list of releases a repository keeps in {@code .pnlibrary/releases.json}.
 *
 * It is the first place the installer asks which version to fetch, and its {@code artifacts} entries are where the
 * file, size and checksum for this platform come from: the same list the already-running pnLibrary reads for its own
 * updates. GitHub's own releases are used only when the list has nothing suitable. The file format is described in
 * {@code docs/repository.md}.
 */
final class RepositoryCatalog {
    /** Where the list lives inside a repository. */
    static final String PATH = ".pnlibrary/releases.json";

    private static final Pattern SAFE_FILE = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]*\\.jar");
    private static final Pattern SHA256 = Pattern.compile("(?i)^[0-9a-f]{64}$");

    private RepositoryCatalog() {}

    /** A release of the list, with the file to download when the list describes one that fits. */
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

        /** @return the artifact to download, or {@code null} when the list does not describe one that fits */
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
        Listed listed = pick(json, minimum, true, Long.MAX_VALUE, null, 0);
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
        Listed listed = pick(json, minimum, false, Long.MAX_VALUE, null, 0);
        return listed == null ? null : listed.version();
    }

    /**
     * Picks the newest release of the list that is at least {@code minimum}, with the artifact that fits
     * {@code platform} and {@code javaFeature} when the list describes one.
     *
     * @param json content of {@code releases.json}
     * @param minimum lowest acceptable version
     * @param stableOnly {@code true} to consider only stable releases
     * @param maximumBytes the largest file the plugin accepts; a larger described artifact counts as not described
     * @param platform the installer's own platform, or {@code null} to only look up the version
     * @param javaFeature the running JVM's feature version, used to match an artifact's {@code java} range
     * @return the release, or {@code null} when the list is empty, malformed or has no such release
     */
    static Listed pick(String json, String minimum, boolean stableOnly, long maximumBytes, BootstrapPlatform platform, int javaFeature) {
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
            LibraryRelease artifact = platform == null ? null : artifact(release, version, stable, maximumBytes, platform, javaFeature);
            newest = new Listed(version, stable, artifact);
        }
        return newest;
    }

    /**
     * The best {@code artifacts} entry of a release for {@code platform} and {@code javaFeature}: an entry for the
     * exact platform first, one for {@code UNIVERSAL} otherwise, {@code null} when neither is described completely.
     */
    private static LibraryRelease artifact(
            JsonObject release, String version, boolean stable, long maximumBytes, BootstrapPlatform platform, int javaFeature) {
        JsonElement array = release.get("artifacts");
        if (array == null || !array.isJsonArray()) return null;
        LibraryRelease universal = null;
        for (JsonElement element : array.getAsJsonArray()) {
            if (!element.isJsonObject()) continue;
            JsonObject entry = element.getAsJsonObject();
            String entryPlatform = text(entry, "platform");
            if (entryPlatform == null || !fitsJava(entry, javaFeature)) continue;
            boolean exact = platform.name().equalsIgnoreCase(entryPlatform);
            boolean universalEntry = !exact && "UNIVERSAL".equalsIgnoreCase(entryPlatform);
            if (!exact && !universalEntry) continue;
            LibraryRelease candidate = decodeArtifact(entry, version, stable, maximumBytes, universalEntry);
            if (candidate == null) continue;
            if (exact) return candidate;
            if (universal == null) universal = candidate;
        }
        return universal;
    }

    /** Whether an artifact entry's optional {@code java} range includes {@code javaFeature}; absent means any version. */
    private static boolean fitsJava(JsonObject entry, int javaFeature) {
        JsonElement javaElement = entry.get("java");
        if (javaElement == null) return true;
        if (!javaElement.isJsonObject()) return false;
        JsonObject java = javaElement.getAsJsonObject();
        Long minimum = number(java, "minimum");
        Long maximum = number(java, "maximum");
        if (minimum != null && javaFeature < minimum) return false;
        if (maximum != null && javaFeature > maximum) return false;
        return true;
    }

    /**
     * One {@code artifacts} entry, verified complete, or {@code null} when it is missing a required field.
     *
     * @param requireUniversalName {@code true} for a {@code UNIVERSAL} entry, whose file must match the universal
     * jar name pattern; a platform-specific entry only needs a safe jar filename, checked against the downloaded
     * jar's own descriptor when it is installed
     */
    private static LibraryRelease decodeArtifact(
            JsonObject entry, String version, boolean stable, long maximumBytes, boolean requireUniversalName) {
        String file = text(entry, "file");
        String url = text(entry, "url");
        String sha256 = text(entry, "sha256");
        if (file == null || !SAFE_FILE.matcher(file).matches()) return null;
        if (requireUniversalName && !BootstrapPlatform.isUniversal(file)) return null;
        if (sha256 == null || !SHA256.matcher(sha256).matches()) return null;
        URI uri;
        try {
            uri = URI.create(url);
        } catch (RuntimeException invalid) {
            return null;
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) return null;
        long size = -1;
        JsonElement declared = entry.get("size");
        if (declared != null && declared.isJsonPrimitive()) {
            Long parsed = number(entry, "size");
            if (parsed == null) return null;
            size = parsed;
            if (size < 1 || size > maximumBytes) return null;
        }
        return new LibraryRelease(version, file, uri, sha256.toLowerCase(Locale.ROOT), size, stable);
    }

    private static Long number(JsonObject object, String name) {
        JsonElement value = object.get(name);
        if (value == null || !value.isJsonPrimitive()) return null;
        try {
            return value.getAsLong();
        } catch (RuntimeException invalid) {
            return null;
        }
    }

    private static String text(JsonObject object, String name) {
        JsonElement value = object.get(name);
        return value == null || !value.isJsonPrimitive() ? null : value.getAsString();
    }
}
