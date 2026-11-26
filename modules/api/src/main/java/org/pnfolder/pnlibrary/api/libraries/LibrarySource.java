package org.pnfolder.pnlibrary.api.libraries;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * One place a library's jar can come from, with the settings of that place only. A library lists any number of
 * sources; they are tried in order until one gives a jar that passes its check.
 *
 * <pre>{@code
 * LibrarySource.builder().link("https://cdn.example.org/a-1.0.jar").sha256("3c0e...").name("CDN").build();
 * LibrarySource.builder().repository("https://repo.example.org/maven2/").build(); // Maven path under this repository
 * }</pre>
 */
public final class LibrarySource {
    private static final Pattern SHA256 = Pattern.compile("[0-9A-Fa-f]{64}");

    private final String url;
    private final boolean direct;
    private final String sha256;
    private final String name;
    private final String description;

    private LibrarySource(Builder builder) {
        if (builder.url == null) {
            throw new IllegalStateException("A source needs a link or a repository");
        }
        this.url = builder.url;
        this.direct = builder.direct;
        this.sha256 = builder.sha256;
        this.name = builder.name != null ? builder.name : URI.create(url).getHost();
        this.description = builder.description;
    }

    /** Starts a source; set a {@link Builder#link} or a {@link Builder#repository}. */
    @NotNull
    public static Builder builder() {
        return new Builder();
    }

    /** HTTPS address: a direct link to the jar, or the root of a Maven repository, see {@link #isDirect()}. */
    @NotNull
    public String getUrl() {
        return url;
    }

    /** {@code true} for a link to the jar itself, {@code false} for a Maven repository the path is built under. */
    public boolean isDirect() {
        return direct;
    }

    /** The sum the jar from this source must have; {@code null} means the jar from this source is not checked. */
    @Nullable
    public String getSha256() {
        return sha256;
    }

    /** Short label shown in errors and in the menu; the host of the address when not given. */
    @NotNull
    public String getName() {
        return name;
    }

    /** A sentence on what this place is, such as {@code the main repository of the company}; {@code null} when not given. */
    @Nullable
    public String getDescription() {
        return description;
    }

    static String https(String value, String what) {
        URI uri = URI.create(value.trim());
        if (uri.getScheme() == null || !uri.getScheme().equalsIgnoreCase("https") || uri.getHost() == null || uri.getHost().trim().isEmpty()) {
            throw new IllegalArgumentException(what + " must be an HTTPS link: " + value);
        }
        return uri.toASCIIString();
    }

    @NotNull
    @Override
    public String toString() {
        return name + " (" + url + ")";
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof LibrarySource)) {
            return false;
        }
        LibrarySource that = (LibrarySource) other;
        return that.url.equals(url) && that.direct == direct && java.util.Objects.equals(that.sha256, sha256);
    }

    @Override
    public int hashCode() {
        return (url.hashCode() * 31 + Boolean.hashCode(direct)) * 31 + (sha256 == null ? 0 : sha256.hashCode());
    }

    /** Builder; {@link #build} checks that an address was given. */
    public static final class Builder {
        private String url;
        private boolean direct;
        private String sha256;
        private String name;
        private String description;

        Builder() {
        }

        /** The jar lies at this exact HTTPS link. */
        @NotNull
        public Builder link(@NotNull String url) {
            this.url = https(url, "Library link");
            this.direct = true;
            return this;
        }

        /** The jar is looked up by its Maven path under this HTTPS repository. */
        @NotNull
        public Builder repository(@NotNull String url) {
            this.url = https(url, "Repository");
            this.direct = false;
            return this;
        }

        /** The sum the jar from this source must have, 64 hexadecimal characters. */
        @NotNull
        public Builder sha256(@NotNull String hex) {
            if (!SHA256.matcher(hex).matches()) {
                throw new IllegalArgumentException("SHA-256 must be 64 hexadecimal characters");
            }
            this.sha256 = hex.toLowerCase(Locale.ROOT);
            return this;
        }

        /** Gives the source a short label. */
        @NotNull
        public Builder name(@NotNull String value) {
            this.name = blankToNull(value);
            return this;
        }

        /** Says in a sentence what this place is. */
        @NotNull
        public Builder description(@NotNull String value) {
            this.description = blankToNull(value);
            return this;
        }

        /** Creates the source. */
        @NotNull
        public LibrarySource build() {
            return new LibrarySource(this);
        }
    }

    static String blankToNull(String value) {
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
