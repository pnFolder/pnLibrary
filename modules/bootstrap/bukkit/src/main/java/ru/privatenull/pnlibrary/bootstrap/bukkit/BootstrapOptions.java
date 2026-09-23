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

    public String minimumVersion() { return minimumVersion; }
    public String repositoryOwner() { return repositoryOwner; }
    public String repositoryName() { return repositoryName; }
    public long maximumBytes() { return maximumBytes; }

    public static Builder builder(String minimumVersion) { return new Builder(minimumVersion); }

    private static String part(String value, String field) {
        Objects.requireNonNull(value, field);
        if (!value.matches("[A-Za-z0-9_.-]+")) throw new IllegalArgumentException("invalid " + field + ": " + value);
        return value;
    }

    public static final class Builder {
        private final String minimumVersion;
        private String repositoryOwner = "pnFolder";
        private String repositoryName = "pnLibrary";
        private long maximumBytes = 512L * 1024L * 1024L;

        private Builder(String minimumVersion) { this.minimumVersion = minimumVersion; }

        public Builder repository(String owner, String name) {
            this.repositoryOwner = owner;
            this.repositoryName = name;
            return this;
        }

        public Builder maximumBytes(long value) {
            this.maximumBytes = value;
            return this;
        }

        public BootstrapOptions build() { return new BootstrapOptions(this); }
    }
}
