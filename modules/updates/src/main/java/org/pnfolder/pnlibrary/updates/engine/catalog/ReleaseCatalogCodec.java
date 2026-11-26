package org.pnfolder.pnlibrary.updates.engine.catalog;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.platform.PlatformType;
import org.pnfolder.pnlibrary.api.updates.product.UpdateChannel;
import org.pnfolder.pnlibrary.api.version.ApiVersionRange;
import org.pnfolder.pnlibrary.api.version.SemanticVersion;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import static org.pnfolder.pnlibrary.updates.engine.catalog.CatalogJson.fail;

/** Strict schema-1 JSON decoder for bounded release catalogs. */
public final class ReleaseCatalogCodec {
    /** Supported catalog schema generation. */
    public static final int SCHEMA = 1;
    /** Default maximum encoded catalog size. */
    public static final int MAX_BYTES = 1024 * 1024;

    private static final Pattern SAFE_FILE = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]*");
    private static final Pattern SHA256 = Pattern.compile("[0-9a-fA-F]{64}");

    private final int maximumBytes;

    public ReleaseCatalogCodec() {
        this(MAX_BYTES);
    }

    public ReleaseCatalogCodec(int maximumBytes) {
        this.maximumBytes = maximumBytes;
    }

    /** Decodes and validates catalog {@code bytes}. */
    @NotNull
    public ReleaseCatalog decode(@NotNull byte[] bytes) {
        if (bytes.length > maximumBytes) {
            throw new IllegalArgumentException("Release catalog exceeds " + maximumBytes + " bytes");
        }
        JsonObject root = parseRoot(bytes);
        int schema = CatalogJson.requiredInt(root, "schema");
        if (schema != SCHEMA) {
            throw fail("schema", "unsupported schema " + schema);
        }
        String product = CatalogJson.requiredString(root, "product");
        List<CatalogRelease> releases = new ArrayList<CatalogRelease>();
        int index = 0;
        for (JsonElement value : CatalogJson.requiredArray(root, "releases")) {
            releases.add(decodeRelease(value.getAsJsonObject(), "releases[" + index++ + "]"));
        }
        Set<SemanticVersion> versions = new HashSet<SemanticVersion>();
        for (CatalogRelease release : releases) {
            versions.add(release.getVersion());
        }
        if (versions.size() != releases.size()) {
            throw new IllegalArgumentException("Release catalog contains duplicate versions");
        }
        return new ReleaseCatalog(product, releases);
    }

    private JsonObject parseRoot(byte[] bytes) {
        try {
            return JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception error) {
            throw fail("root", "malformed JSON", error);
        }
    }

    private CatalogRelease decodeRelease(JsonObject value, String field) {
        try {
            List<CatalogArtifact> artifacts = new ArrayList<CatalogArtifact>();
            int index = 0;
            for (JsonElement raw : CatalogJson.requiredArray(value, "artifacts")) {
                artifacts.add(decodeArtifact(raw.getAsJsonObject(), field + ".artifacts[" + index++ + "]"));
            }
            if (artifacts.isEmpty()) {
                throw new IllegalArgumentException(field + ".artifacts must not be empty");
            }
            SemanticVersion version = SemanticVersion.parse(CatalogJson.requiredString(value, "version"));
            UpdateChannel channel = channel(CatalogJson.requiredString(value, "channel"));
            return new CatalogRelease(version, channel, CatalogJson.requiredString(value, "description"),
                CatalogJson.optionalHttpsUrl(value, "changelogUrl"),
                Instant.parse(CatalogJson.requiredString(value, "publishedAt")),
                apiRange(CatalogJson.requiredObject(value, "api")), artifacts);
        } catch (ManifestException error) {
            throw error;
        } catch (Exception error) {
            throw fail(field, error.getMessage() == null ? "invalid release" : error.getMessage(), error);
        }
    }

    private CatalogArtifact decodeArtifact(JsonObject value, String field) {
        String file = CatalogJson.requiredString(value, "file");
        if (!SAFE_FILE.matcher(file).matches() || !file.toLowerCase(Locale.ROOT).endsWith(".jar")) {
            throw new IllegalArgumentException(field + ".file is not a safe JAR filename");
        }
        PlatformType platform = platform(value, field);
        // Absent only for a UNIVERSAL artifact that imposes no Java restriction beyond the library's own floor.
        JsonObject java = value.has("java") ? CatalogJson.requiredObject(value, "java") : null;
        int minimum = java == null ? 8 : CatalogJson.requiredInt(java, "minimum");
        Integer maximum = java == null ? null : CatalogJson.optionalInt(java, "maximum");
        if (minimum < 8 || (maximum != null && maximum < minimum)) {
            throw new IllegalArgumentException(field + ".java range is invalid");
        }
        VersionRangeText minecraft = CatalogJson.optionalRange(value, "minecraft");
        VersionRangeText platformApi = CatalogJson.optionalRange(value, "platformApi");
        requireRangesFit(platform, minecraft, platformApi, field);
        URI url = url(value, field);
        String sha256 = sha256(value, field);
        Long size = CatalogJson.optionalLong(value, "size");
        if (size != null && size <= 0) {
            throw new IllegalArgumentException(field + ".size must be positive");
        }
        return new CatalogArtifact(file, platform, minecraft, platformApi, minimum, maximum, url, sha256, size);
    }

    private static String sha256(JsonObject value, String field) {
        String sha256 = CatalogJson.optionalString(value, "sha256");
        if (sha256 == null) {
            return null;
        }
        if (!SHA256.matcher(sha256).matches()) {
            throw new IllegalArgumentException(field + ".sha256 must have 64 hexadecimal characters");
        }
        return sha256.toLowerCase(Locale.ROOT);
    }

    private static void requireRangesFit(PlatformType platform, VersionRangeText minecraft, VersionRangeText platformApi,
                                         String field) {
        if (platform == PlatformType.UNIVERSAL) {
            if (minecraft != null || platformApi != null) {
                throw new IllegalArgumentException(field + ": a UNIVERSAL artifact must not declare minecraft or platformApi");
            }
        } else {
            if (minecraft != null && platform.isProxy()) {
                throw new IllegalArgumentException(field + ".minecraft is only valid for server platforms");
            }
            if (platformApi != null && platform.isServer()) {
                throw new IllegalArgumentException(field + ".platformApi is only valid for proxy platforms");
            }
        }
    }

    private static PlatformType platform(JsonObject value, String field) {
        try {
            return PlatformType.valueOf(CatalogJson.requiredString(value, "platform").toUpperCase(Locale.ROOT));
        } catch (Exception error) {
            throw fail(field + ".platform", "unknown platform", error);
        }
    }

    private static URI url(JsonObject value, String field) {
        URI url;
        try {
            url = new URI(CatalogJson.requiredString(value, "url"));
        } catch (Exception error) {
            throw fail(field + ".url", "invalid URL", error);
        }
        if (!"https".equalsIgnoreCase(url.getScheme())) {
            throw new IllegalArgumentException(field + ".url must use HTTPS");
        }
        return url;
    }

    private static ApiVersionRange apiRange(JsonObject value) {
        return new ApiVersionRange(CatalogJson.requiredInt(value, "minimum"), CatalogJson.requiredInt(value, "maximum"));
    }

    private static UpdateChannel channel(String value) {
        try {
            return UpdateChannel.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (Exception error) {
            throw fail("channel", "unknown channel", error);
        }
    }
}
