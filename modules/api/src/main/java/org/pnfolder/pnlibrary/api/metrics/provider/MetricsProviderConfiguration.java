package org.pnfolder.pnlibrary.api.metrics.provider;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.platform.PlatformType;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Provider-neutral credentials and enablement requested for one plugin module. */
public final class MetricsProviderConfiguration {
    private final MetricsProvider provider;
    private final boolean enabled;
    private final Integer projectId;
    private final Map<PlatformType, Integer> platformProjectIds;
    private final String token;

    /** An enabled configuration without credentials. */
    public MetricsProviderConfiguration(@NotNull MetricsProvider provider) {
        this(provider, true, null, Collections.emptyMap(), null);
    }

    /**
     * Creates the configuration.
     *
     * @param provider metrics backend to open
     * @param enabled whether this backend participates in the resulting metrics session
     * @param projectId positive bStats project identifier used on every platform without its own entry in {@code platformProjectIds}
     * @param platformProjectIds bStats project identifiers by platform: bStats keeps one project per platform
     * @param token FastStats project token, required by {@link MetricsProvider#FASTSTATS}
     */
    public MetricsProviderConfiguration(@NotNull MetricsProvider provider, boolean enabled, @Nullable Integer projectId,
                                        @NotNull Map<PlatformType, Integer> platformProjectIds, @Nullable String token) {
        this.provider = Objects.requireNonNull(provider);
        this.enabled = enabled;
        this.projectId = projectId;
        this.platformProjectIds = Collections.unmodifiableMap(new LinkedHashMap<>(platformProjectIds));
        this.token = token;
    }

    /** A bStats configuration with one project for every platform. */
    @NotNull
    public static MetricsProviderConfiguration bStats(int projectId) {
        return new MetricsProviderConfiguration(MetricsProvider.BSTATS, true, projectId,
                Collections.emptyMap(), null);
    }

    /** A FastStats configuration with a project token. */
    @NotNull
    public static MetricsProviderConfiguration fastStats(@NotNull String token) {
        return new MetricsProviderConfiguration(MetricsProvider.FASTSTATS, true, null,
                Collections.emptyMap(), token);
    }

    /** The metrics backend to open. */
    @NotNull
    public MetricsProvider getProvider() {
        return provider;
    }

    /** Whether this backend participates in the resulting metrics session. */
    public boolean isEnabled() {
        return enabled;
    }

    /** The bStats project used on every platform without its own entry, or {@code null}. */
    @Nullable
    public Integer getProjectId() {
        return projectId;
    }

    /** The bStats projects by platform. */
    @NotNull
    public Map<PlatformType, Integer> getPlatformProjectIds() {
        return platformProjectIds;
    }

    /** The FastStats project token, or {@code null}. */
    @Nullable
    public String getToken() {
        return token;
    }

    /** Returns this configuration with {@code enabled} switched. */
    @NotNull
    public MetricsProviderConfiguration withEnabled(boolean enabled) {
        return new MetricsProviderConfiguration(provider, enabled, projectId, platformProjectIds, token);
    }

    /** Returns this configuration with {@code projectId} replaced. */
    @NotNull
    public MetricsProviderConfiguration withProjectId(@Nullable Integer projectId) {
        return new MetricsProviderConfiguration(provider, enabled, projectId, platformProjectIds, token);
    }

    /** Returns this configuration with {@code token} replaced. */
    @NotNull
    public MetricsProviderConfiguration withToken(@Nullable String token) {
        return new MetricsProviderConfiguration(provider, enabled, projectId, platformProjectIds, token);
    }

    /**
     * Returns this configuration with the bStats project of {@code platform}: its own entry first, then the common one.
     *
     * @throws IllegalArgumentException when bStats has no project for the platform
     */
    @NotNull
    public MetricsProviderConfiguration forPlatform(@NotNull PlatformType platform) {
        if (provider != MetricsProvider.BSTATS) {
            return this;
        }
        Integer id = platformProjectIds.get(platform);
        if (id == null) {
            id = projectId;
        }
        if (id == null) {
            throw new IllegalArgumentException("No bStats project ID is configured for platform " + platform.getDisplayName());
        }
        return new MetricsProviderConfiguration(provider, enabled, id, Collections.emptyMap(), token);
    }

    /** Returns the provider credentials built from this configuration. */
    @NotNull
    public MetricsCredentials<?> credentials() {
        if (provider == MetricsProvider.BSTATS) {
            if (projectId == null) {
                throw new IllegalArgumentException("Metrics project ID is required for bStats");
            }
            return new MetricsCredentials.BStats(projectId);
        }
        if (token == null) {
            throw new IllegalArgumentException("FastStats requires a project token");
        }
        char[] characters = token.toCharArray();
        try {
            return new MetricsCredentials.FastStats(characters);
        } finally {
            java.util.Arrays.fill(characters, '\u0000');
        }
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof MetricsProviderConfiguration)) {
            return false;
        }
        MetricsProviderConfiguration that = (MetricsProviderConfiguration) other;
        return provider == that.provider && enabled == that.enabled && Objects.equals(projectId, that.projectId)
                && platformProjectIds.equals(that.platformProjectIds) && Objects.equals(token, that.token);
    }

    @Override
    public int hashCode() {
        return Objects.hash(provider, enabled, projectId, platformProjectIds, token);
    }

    /** Keeps legacy configuration logs from exposing project tokens. */
    @NotNull
    @Override
    public String toString() {
        return "MetricsProviderConfiguration(provider=" + provider + ", enabled=" + enabled + ", projectId=" + projectId
                + ", platformProjectIds=" + platformProjectIds + ", token=[REDACTED])";
    }
}
