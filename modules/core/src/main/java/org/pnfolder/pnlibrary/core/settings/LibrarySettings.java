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
    private final boolean journalForeignPlugins;
    private final String timeZone;
    private final String noPermissionMessage;
    private final boolean downloadsEnabled;
    private final boolean downloadsAutomatic;
    private final Duration updateCheckInterval;
    private final RestartSettings restart;

    /** Settings with every value given. */
    public LibrarySettings(
        boolean privacy,
        int historyRetentionDays,
        boolean journalForeignPlugins,
        @NotNull String timeZone,
        @NotNull String noPermissionMessage,
        boolean downloadsEnabled,
        boolean downloadsAutomatic,
        @NotNull Duration updateCheckInterval,
        @NotNull RestartSettings restart) {
        this.privacy = privacy;
        this.historyRetentionDays = historyRetentionDays;
        this.journalForeignPlugins = journalForeignPlugins;
        this.timeZone = Objects.requireNonNull(timeZone, "timeZone");
        this.noPermissionMessage = Objects.requireNonNull(noPermissionMessage, "noPermissionMessage");
        this.downloadsEnabled = downloadsEnabled;
        this.downloadsAutomatic = downloadsAutomatic;
        this.updateCheckInterval = Objects.requireNonNull(updateCheckInterval, "updateCheckInterval");
        this.restart = Objects.requireNonNull(restart, "restart");
    }

    /** Settings with every value at its default. */
    public LibrarySettings() {
        this(
            false,
            30,
            false,
            ZoneId.systemDefault().getId(),
            "Недостаточно прав для этой команды.",
            true,
            true,
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

    /** Whether warnings and errors of plugins that are not connected to pnLibrary go to the journal too. */
    public boolean getJournalForeignPlugins() {
        return journalForeignPlugins;
    }

    /** Zone id used for every time pnLibrary shows. */
    public @NotNull String getTimeZone() {
        return timeZone;
    }

    /** What a player sees without the permission for a command. */
    public @NotNull String getNoPermissionMessage() {
        return noPermissionMessage;
    }

    /** Whether plugins may download files through pnLibrary at all. */
    public boolean getDownloadsEnabled() {
        return downloadsEnabled;
    }

    /** Whether downloads that a plugin starts itself, without a command, are allowed. */
    public boolean getDownloadsAutomatic() {
        return downloadsAutomatic;
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
        return new LibrarySettings(privacy, historyRetentionDays, journalForeignPlugins, timeZone, noPermissionMessage,
            downloadsEnabled, downloadsAutomatic, updateCheckInterval,
            value);
    }

    public @NotNull LibrarySettings withPrivacy(boolean value) {
        return new LibrarySettings(value, historyRetentionDays, journalForeignPlugins, timeZone,
            noPermissionMessage, downloadsEnabled,
            downloadsAutomatic, updateCheckInterval,
            restart);
    }

    public @NotNull LibrarySettings withHistoryRetentionDays(int value) {
        return new LibrarySettings(privacy, value, journalForeignPlugins, timeZone, noPermissionMessage,
            downloadsEnabled,
            downloadsAutomatic, updateCheckInterval,
            restart);
    }

    public @NotNull LibrarySettings withJournalForeignPlugins(boolean value) {
        return new LibrarySettings(privacy, historyRetentionDays, value, timeZone, noPermissionMessage,
            downloadsEnabled,
            downloadsAutomatic, updateCheckInterval,
            restart);
    }

    public @NotNull LibrarySettings withTimeZone(@NotNull String value) {
        return new LibrarySettings(privacy, historyRetentionDays, journalForeignPlugins, value,
            noPermissionMessage, downloadsEnabled,
            downloadsAutomatic, updateCheckInterval,
            restart);
    }

    public @NotNull LibrarySettings withNoPermissionMessage(@NotNull String value) {
        return new LibrarySettings(privacy, historyRetentionDays, journalForeignPlugins, timeZone, value,
            downloadsEnabled,
            downloadsAutomatic, updateCheckInterval,
            restart);
    }

    public @NotNull LibrarySettings withDownloadsEnabled(boolean value) {
        return new LibrarySettings(privacy, historyRetentionDays, journalForeignPlugins, timeZone,
            noPermissionMessage, value,
            downloadsAutomatic, updateCheckInterval,
            restart);
    }

    public @NotNull LibrarySettings withDownloadsAutomatic(boolean value) {
        return new LibrarySettings(privacy, historyRetentionDays, journalForeignPlugins, timeZone, noPermissionMessage,
            downloadsEnabled, value, updateCheckInterval,
            restart);
    }

    public @NotNull LibrarySettings withUpdateCheckInterval(@NotNull Duration value) {
        return new LibrarySettings(privacy, historyRetentionDays, journalForeignPlugins, timeZone, noPermissionMessage,
            downloadsEnabled, downloadsAutomatic, value,
            restart);
    }

    /** The runtime policy of diagnostics and command answers; everything not listed here keeps its default. */
    public @NotNull PnLibraryConfig toConfig() {
        return PnLibraryConfig.builder()
            .historyRetentionDays(historyRetentionDays)
            .privacy(privacy)
            .noPermissionMessage(noPermissionMessage)
            .journalForeignPlugins(journalForeignPlugins)
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
            && journalForeignPlugins == that.journalForeignPlugins
            && timeZone.equals(that.timeZone)
            && noPermissionMessage.equals(that.noPermissionMessage)
            && downloadsEnabled == that.downloadsEnabled
            && downloadsAutomatic == that.downloadsAutomatic
            && updateCheckInterval.equals(that.updateCheckInterval)
            && restart.equals(that.restart);
    }

    @Override
    public int hashCode() {
        return Objects.hash(privacy, historyRetentionDays, journalForeignPlugins, timeZone, noPermissionMessage,
            downloadsEnabled, downloadsAutomatic, updateCheckInterval,
            restart);
    }

    @Override
    public String toString() {
        return "LibrarySettings(privacy=" + privacy + ", "
            + "historyRetentionDays=" + historyRetentionDays + ", "
            + "journalForeignPlugins=" + journalForeignPlugins + ", "
            + "timeZone=" + timeZone + ", "
            + "noPermissionMessage=" + noPermissionMessage + ", "
            + "downloadsEnabled=" + downloadsEnabled + ", "
            + "downloadsAutomatic=" + downloadsAutomatic + ", "
            + "updateCheckInterval=" + updateCheckInterval + ", "
            + "restart=" + restart + ")";
    }
}
