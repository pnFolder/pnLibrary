package ru.privatenull.pnlibrary.bootstrap.bukkit;

import java.util.Objects;

/** Immutable settings for embedding the pnLibrary Bukkit bootstrapper. */
public final class BootstrapOptions {
    private final String minimumVersion;
    private final String repositoryOwner;
    private final String repositoryName;
    private final long maximumBytes;

    private BootstrapOptions(Builder builder) {
        this.minimumVersion = BootstrapVersion.normalize(builder.minimumVersion);
        this.repositoryOwner = part(builder.repositoryOwner, "repositoryOwner");
        this.repositoryName = part(builder.repositoryName, "repositoryName");
        if (builder.maximumBytes < 1) throw new IllegalArgumentException("maximumBytes must be positive");
        this.maximumBytes = builder.maximumBytes;
    }

    /** Returns the minimum required library version.
     * @return the minimum pnLibrary version accepted by the embedding plugin */
    public String minimumVersion() { return minimumVersion; }

    /** Returns the configured repository owner.
     * @return the GitHub organization or account that owns the release repository */
    public String repositoryOwner() { return repositoryOwner; }

    /** Returns the configured repository name.
     * @return the GitHub release repository name */
    public String repositoryName() { return repositoryName; }

    /** Returns the download size limit.
     * @return the maximum number of bytes accepted for a downloaded library JAR */
    public long maximumBytes() { return maximumBytes; }

    /**
     * Creates an options builder that requires at least {@code minimumVersion}.
     *
     * @param minimumVersion minimum compatible semantic version
     * @return a new options builder
     */
    public static Builder builder(String minimumVersion) { return new Builder(minimumVersion); }

    private static String part(String value, String field) {
        Objects.requireNonNull(value, field);
        if (!value.matches("[A-Za-z0-9_.-]+")) throw new IllegalArgumentException("invalid " + field + ": " + value);
        return value;
    }

    /** Fluent builder for validated bootstrap options. */
    public static final class Builder {
        private final String minimumVersion;
        private String repositoryOwner = "pnFolder";
        private String repositoryName = "pnLibrary";
        private long maximumBytes = 512L * 1024L * 1024L;

        private Builder(String minimumVersion) { this.minimumVersion = minimumVersion; }

        /**
         * Selects the GitHub repository used to locate pnLibrary releases.
         *
         * @param owner repository organization or account
         * @param name repository name
         * @return this builder
         */
        public Builder repository(String owner, String name) {
            this.repositoryOwner = owner;
            this.repositoryName = name;
            return this;
        }

        /**
         * Limits the accepted download size.
         *
         * @param value positive maximum size in bytes
         * @return this builder
         */
        public Builder maximumBytes(long value) {
            this.maximumBytes = value;
            return this;
        }

        /** Builds the configured options.
         * @return an immutable, validated options instance */
        public BootstrapOptions build() { return new BootstrapOptions(this); }
    }
}
