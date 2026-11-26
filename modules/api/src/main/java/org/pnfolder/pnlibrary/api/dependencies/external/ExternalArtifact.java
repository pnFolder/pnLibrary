package org.pnfolder.pnlibrary.api.dependencies.external;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.platform.PlatformType;
import org.pnfolder.pnlibrary.api.updates.UpdateValidation;

/**
 * One downloadable file of an external plugin, with its own checksum and the servers it is meant for.
 *
 * <p>The checksum belongs to this file only: when this variant is chosen, exactly this file is downloaded and checked
 * against exactly this SHA-256. Variants for other platforms or versions are never touched.
 */
public final class ExternalArtifact {
    private static final Pattern VERSION_NUMBERS = Pattern.compile("^\\d+(\\.\\d+)*");

    private final URI uri;
    private final String sha256;
    private final List<URI> mirrors;
    private final Set<PlatformType> platforms;
    private final String minecraftFrom;
    private final String minecraftBefore;
    private final String label;
    private final Long maxBytes;

    /** Creates a file that fits every server, with no mirrors and the library-wide size limit. */
    public ExternalArtifact(@NotNull URI uri, @Nullable String sha256) {
        this(uri, sha256, Collections.emptyList(), Collections.emptySet(), null, null, null, null);
    }

    /**
     * Creates a file.
     *
     * @param uri verified HTTPS artifact location
     * @param sha256 expected SHA-256 digest, when known
     * @param mirrors other HTTPS addresses of the same file, tried in order when the one before does not answer
     * @param platforms platforms this file fits; empty means every platform
     * @param minecraftFrom oldest Minecraft version this file fits (inclusive), such as {@code 1.16}; null means no lower bound
     * @param minecraftBefore first Minecraft version this file no longer fits (exclusive), such as {@code 1.21}; null means no upper bound
     * @param label short name of the variant for people, such as {@code для Paper 1.21}
     * @param maxBytes largest accepted size of this file in bytes; null means the library-wide limit
     */
    public ExternalArtifact(@NotNull URI uri, @Nullable String sha256, @NotNull List<URI> mirrors,
                            @NotNull Set<PlatformType> platforms, @Nullable String minecraftFrom,
                            @Nullable String minecraftBefore, @Nullable String label, @Nullable Long maxBytes) {
        if (!UpdateValidation.isHttps(uri)) {
            throw new IllegalArgumentException("External artifact must use HTTPS");
        }
        UpdateValidation.requireSha256(sha256, "External artifact");
        if (maxBytes != null && (maxBytes < 1 || maxBytes > Integer.MAX_VALUE)) {
            throw new IllegalArgumentException("Size limit must be between 1 byte and 2 GB");
        }
        for (URI mirror : mirrors) {
            if (!UpdateValidation.isHttps(mirror)) {
                throw new IllegalArgumentException("External artifact mirrors must use HTTPS");
            }
        }
        requireVersion(minecraftFrom);
        requireVersion(minecraftBefore);
        this.uri = uri;
        this.sha256 = sha256;
        this.mirrors = UpdateValidation.frozen(mirrors);
        this.platforms = Collections.unmodifiableSet(new LinkedHashSet<>(platforms));
        this.minecraftFrom = minecraftFrom;
        this.minecraftBefore = minecraftBefore;
        this.label = label;
        this.maxBytes = maxBytes;
    }

    /** The general file (no platform or version limit) or else the first one; null when there are none. */
    static ExternalArtifact general(List<ExternalArtifact> files) {
        for (ExternalArtifact file : files) {
            if (file.platforms.isEmpty() && file.minecraftFrom == null && file.minecraftBefore == null) {
                return file;
            }
        }
        return files.isEmpty() ? null : files.get(0);
    }

    /** A file with the same address and checksum, now the general one: the given mirrors and size limit. */
    ExternalArtifact withMirrorsAndLimit(List<URI> mirrors, Long maxBytes) {
        return new ExternalArtifact(uri, sha256, mirrors, Collections.emptySet(), null, null, null,
            maxBytes);
    }

    /** A variant described with plain text addresses; every mirror must be HTTPS. */
    static ExternalArtifact variant(String url, String sha256, Set<PlatformType> platforms, String minecraftFrom,
                                    String minecraftBefore, List<String> mirrors, String label, Long maxBytes) {
        List<URI> mirrorUris = new ArrayList<>();
        for (String mirror : mirrors) {
            mirrorUris.add(UpdateValidation.requireHttpsUri(mirror, "Mirror must use HTTPS"));
        }
        return new ExternalArtifact(URI.create(url), sha256, mirrorUris, platforms, minecraftFrom, minecraftBefore,
            label, maxBytes);
    }

    private static void requireVersion(String text) {
        if (text != null && numbers(text) == null) {
            throw new IllegalArgumentException("Minecraft version must look like 1.20.4: " + text);
        }
    }

    /** Verified HTTPS artifact location. */
    @NotNull
    public URI getUri() {
        return uri;
    }

    /** Expected SHA-256 digest, when known. */
    @Nullable
    public String getSha256() {
        return sha256;
    }

    /** Other HTTPS addresses of the same file, tried in order when the one before does not answer. */
    @NotNull
    public List<URI> getMirrors() {
        return mirrors;
    }

    /** Platforms this file fits; empty means every platform. */
    @NotNull
    public Set<PlatformType> getPlatforms() {
        return platforms;
    }

    /** Oldest Minecraft version this file fits (inclusive); null means no lower bound. */
    @Nullable
    public String getMinecraftFrom() {
        return minecraftFrom;
    }

    /** First Minecraft version this file no longer fits (exclusive); null means no upper bound. */
    @Nullable
    public String getMinecraftBefore() {
        return minecraftBefore;
    }

    /** Short name of the variant for people; null when none was given. */
    @Nullable
    public String getLabel() {
        return label;
    }

    /** Largest accepted size of this file in bytes; null means the library-wide limit. */
    @Nullable
    public Long getMaxBytes() {
        return maxBytes;
    }

    /** Whether this file fits a server of {@code platform} running Minecraft {@code minecraft} (null when unknown). */
    public boolean fits(@NotNull PlatformType platform, @Nullable String minecraft) {
        if (!platforms.isEmpty() && !platforms.contains(platform)) {
            return false;
        }
        if (minecraftFrom == null && minecraftBefore == null) {
            return true;
        }
        List<Integer> current = minecraft == null ? null : numbers(minecraft);
        if (current == null) {
            return false;
        }
        if (minecraftFrom != null && compare(current, numbers(minecraftFrom)) < 0) {
            return false;
        }
        return minecraftBefore == null || compare(current, numbers(minecraftBefore)) < 0;
    }

    private static List<Integer> numbers(String text) {
        Matcher matcher = VERSION_NUMBERS.matcher(text.trim());
        if (!matcher.find()) {
            return null;
        }
        List<Integer> result = new ArrayList<>();
        for (String part : matcher.group().split("\\.")) {
            try {
                result.add(Integer.parseInt(part));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return result;
    }

    private static int compare(List<Integer> left, List<Integer> right) {
        for (int i = 0; i < Math.max(left.size(), right.size()); i++) {
            int a = i < left.size() ? left.get(i) : 0;
            int b = i < right.size() ? right.get(i) : 0;
            if (a != b) {
                return a < b ? -1 : 1;
            }
        }
        return 0;
    }
}
