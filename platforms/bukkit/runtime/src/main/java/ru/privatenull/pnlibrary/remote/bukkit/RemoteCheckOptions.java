package ru.privatenull.pnlibrary.remote.bukkit;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** Configuration for one remotely replaceable policy class. */
public final class RemoteCheckOptions {
    final String url;
    final String className;
    final long maxBytes;
    final Map<String, String> values;
    final long intervalTicks;
    final RemoteCheckListener listener;

    private RemoteCheckOptions(Builder builder) {
        if (builder.url == null || !builder.url.startsWith("https://")) throw new IllegalArgumentException("HTTPS URL is required");
        if (builder.className == null || builder.className.trim().isEmpty()) throw new IllegalArgumentException("className is required");
        if (builder.maxBytes < 1) throw new IllegalArgumentException("maxBytes must be positive");
        url = builder.url; className = builder.className; maxBytes = builder.maxBytes;
        intervalTicks = builder.intervalTicks; listener = builder.listener;
        values = Collections.unmodifiableMap(new HashMap<String, String>(builder.values));
    }
    public static Builder builder(String url, String className) { return new Builder(url, className); }
    public static Builder github(String owner, String repository, String file, String className) {
        if (!part(owner) || !part(repository) || file == null || !file.matches("[A-Za-z0-9._-]+")) {
            throw new IllegalArgumentException("invalid GitHub release coordinates");
        }
        return builder("https://github.com/" + owner + "/" + repository + "/releases/latest/download/" + file, className);
    }
    public static final class Builder {
        private final String url, className; private long maxBytes = 8L * 1024L * 1024L; private long intervalTicks = 6L * 60L * 60L * 20L; private RemoteCheckListener listener = new RemoteCheckListener() { }; private final Map<String, String> values = new HashMap<String, String>();
        private Builder(String url, String className) { this.url = url; this.className = className; }
        public Builder maxBytes(long value) { maxBytes = value; return this; }
        public Builder intervalTicks(long value) { if (value < 1) throw new IllegalArgumentException("intervalTicks must be positive"); intervalTicks = value; return this; }
        public Builder listener(RemoteCheckListener value) { listener = value == null ? new RemoteCheckListener() { } : value; return this; }
        public Builder value(String key, String value) { values.put(key, value); return this; }
        public RemoteCheckOptions build() { return new RemoteCheckOptions(this); }
    }

    private static boolean part(String value) { return value != null && value.matches("[A-Za-z0-9_.-]+"); }
}
