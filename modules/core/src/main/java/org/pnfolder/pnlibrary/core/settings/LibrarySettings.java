package org.pnfolder.pnlibrary.core.settings;

import java.time.Duration;
import java.time.ZoneId;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.runtime.PnLibraryConfig;
import org.pnfolder.pnlibrary.updates.policy.UpdateDefaults;

/**
 * Every setting of the library an administrator may change, with its default value.
 *
 * <p>Each property has exactly one entry in {@link SettingsRegistry}, which knows its key, limits and description; a
 * test fails when a property is added here without one. The values are kept by {@link SettingsStore} in
 * {@code data/settings.json}. The class is immutable: {@code withX} methods return a changed copy.
 */
public final class LibrarySettings {
    private final boolean privacy;
    private final int historyRetentionDays;
    private final String timeZone;
    private final Duration updateCheckInterval;
    private final RestartSettings restart;

    /** Settings with every value given. */
    public LibrarySettings(
        boolean privacy,
        int historyRetentionDays,
        @NotNull String timeZone,
        @NotNull Duration updateCheckInterval,
        @NotNull RestartSettings restart) {
        this.privacy = privacy;
        this.historyRetentionDays = historyRetentionDays;
        this.timeZone = Objects.requireNonNull(timeZone, "timeZone");
        this.updateCheckInterval = Objects.requireNonNull(updateCheckInterval, "updateCheckInterval");
        this.restart = Objects.requireNonNull(restart, "restart");
    }

    /** Settings with every value at its default. */
    public LibrarySettings() {
        this(
            false,
            30,
            ZoneId.systemDefault().getId(),
            UpdateDefaults.CHECK_INTERVAL,
            new RestartSettings());
    }

    /** Whether personal data of players is left out of diagnostic reports. */
    public boolean getPrivacy() {
        return privacy;
    }

    /** Days the diagnostic history is kept. */
    public int getHistoryRetentionDays() {
        return historyRetentionDays;
    }

    /** Zone id used for every time pnLibrary shows. */
    public @NotNull String getTimeZone() {
        return timeZone;
    }

    /** How often the release catalogs are asked for new versions. */
    public @NotNull Duration getUpdateCheckInterval() {
        return updateCheckInterval;
    }

    /** How the server restarts after an update is staged. */
    public @NotNull RestartSettings getRestart() {
        return restart;
    }

    public @NotNull LibrarySettings withRestart(@NotNull RestartSettings value) {
        return new LibrarySettings(privacy, historyRetentionDays, timeZone, updateCheckInterval,
            value);
    }

    public @NotNull LibrarySettings withPrivacy(boolean value) {
        return new LibrarySettings(value, historyRetentionDays, timeZone,
            updateCheckInterval,
            restart);
    }

    public @NotNull LibrarySettings withHistoryRetentionDays(int value) {
        return new LibrarySettings(privacy, value, timeZone, updateCheckInterval,
            restart);
    }

    public @NotNull LibrarySettings withTimeZone(@NotNull String value) {
        return new LibrarySettings(privacy, historyRetentionDays, value,
            updateCheckInterval,
            restart);
    }

    public @NotNull LibrarySettings withUpdateCheckInterval(@NotNull Duration value) {
        return new LibrarySettings(privacy, historyRetentionDays, timeZone, value,
            restart);
    }

    /** The runtime policy of diagnostics and command answers; everything not listed here keeps its default. */
    public @NotNull PnLibraryConfig toConfig() {
        return PnLibraryConfig.builder()
            .historyRetentionDays(historyRetentionDays)
            .privacy(privacy)
            .timeZone(timeZone)
            .upload(true)
            .build();
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof LibrarySettings)) {
            return false;
        }
        LibrarySettings that = (LibrarySettings) other;
        return privacy == that.privacy
            && historyRetentionDays == that.historyRetentionDays
            && timeZone.equals(that.timeZone)
            && updateCheckInterval.equals(that.updateCheckInterval)
            && restart.equals(that.restart);
    }

    @Override
    public int hashCode() {
        return Objects.hash(privacy, historyRetentionDays, timeZone, updateCheckInterval,
            restart);
    }

    @Override
    public String toString() {
        return "LibrarySettings(privacy=" + privacy + ", "
            + "historyRetentionDays=" + historyRetentionDays + ", "
            + "timeZone=" + timeZone + ", "
            + "updateCheckInterval=" + updateCheckInterval + ", "
            + "restart=" + restart + ")";
    }
}
