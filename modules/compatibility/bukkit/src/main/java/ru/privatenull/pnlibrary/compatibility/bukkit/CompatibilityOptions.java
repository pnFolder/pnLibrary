package ru.privatenull.pnlibrary.compatibility.bukkit;

/** Immutable policy used by the standalone pnLibrary compatibility guard. */
public final class CompatibilityOptions {
    private final String pluginName;
    private final String minimumVersion;
    private final String repositoryOwner;
    private final String repositoryName;

    private CompatibilityOptions(Builder builder) {
        this.pluginName = required(builder.pluginName, "pluginName");
        this.minimumVersion = CompatibilityVersion.normalize(builder.minimumVersion);
        this.repositoryOwner = part(builder.repositoryOwner, "repositoryOwner");
        this.repositoryName = part(builder.repositoryName, "repositoryName");
    }

    public String pluginName() { return pluginName; }
    public String minimumVersion() { return minimumVersion; }
    public String repositoryOwner() { return repositoryOwner; }
    public String repositoryName() { return repositoryName; }
    public String releasesUrl() { return "https://github.com/" + repositoryOwner + "/" + repositoryName + "/releases"; }

    public static Builder builder(String minimumVersion) { return new Builder(minimumVersion); }

    public static final class Builder {
        private final String minimumVersion;
        private String pluginName = "pnLibrary";
        private String repositoryOwner = "pnFolder";
        private String repositoryName = "pnLibrary";

        private Builder(String minimumVersion) { this.minimumVersion = minimumVersion; }
        public Builder pluginName(String value) { pluginName = value; return this; }
        public Builder repository(String owner, String name) { repositoryOwner = owner; repositoryName = name; return this; }
        public CompatibilityOptions build() { return new CompatibilityOptions(this); }
    }

    private static String required(String value, String field) {
        if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }

    private static String part(String value, String field) {
        if (value == null || !value.matches("[A-Za-z0-9_.-]+")) throw new IllegalArgumentException("invalid " + field);
        return value;
    }
}
