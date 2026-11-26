package org.pnfolder.pnlibrary.api.updates.state;

import java.util.Objects;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.updates.UpdateValidation;
import org.pnfolder.pnlibrary.api.updates.product.UpdateChannel;
import org.pnfolder.pnlibrary.api.updates.release.ReleaseSummary;
import org.pnfolder.pnlibrary.api.version.ApiVersionRange;

/** Immutable point-in-time state of one registered product updater. */
public final class UpdateSnapshot {
    private final String product;
    private final String currentVersion;
    private final String latestVersion;
    private final UpdateChannel channel;
    private final UpdateState state;
    private final int currentJava;
    private final int requiredJava;
    private final boolean automaticDownload;
    private final String releaseUrl;
    private final String message;
    private final String latestChangelogUrl;
    private final ApiVersionRange supportedApi;
    private final List<ReleaseSummary> availableReleases;
    private final List<Integer> javaBuilds;
    private final Integer javaBuild;
    private final Integer recommendedJava;

    /**
     * Creates the snapshot.
     *
     * @param product display name resolved from native plugin metadata
     * @param currentVersion version currently running
     * @param latestVersion newest compatible release version, when known
     * @param channel configured release maturity
     * @param state current updater phase
     * @param currentJava Java feature version running the server
     * @param requiredJava minimum Java version of the selected artifact
     * @param automaticDownload whether checks may download a verified artifact automatically
     * @param releaseUrl public release page, when one is known
     * @param message failure or status detail intended for diagnostics, when present
     * @param latestChangelogUrl link to the human-readable changelog for {@code latestVersion}, when published
     * @param supportedApi pnLibrary API generations supported by this registration, when declared
     * @param availableReleases known catalog releases, including channels not selected for installation
     * @param javaBuilds Java versions the newest release has a build for, ascending; empty when unknown
     * @param javaBuild the build the administrator chose, or {@code null} when the recommended one is used
     * @param recommendedJava the build used when nothing is chosen: the one matching the installed product, else the newest
     */
    public UpdateSnapshot(@NotNull String product, @NotNull String currentVersion, @Nullable String latestVersion,
                          @NotNull UpdateChannel channel, @NotNull UpdateState state, int currentJava,
                          int requiredJava, boolean automaticDownload, @Nullable String releaseUrl,
                          @Nullable String message, @Nullable String latestChangelogUrl,
                          @Nullable ApiVersionRange supportedApi, @NotNull List<ReleaseSummary> availableReleases,
                          @NotNull List<Integer> javaBuilds, @Nullable Integer javaBuild,
                          @Nullable Integer recommendedJava) {
        this.product = Objects.requireNonNull(product, "product");
        this.currentVersion = Objects.requireNonNull(currentVersion, "currentVersion");
        this.latestVersion = latestVersion;
        this.channel = Objects.requireNonNull(channel, "channel");
        this.state = Objects.requireNonNull(state, "state");
        this.currentJava = currentJava;
        this.requiredJava = requiredJava;
        this.automaticDownload = automaticDownload;
        this.releaseUrl = releaseUrl;
        this.message = message;
        this.latestChangelogUrl = latestChangelogUrl;
        this.supportedApi = supportedApi;
        this.availableReleases = UpdateValidation.frozen(availableReleases);
        this.javaBuilds = UpdateValidation.frozen(javaBuilds);
        this.javaBuild = javaBuild;
        this.recommendedJava = recommendedJava;
    }

    /** Creates a snapshot with no changelog, API range, releases or Java builds. */
    public UpdateSnapshot(@NotNull String product, @NotNull String currentVersion, @Nullable String latestVersion,
                          @NotNull UpdateChannel channel, @NotNull UpdateState state, int currentJava,
                          int requiredJava, boolean automaticDownload, @Nullable String releaseUrl,
                          @Nullable String message) {
        this(product, currentVersion, latestVersion, channel, state, currentJava, requiredJava, automaticDownload,
            releaseUrl, message, null, null, Collections.emptyList(),
            Collections.emptyList(), null, null);
    }

