package org.pnfolder.pnlibrary.api.updates.request;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.platform.PlatformType;
import org.pnfolder.pnlibrary.api.updates.UpdateValidation;

/** Release-asset name pattern bounded to a range of Java feature versions. */
public final class PluginUpdateArtifact {
    private final String pattern;
    private final int minimumJava;
    private final Integer maximumJava;
    private final PlatformType platform;

    /**
     * Creates the rule.
     *
     * @param pattern regular expression matched against complete release asset names
     * @param minimumJava lowest supported Java feature version, inclusive
     * @param maximumJava highest supported Java feature version, inclusive, or {@code null}
     * @param platform optional server/proxy platform restriction
     */
    public PluginUpdateArtifact(@NotNull String pattern, int minimumJava, @Nullable Integer maximumJava,
                                @Nullable PlatformType platform) {
        this.pattern = pattern;
        this.minimumJava = minimumJava;
        this.maximumJava = maximumJava;
        this.platform = platform;
    }

    /** Creates a rule for every platform. */
    public PluginUpdateArtifact(@NotNull String pattern, int minimumJava, @Nullable Integer maximumJava) {
        this(pattern, minimumJava, maximumJava, null);
    }

    /** Regular expression matched against complete release asset names. */
    @NotNull
    public String getPattern() {
        return pattern;
    }

    /** Lowest supported Java feature version, inclusive. */
    public int getMinimumJava() {
        return minimumJava;
    }

    /** Highest supported Java feature version, inclusive, or {@code null}. */
    @Nullable
    public Integer getMaximumJava() {
        return maximumJava;
    }

    /** Optional server/proxy platform restriction. */
    @Nullable
    public PlatformType getPlatform() {
        return platform;
    }

    /** Returns whether {@code javaFeature} is inside this artifact's inclusive Java range. */
    public boolean supports(int javaFeature) {
        return UpdateValidation.javaInRange(javaFeature, minimumJava, maximumJava);
    }
}
