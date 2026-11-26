package org.pnfolder.pnlibrary.api.remote;

import java.net.URI;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.updates.UpdateValidation;

/** Reusable declaration for a remotely sourced policy. */
public final class RemotePolicySettings {
    private static final Duration MINIMUM_CHECK_EVERY = Duration.ofMinutes(1);
    private static final Duration DEFAULT_CHECK_EVERY = Duration.ofHours(6);
    private static final Duration DEFAULT_STARTUP_TIMEOUT = Duration.ofSeconds(20);

    private final String source;
    private final Duration checkEvery;
    private final DenyAction onDeny;
    private final Map<String, String> values;
    private final PolicyErrorAction onError;
    private final Duration startupTimeout;

    /**
     * Creates the declaration.
     *
     * @param source absolute HTTPS or file URI of the {@code .java} policy source
     * @param checkEvery interval between policy refreshes, at least one minute
     * @param onDeny action applied after a denied result
     * @param values immutable application-defined policy values
     * @param onError what an unreachable, uncompilable or failing policy means
     * @param startupTimeout how long the start of the plugin waits for the first check before treating it as failed
     */
    public RemotePolicySettings(@NotNull String source, @NotNull Duration checkEvery, @NotNull DenyAction onDeny,
                                @NotNull Map<String, String> values, @NotNull PolicyErrorAction onError,
                                @NotNull Duration startupTimeout) {
        this.source = source;
        this.checkEvery = checkEvery;
        this.onDeny = onDeny;
        this.values = values;
        this.onError = onError;
        this.startupTimeout = startupTimeout;
    }

    /** Creates a declaration with the default interval, actions and timeout and no values. */
    public RemotePolicySettings(@NotNull String source) {
        this(source, DEFAULT_CHECK_EVERY, DenyAction.DISABLE_PLUGIN, Collections.<String, String>emptyMap(),
            PolicyErrorAction.DENY, DEFAULT_STARTUP_TIMEOUT);
    }

    /** Creates a new {@link Builder}. */
    @NotNull
    public static Builder builder() {
        return new Builder();
    }

    /** Absolute HTTPS or file URI of the {@code .java} policy source. */
    @NotNull
    public String getSource() {
        return source;
    }

    /** Interval between policy refreshes, at least one minute. */
    @NotNull
    public Duration getCheckEvery() {
        return checkEvery;
    }

    /** Action applied after a denied result. */
    @NotNull
    public DenyAction getOnDeny() {
        return onDeny;
    }

    /** Immutable application-defined policy values. */
    @NotNull
    public Map<String, String> getValues() {
        return values;
    }

    /** What an unreachable, uncompilable or failing policy means. */
    @NotNull
    public PolicyErrorAction getOnError() {
        return onError;
    }

    /** How long the start of the plugin waits for the first check before treating it as failed. */
    @NotNull
    public Duration getStartupTimeout() {
        return startupTimeout;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof RemotePolicySettings)) {
            return false;
        }
        RemotePolicySettings that = (RemotePolicySettings) other;
        return source.equals(that.source) && checkEvery.equals(that.checkEvery) && onDeny == that.onDeny
            && values.equals(that.values) && onError == that.onError && startupTimeout.equals(that.startupTimeout);
    }

    @Override
    public int hashCode() {
        return Objects.hash(source, checkEvery, onDeny, values, onError, startupTimeout);
    }

    @Override
    public String toString() {
        return "RemotePolicySettings(source=" + source + ", checkEvery=" + checkEvery + ", onDeny=" + onDeny
            + ", values=" + values + ", onError=" + onError + ", startupTimeout=" + startupTimeout + ")";
    }

    /** Fluent Java-friendly builder for {@link RemotePolicySettings}. */
    public static final class Builder {
        private String source;
        private Duration checkEvery = DEFAULT_CHECK_EVERY;
        private DenyAction onDeny = DenyAction.DISABLE_PLUGIN;
        private PolicyErrorAction onError = PolicyErrorAction.DENY;
        private Duration startupTimeout = DEFAULT_STARTUP_TIMEOUT;
        private final Map<String, String> values = new LinkedHashMap<String, String>();

        private Builder() {
        }

        /** Sets the source the policy is fetched from: an absolute HTTPS or file URL of a {@code .java} file. */
        @NotNull
        public Builder source(@NotNull String value) {
            URI uri = URI.create(value);
            boolean localFile = "file".equalsIgnoreCase(uri.getScheme()) && uri.isAbsolute();
            if (!UpdateValidation.isHttps(uri) && !localFile) {
                throw new IllegalArgumentException("Remote policy source must be an absolute HTTPS or file URL");
            }
            String path = uri.getPath();
            if (path == null || !path.toLowerCase(Locale.ROOT).endsWith(".java")) {
                throw new IllegalArgumentException("Remote policy source must be a .java file");
            }
            this.source = uri.toASCIIString();
            return this;
        }

        /** Sets the refresh interval; below one minute the policy would be downloaded and compiled in a loop. */
        @NotNull
        public Builder checkEvery(@NotNull Duration value) {
            if (value.compareTo(MINIMUM_CHECK_EVERY) < 0) {
                throw new IllegalArgumentException("Remote policy interval must be at least one minute");
            }
            this.checkEvery = value;
            return this;
        }

        /** Sets how long the start of the plugin waits for the first check; 20 seconds by default. */
        @NotNull
        public Builder startupTimeout(@NotNull Duration value) {
            if (value.isZero() || value.isNegative()) {
                throw new IllegalArgumentException("Remote policy startup timeout must be positive");
            }
            this.startupTimeout = value;
            return this;
        }

        /** Sets what happens when the policy denies an action. */
        @NotNull
        public Builder onDeny(@NotNull DenyAction value) {
            this.onDeny = value;
            return this;
        }

        /** Sets what a policy that cannot be checked means; {@link PolicyErrorAction#DENY} by default. */
        @NotNull
        public Builder onError(@NotNull PolicyErrorAction value) {
            this.onError = value;
            return this;
        }

        /** Adds one custom value sent to the policy. */
        @NotNull
        public Builder value(@NotNull String key, @NotNull String value) {
            String normalized = key.trim();
            if (normalized.isEmpty()) {
                throw new IllegalArgumentException("Remote policy value key must not be blank");
            }
            values.put(normalized, value);
            return this;
        }

        /** Adds several custom values sent to the policy. */
        @NotNull
        public Builder values(@NotNull Map<String, String> values) {
            for (Map.Entry<String, String> entry : values.entrySet()) {
                value(entry.getKey(), entry.getValue());
            }
            return this;
        }

        /** Validates required fields and creates the immutable policy. */
        @NotNull
        public RemotePolicySettings build() {
            if (source == null) {
                throw new IllegalArgumentException("Remote policy source is required");
            }
            return new RemotePolicySettings(source, checkEvery, onDeny,
                Collections.unmodifiableMap(new LinkedHashMap<String, String>(values)), onError, startupTimeout);
        }
    }
}
