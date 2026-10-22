package org.pnfolder.pnlibrary.bootstrap;

import java.util.Objects;

/** Immutable settings of the embedded pnLibrary installer, shared by every platform. */
public final class BootstrapOptions {
    private final String minimumVersion;
    private final boolean hasMinimum;
    private final String repositoryOwner;
    private final String repositoryName;
    private final long maximumBytes;
    private final int restartCountdown;
    private final String supportUrl;

    private BootstrapOptions(Builder builder) {
        this.hasMinimum = builder.minimumVersion != null && !builder.minimumVersion.trim().isEmpty();
        this.minimumVersion = hasMinimum ? BootstrapVersion.normalize(builder.minimumVersion) : "0.0.0";
        this.repositoryOwner = part(builder.repositoryOwner, "repositoryOwner");
        this.repositoryName = part(builder.repositoryName, "repositoryName");
        if (builder.maximumBytes < 1) throw new IllegalArgumentException("maximumBytes must be positive");
        this.maximumBytes = builder.maximumBytes;
        if (builder.restartCountdown < 0 || builder.restartCountdown > 300) {
            throw new IllegalArgumentException("restartCountdown must be between 0 and 300 seconds");
        }
        this.restartCountdown = builder.restartCountdown;
        if (!builder.supportUrl.startsWith("https://")) throw new IllegalArgumentException("support link must be an HTTPS URL");
        this.supportUrl = builder.supportUrl;
    }

    /** Returns the minimum required library version.
     * @return the minimum pnLibrary version accepted by the embedding plugin, {@code 0.0.0} when any version will do */
    public String minimumVersion() { return minimumVersion; }

    /** Tells whether the embedding plugin named a lowest version.
     * @return {@code false} when any version will do, so the newest stable release is installed */
    public boolean hasMinimum() { return hasMinimum; }

    /** Returns the configured repository owner.
     * @return the GitHub organization or account that owns the release repository */
    public String repositoryOwner() { return repositoryOwner; }

    /** Returns the configured repository name.
     * @return the GitHub release repository name */
    public String repositoryName() { return repositoryName; }

    /** Returns the download size limit.
     * @return the maximum number of bytes accepted for a downloaded library JAR */
    public long maximumBytes() { return maximumBytes; }

    /** Returns the countdown before a proxy stops itself after installing pnLibrary.
     * @return seconds, or {@code 0} when the proxy is never stopped automatically */
    public int restartCountdown() { return restartCountdown; }

    /** Returns where administrators get help when pnLibrary cannot be installed.
     * @return an HTTPS link, the pnFolder Discord by default */
    public String supportUrl() { return supportUrl; }

    /**
     * Creates an options builder that requires at least {@code minimumVersion}.
     *
     * @param minimumVersion minimum compatible semantic version; {@code null} or blank when any version will do
     * @return a new options builder
     */
    public static Builder builder(String minimumVersion) { return new Builder(minimumVersion); }

    /**
     * Creates an options builder for a plugin that works with any pnLibrary version: the newest stable release is
     * installed, or the newest release of any kind when there is no stable one.
     *
     * @return a new options builder
     */
    public static Builder builder() { return new Builder(null); }

    private static String part(String value, String field) {
        Objects.requireNonNull(value, field);
        if (!value.matches("[A-Za-z0-9_.-]+")) throw new IllegalArgumentException("invalid " + field + ": " + value);
        return value;
    }

    /** Fluent builder for validated bootstrap options. */
    public static final class Builder {
        private final String minimumVersion;
        private String repositoryOwner = BootstrapDefaults.REPOSITORY_OWNER;
        private String repositoryName = BootstrapDefaults.REPOSITORY_NAME;
        private long maximumBytes = 512L * 1024L * 1024L;
        private int restartCountdown;
        private String supportUrl = BootstrapDefaults.SUPPORT_URL;

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

        /**
         * On BungeeCord and Velocity, stops the proxy {@code seconds} after pnLibrary was downloaded, counting down in
         * the console and to online players, so that the hosting panel or start script brings it back with pnLibrary.
         * Off by default: then the console shows the stop command to type when convenient. Bukkit never needs it.
         *
         * @param seconds countdown from 1 through 300; {@code 0} turns it off
         * @return this builder
         */
        public Builder restartCountdown(int seconds) {
            this.restartCountdown = seconds;
            return this;
        }

        /**
         * Sets the support link shown when pnLibrary cannot be installed or is too old, such as your own Discord.
         *
         * @param url HTTPS link; the pnFolder Discord by default
         * @return this builder
         */
        public Builder support(String url) {
            this.supportUrl = java.util.Objects.requireNonNull(url, "url");
            return this;
        }

        /** Builds the configured options.
         * @return an immutable, validated options instance */
        public BootstrapOptions build() { return new BootstrapOptions(this); }
    }
}
