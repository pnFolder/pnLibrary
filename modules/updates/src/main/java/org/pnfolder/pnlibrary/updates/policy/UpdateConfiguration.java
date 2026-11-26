package org.pnfolder.pnlibrary.updates.policy;

import java.time.Duration;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.updates.product.UpdateChannel;

/**
 * Update policy: fixed rules from {@link UpdateDefaults}. Nothing here is read from a file; an administrator changes
 * the channel, automatic download and pause of a plugin with commands or in the menu, and those choices are kept by
 * {@link UpdatePolicyOverrides}.
 */
public final class UpdateConfiguration {
    private final Checks checks;
    private final Downloads downloads;
    private final Installation installation;
    private final LibraryPolicy library;
    private final boolean pluginAutomaticDownload;

    /** Creates the default policy. */
    public UpdateConfiguration() {
        this(new Checks(), new Downloads(), new Installation(), new LibraryPolicy(), true);
    }

    /** Creates a policy from its parts. */
    public UpdateConfiguration(@NotNull Checks checks, @NotNull Downloads downloads,
                               @NotNull Installation installation, @NotNull LibraryPolicy library,
                               boolean pluginAutomaticDownload) {
        this.checks = checks;
        this.downloads = downloads;
        this.installation = installation;
        this.library = library;
        this.pluginAutomaticDownload = pluginAutomaticDownload;
    }

    /** How often the release catalog is checked. */
    @NotNull
    public Checks getChecks() {
        return checks;
    }

    /** Limits and timeouts for artifact downloads. */
    @NotNull
    public Downloads getDownloads() {
        return downloads;
    }

    /** Rules for what may be installed. */
    @NotNull
    public Installation getInstallation() {
        return installation;
    }

    /** Channel and automatic-download choice for the library itself. */
    @NotNull
    public LibraryPolicy getLibrary() {
        return library;
    }

    /** Whether plugins may be downloaded without a command. */
    public boolean getPluginAutomaticDownload() {
        return pluginAutomaticDownload;
    }

    /** Whether any component may be downloaded without a command. */
    public boolean getEffectiveAutomaticDownloads() {
        return library.getAutomaticDownload() || pluginAutomaticDownload;
    }

    /** A copy with another library policy. */
    @NotNull
    public UpdateConfiguration withLibrary(@NotNull LibraryPolicy changed) {
        return new UpdateConfiguration(checks, downloads, installation, changed, pluginAutomaticDownload);
    }

