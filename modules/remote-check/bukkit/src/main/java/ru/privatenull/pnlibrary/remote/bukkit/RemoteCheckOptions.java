package ru.privatenull.pnlibrary.remote.bukkit;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** Configuration for one remotely replaceable policy class. */
public final class RemoteCheckOptions {
    final String url;
    final String sha256;
    final String className;
    final long maxBytes;
    final Map<String, String> values;

    private RemoteCheckOptions(Builder builder) {
        if (builder.url == null || !builder.url.startsWith("https://")) throw new IllegalArgumentException("HTTPS URL is required");
        if (builder.className == null || builder.className.trim().isEmpty()) throw new IllegalArgumentException("className is required");
        if (!builder.sha256.matches("(?i)[0-9a-f]{64}")) throw new IllegalArgumentException("SHA-256 is required");
        if (builder.maxBytes < 1) throw new IllegalArgumentException("maxBytes must be positive");
        url = builder.url; sha256 = builder.sha256.toLowerCase(); className = builder.className; maxBytes = builder.maxBytes;
        values = Collections.unmodifiableMap(new HashMap<String, String>(builder.values));
    }
    public static Builder builder(String url, String sha256, String className) { return new Builder(url, sha256, className); }
    public static Builder github(String owner, String repository, String file, String sha256, String className) {
        if (!part(owner) || !part(repository) || file == null || !file.matches("[A-Za-z0-9._-]+\\.jar")) {
            throw new IllegalArgumentException("invalid GitHub release coordinates");
        }
        return builder("https://github.com/" + owner + "/" + repository + "/releases/latest/download/" + file, sha256, className);
    }
    public static final class Builder {
        private final String url, sha256, className; private long maxBytes = 8L * 1024L * 1024L; private final Map<String, String> values = new HashMap<String, String>();
        private Builder(String url, String sha256, String className) { this.url = url; this.sha256 = sha256; this.className = className; }
        public Builder maxBytes(long value) { maxBytes = value; return this; }
        public Builder value(String key, String value) { values.put(key, value); return this; }
        public RemoteCheckOptions build() { return new RemoteCheckOptions(this); }
    }

    private static boolean part(String value) { return value != null && value.matches("[A-Za-z0-9_.-]+"); }
}