    /** Creates a snapshot with a changelog link and no API range, releases or Java builds. */
    public UpdateSnapshot(@NotNull String product, @NotNull String currentVersion, @Nullable String latestVersion,
                          @NotNull UpdateChannel channel, @NotNull UpdateState state, int currentJava,
                          int requiredJava, boolean automaticDownload, @Nullable String releaseUrl,
                          @Nullable String message, @Nullable String latestChangelogUrl) {
        this(product, currentVersion, latestVersion, channel, state, currentJava, requiredJava, automaticDownload,
            releaseUrl, message, latestChangelogUrl, null);
    }

    /** Creates a snapshot with a changelog link and an API range, and no releases or Java builds. */
    public UpdateSnapshot(@NotNull String product, @NotNull String currentVersion, @Nullable String latestVersion,
                          @NotNull UpdateChannel channel, @NotNull UpdateState state, int currentJava,
                          int requiredJava, boolean automaticDownload, @Nullable String releaseUrl,
                          @Nullable String message, @Nullable String latestChangelogUrl,
                          @Nullable ApiVersionRange supportedApi) {
        this(product, currentVersion, latestVersion, channel, state, currentJava, requiredJava, automaticDownload,
            releaseUrl, message, latestChangelogUrl, supportedApi, Collections.emptyList());
    }

    /** Creates a snapshot with the known releases and no Java builds. */
    public UpdateSnapshot(@NotNull String product, @NotNull String currentVersion, @Nullable String latestVersion,
                          @NotNull UpdateChannel channel, @NotNull UpdateState state, int currentJava,
                          int requiredJava, boolean automaticDownload, @Nullable String releaseUrl,
                          @Nullable String message, @Nullable String latestChangelogUrl,
                          @Nullable ApiVersionRange supportedApi, @NotNull List<ReleaseSummary> availableReleases) {
        this(product, currentVersion, latestVersion, channel, state, currentJava, requiredJava, automaticDownload,
            releaseUrl, message, latestChangelogUrl, supportedApi, availableReleases,
            Collections.emptyList(), null, null);
    }

    /** Display name resolved from native plugin metadata. */
    @NotNull
    public String getProduct() {
        return product;
    }

    /** Version currently running. */
    @NotNull
    public String getCurrentVersion() {
        return currentVersion;
    }

    /** Newest compatible release version, when known. */
    @Nullable
    public String getLatestVersion() {
        return latestVersion;
    }

    /** Configured release maturity. */
    @NotNull
    public UpdateChannel getChannel() {
        return channel;
    }

    /** Current updater phase. */
    @NotNull
    public UpdateState getState() {
        return state;
    }

    /** Java feature version running the server. */
    public int getCurrentJava() {
        return currentJava;
    }

    /** Minimum Java version of the selected artifact. */
    public int getRequiredJava() {
        return requiredJava;
    }

    /** Whether checks may download a verified artifact automatically. */
    public boolean getAutomaticDownload() {
        return automaticDownload;
    }

    /** Public release page, when one is known. */
    @Nullable
    public String getReleaseUrl() {
        return releaseUrl;
    }

    /** Failure or status detail intended for diagnostics, when present. */
    @Nullable
    public String getMessage() {
        return message;
    }

    /** Link to the human-readable changelog for {@link #getLatestVersion()}, when published. */
    @Nullable
    public String getLatestChangelogUrl() {
        return latestChangelogUrl;
    }

    /** pnLibrary API generations supported by this registration, when declared. */
    @Nullable
    public ApiVersionRange getSupportedApi() {
        return supportedApi;
    }

    /** Immutable copy of known catalog releases, including channels not selected for installation. */
    @NotNull
    public List<ReleaseSummary> getAvailableReleases() {
        return availableReleases;
    }

    /** Java versions the newest release has a build for on this platform and server, ascending. */
    @NotNull
    public List<Integer> getJavaBuilds() {
        return javaBuilds;
    }

    /** The build the administrator chose, or {@code null} when the recommended one is used. */
    @Nullable
    public Integer getJavaBuild() {
        return javaBuild;
    }

    /** The build used when nothing is chosen: the one matching the installed product, else the newest. */
    @Nullable
    public Integer getRecommendedJava() {
        return recommendedJava;
    }
}