    /** A copy with another automatic-download choice for plugins. */
    @NotNull
    public UpdateConfiguration withPluginAutomaticDownload(boolean changed) {
        return new UpdateConfiguration(checks, downloads, installation, library, changed);
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof UpdateConfiguration)) {
            return false;
        }
        UpdateConfiguration that = (UpdateConfiguration) other;
        return pluginAutomaticDownload == that.pluginAutomaticDownload && checks.equals(that.checks)
            && downloads.equals(that.downloads) && installation.equals(that.installation)
            && library.equals(that.library);
    }

    @Override
    public int hashCode() {
        return Objects.hash(checks, downloads, installation, library, pluginAutomaticDownload);
    }

    @Override
    public String toString() {
        return "UpdateConfiguration(checks=" + checks + ", downloads=" + downloads + ", installation=" + installation
            + ", library=" + library + ", pluginAutomaticDownload=" + pluginAutomaticDownload + ")";
    }

    /** Channel and automatic-download choice for the library itself. */
    public static final class LibraryPolicy {
        private final UpdateChannel channel;
        private final boolean automaticDownload;

        /** Creates the default choice: the dev channel with automatic download. */
        public LibraryPolicy() {
            this(UpdateChannel.DEV, true);
        }

        /** Creates a choice. */
        public LibraryPolicy(@NotNull UpdateChannel channel, boolean automaticDownload) {
            this.channel = channel;
            this.automaticDownload = automaticDownload;
        }

        /** The channel of the library. */
        @NotNull
        public UpdateChannel getChannel() {
            return channel;
        }

        /** Whether the library may be downloaded without a command. */
        public boolean getAutomaticDownload() {
            return automaticDownload;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof LibraryPolicy)) {
                return false;
            }
            LibraryPolicy that = (LibraryPolicy) other;
            return channel == that.channel && automaticDownload == that.automaticDownload;
        }

        @Override
        public int hashCode() {
            return Objects.hash(channel, automaticDownload);
        }

        @Override
        public String toString() {
            return "LibraryPolicy(channel=" + channel + ", automaticDownload=" + automaticDownload + ")";
        }
    }

    /** How often the release catalog is checked. */
    public static final class Checks {
        private final Duration interval;

        /** Creates the default interval. */
        public Checks() {
            this(UpdateDefaults.CHECK_INTERVAL);
        }

        /** Creates checks with the given interval. */
        public Checks(@NotNull Duration interval) {
            this.interval = interval;
        }

        /** How often the release catalog is checked. */
        @NotNull
        public Duration getInterval() {
            return interval;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Checks && interval.equals(((Checks) other).interval);
        }

        @Override
        public int hashCode() {
            return interval.hashCode();
        }

        @Override
        public String toString() {
            return "Checks(interval=" + interval + ")";
        }
    }

    /** Limits and timeouts for artifact downloads. */
    public static final class Downloads {
        private final boolean allowManagedPlugins;
        private final Duration connectTimeout;
        private final Duration readTimeout;
        private final long maxArtifactBytes;

        /** Creates the defaults from {@link UpdateDefaults}. */
        public Downloads() {
            this(UpdateDefaults.ALLOW_MANAGED_PLUGINS, UpdateDefaults.CONNECT_TIMEOUT, UpdateDefaults.READ_TIMEOUT,
                UpdateDefaults.MAX_ARTIFACT_BYTES);
        }

        /** Creates the limits. */
        public Downloads(boolean allowManagedPlugins, @NotNull Duration connectTimeout,
                         @NotNull Duration readTimeout, long maxArtifactBytes) {
            this.allowManagedPlugins = allowManagedPlugins;
            this.connectTimeout = connectTimeout;
            this.readTimeout = readTimeout;
            this.maxArtifactBytes = maxArtifactBytes;
        }

        /** Whether plugins already managed by pnLibrary may be updated. */
        public boolean getAllowManagedPlugins() {
            return allowManagedPlugins;
        }

        /** How long to wait for a connection. */
        @NotNull
        public Duration getConnectTimeout() {
            return connectTimeout;
        }

        /** How long to wait for data from a silent server. */
        @NotNull
        public Duration getReadTimeout() {
            return readTimeout;
        }

        /** The largest single update file accepted. */
        public long getMaxArtifactBytes() {
            return maxArtifactBytes;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof Downloads)) {
                return false;
            }
            Downloads that = (Downloads) other;
            return allowManagedPlugins == that.allowManagedPlugins && maxArtifactBytes == that.maxArtifactBytes
                && connectTimeout.equals(that.connectTimeout) && readTimeout.equals(that.readTimeout);
        }

        @Override
        public int hashCode() {
            return Objects.hash(allowManagedPlugins, connectTimeout, readTimeout, maxArtifactBytes);
        }

        @Override
        public String toString() {
            return "Downloads(allowManagedPlugins=" + allowManagedPlugins + ", connectTimeout=" + connectTimeout
                + ", readTimeout=" + readTimeout + ", maxArtifactBytes=" + maxArtifactBytes + ")";
        }
    }

    /** Rules for what may be installed. */
    public static final class Installation {
        private final boolean allowNewPlugins;
        private final boolean requireSha256;

        /** Creates the defaults: no new plugins, checksums required. */
        public Installation() {
            this(false, UpdateDefaults.REQUIRE_SHA256);
        }

        /** Creates the rules. */
        public Installation(boolean allowNewPlugins, boolean requireSha256) {
            this.allowNewPlugins = allowNewPlugins;
            this.requireSha256 = requireSha256;
        }

        /** Whether plugins that are not installed yet may be added. */
        public boolean getAllowNewPlugins() {
            return allowNewPlugins;
        }

        /** Whether every downloaded file must carry a SHA-256 checksum. */
        public boolean getRequireSha256() {
            return requireSha256;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof Installation)) {
                return false;
            }
            Installation that = (Installation) other;
            return allowNewPlugins == that.allowNewPlugins && requireSha256 == that.requireSha256;
        }

        @Override
        public int hashCode() {
            return Objects.hash(allowNewPlugins, requireSha256);
        }

        @Override
        public String toString() {
            return "Installation(allowNewPlugins=" + allowNewPlugins + ", requireSha256=" + requireSha256 + ")";
        }
    }
}
