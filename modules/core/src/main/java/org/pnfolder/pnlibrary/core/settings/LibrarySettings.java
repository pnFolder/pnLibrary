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
    private final boolean downloadsToDataFolder;
    private final boolean downloadsToCache;
    private final Duration updateCheckInterval;
    private final Duration updateConnectTimeout;
    private final Duration updateReadTimeout;

    /** Settings with every value given. */
    public LibrarySettings(
        boolean privacy,
        int historyRetentionDays,
        boolean journalForeignPlugins,
        @NotNull String timeZone,
        @NotNull String noPermissionMessage,
        boolean downloadsEnabled,
        boolean downloadsAutomatic,
        boolean downloadsToDataFolder,
        boolean downloadsToCache,
        @NotNull Duration updateCheckInterval,
        @NotNull Duration updateConnectTimeout,
        @NotNull Duration updateReadTimeout) {
        this.privacy = privacy;
        this.historyRetentionDays = historyRetentionDays;
        this.journalForeignPlugins = journalForeignPlugins;
        this.timeZone = timeZone;
        this.noPermissionMessage = noPermissionMessage;
        this.downloadsEnabled = downloadsEnabled;
        this.downloadsAutomatic = downloadsAutomatic;
        this.downloadsToDataFolder = downloadsToDataFolder;
        this.downloadsToCache = downloadsToCache;
        this.updateCheckInterval = updateCheckInterval;
        this.updateConnectTimeout = updateConnectTimeout;
        this.updateReadTimeout = updateReadTimeout;
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
            true,
            true,
            UpdateDefaults.CHECK_INTERVAL,
            UpdateDefaults.CONNECT_TIMEOUT,
            UpdateDefaults.READ_TIMEOUT);
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

    /** Whether a file may be saved into the data folder of the plugin that asked for it. */
    public boolean getDownloadsToDataFolder() {
        return downloadsToDataFolder;
    }

    /** Whether a file may be saved into the shared cache of pnLibrary. */
    public boolean getDownloadsToCache() {
        return downloadsToCache;
    }

    /** How often the release catalogs are asked for new versions. */
    public @NotNull Duration getUpdateCheckInterval() {
        return updateCheckInterval;
    }

    /** How long to wait for a connection while downloading an update file. */
    public @NotNull Duration getUpdateConnectTimeout() {
        return updateConnectTimeout;
    }

    /** How long to wait for data from a silent server while downloading an update file. */
    public @NotNull Duration getUpdateReadTimeout() {
        return updateReadTimeout;
    }

    /** A copy with privacy changed. */
    public @NotNull LibrarySettings withPrivacy(boolean value) {
        return new LibrarySettings(value, historyRetentionDays, journalForeignPlugins, timeZone,
            noPermissionMessage, downloadsEnabled,
            downloadsAutomatic, downloadsToDataFolder, downloadsToCache, updateCheckInterval,
            updateConnectTimeout, updateReadTimeout);
    }

    /** A copy with historyRetentionDays changed. */
    public @NotNull LibrarySettings withHistoryRetentionDays(int value) {
        return new LibrarySettings(privacy, value, journalForeignPlugins, timeZone, noPermissionMessage,
            downloadsEnabled,
            downloadsAutomatic, downloadsToDataFolder, downloadsToCache, updateCheckInterval,
            updateConnectTimeout, updateReadTimeout);
    }

    /** A copy with journalForeignPlugins changed. */
    public @NotNull LibrarySettings withJournalForeignPlugins(boolean value) {
        return new LibrarySettings(privacy, historyRetentionDays, value, timeZone, noPermissionMessage,
            downloadsEnabled,
            downloadsAutomatic, downloadsToDataFolder, downloadsToCache, updateCheckInterval,
            updateConnectTimeout, updateReadTimeout);
    }

    /** A copy with timeZone changed. */
    public @NotNull LibrarySettings withTimeZone(@NotNull String value) {
        return new LibrarySettings(privacy, historyRetentionDays, journalForeignPlugins, value,
            noPermissionMessage, downloadsEnabled,
            downloadsAutomatic, downloadsToDataFolder, downloadsToCache, updateCheckInterval,
            updateConnectTimeout, updateReadTimeout);
    }

    /** A copy with noPermissionMessage changed. */
    public @NotNull LibrarySettings withNoPermissionMessage(@NotNull String value) {
        return new LibrarySettings(privacy, historyRetentionDays, journalForeignPlugins, timeZone, value,
            downloadsEnabled,
            downloadsAutomatic, downloadsToDataFolder, downloadsToCache, updateCheckInterval,
            updateConnectTimeout, updateReadTimeout);
    }

    /** A copy with downloadsEnabled changed. */
    public @NotNull LibrarySettings withDownloadsEnabled(boolean value) {
        return new LibrarySettings(privacy, historyRetentionDays, journalForeignPlugins, timeZone,
            noPermissionMessage, value,
            downloadsAutomatic, downloadsToDataFolder, downloadsToCache, updateCheckInterval,
            updateConnectTimeout, updateReadTimeout);
    }

    /** A copy with downloadsAutomatic changed. */
    public @NotNull LibrarySettings withDownloadsAutomatic(boolean value) {
        return new LibrarySettings(privacy, historyRetentionDays, journalForeignPlugins, timeZone, noPermissionMessage,
            downloadsEnabled, value, downloadsToDataFolder, downloadsToCache, updateCheckInterval,
            updateConnectTimeout, updateReadTimeout);
    }

    /** A copy with downloadsToDataFolder changed. */
    public @NotNull LibrarySettings withDownloadsToDataFolder(boolean value) {
        return new LibrarySettings(privacy, historyRetentionDays, journalForeignPlugins, timeZone, noPermissionMessage,
            downloadsEnabled, downloadsAutomatic, value, downloadsToCache, updateCheckInterval,
            updateConnectTimeout, updateReadTimeout);
    }

    /** A copy with downloadsToCache changed. */
    public @NotNull LibrarySettings withDownloadsToCache(boolean value) {
        return new LibrarySettings(privacy, historyRetentionDays, journalForeignPlugins, timeZone, noPermissionMessage,
            downloadsEnabled, downloadsAutomatic, downloadsToDataFolder, value, updateCheckInterval,
            updateConnectTimeout, updateReadTimeout);
    }

    /** A copy with updateCheckInterval changed. */
    public @NotNull LibrarySettings withUpdateCheckInterval(@NotNull Duration value) {
        return new LibrarySettings(privacy, historyRetentionDays, journalForeignPlugins, timeZone, noPermissionMessage,
            downloadsEnabled, downloadsAutomatic, downloadsToDataFolder, downloadsToCache, value,
            updateConnectTimeout, updateReadTimeout);
    }

    /** A copy with updateConnectTimeout changed. */
    public @NotNull LibrarySettings withUpdateConnectTimeout(@NotNull Duration value) {
        return new LibrarySettings(privacy, historyRetentionDays, journalForeignPlugins, timeZone, noPermissionMessage,
            downloadsEnabled, downloadsAutomatic, downloadsToDataFolder, downloadsToCache, updateCheckInterval,
            value, updateReadTimeout);
    }

    /** A copy with updateReadTimeout changed. */
    public @NotNull LibrarySettings withUpdateReadTimeout(@NotNull Duration value) {
        return new LibrarySettings(privacy, historyRetentionDays, journalForeignPlugins, timeZone, noPermissionMessage,
            downloadsEnabled, downloadsAutomatic, downloadsToDataFolder, downloadsToCache, updateCheckInterval,
            updateConnectTimeout, value);
    }

    /** The runtime policy of diagnostics and command answers; everything not listed here keeps its default. */
    public @NotNull PnLibraryConfig toConfig() {
        return PnLibraryConfig.builder()
            .historyRetentionDays(historyRetentionDays)
            .privacy(privacy)
            .noPermissionMessage(noPermissionMessage)
            .journalForeignPlugins(journalForeignPlugins)
            .timeZone(timeZone)
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
            && downloadsToDataFolder == that.downloadsToDataFolder
            && downloadsToCache == that.downloadsToCache
            && updateCheckInterval.equals(that.updateCheckInterval)
            && updateConnectTimeout.equals(that.updateConnectTimeout)
            && updateReadTimeout.equals(that.updateReadTimeout);
    }

    @Override
    public int hashCode() {
        return Objects.hash(privacy, historyRetentionDays, journalForeignPlugins, timeZone, noPermissionMessage,
            downloadsEnabled, downloadsAutomatic, downloadsToDataFolder, downloadsToCache, updateCheckInterval,
            updateConnectTimeout, updateReadTimeout);
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
            + "downloadsToDataFolder=" + downloadsToDataFolder + ", "
            + "downloadsToCache=" + downloadsToCache + ", "
            + "updateCheckInterval=" + updateCheckInterval + ", "
            + "updateConnectTimeout=" + updateConnectTimeout + ", "
            + "updateReadTimeout=" + updateReadTimeout + ")";
    }
}
