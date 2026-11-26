package org.pnfolder.pnlibrary.api.runtime;

import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Immutable runtime policy for diagnostic collection, retention, and upload.
 *
 * <p>Regular expressions are compiled when this object is created, so an invalid redaction policy fails during startup
 * rather than while a support report is being generated. Build one with {@link #builder()}; every getter is described
 * on the setter of the same name in {@link PnLibraryConfigBuilder}. The
 * constructors throw {@link IllegalArgumentException} when a limit, provider, mode, time zone or regular expression is invalid.
 *
 * <pre>{@code
 * PnLibraryConfig config = PnLibraryConfig.builder().upload(false).logRecords(500).build();
 * }</pre>
 */
public final class PnLibraryConfig {
    private final boolean upload;
    private final String uploadMode;
    private final List<String> uploadProviders;
    private final String uploadEndpoint;
    private final String uploadPublicBase;
    private final String uploadPublicKey;
    private final String uploadKeyId;
    private final boolean allowPlaintext;
    private final boolean configs;
    private final boolean logs;
    private final int logRecords;
    private final int historyRetentionDays;
    private final int historyMaxBytes;
    private final Duration cooldown;
    private final int keepReports;
    private final int maxReportBytes;
    private final int deleteAfterDays;
    private final int taskHistoryCapacity;
    private final List<String> secretKeyPatterns;
    private final List<String> redactValuePatterns;
    private final boolean privacy;
    private final int journalInfoDays;
    private final int journalWarningDays;
    private final int journalErrorDays;
    private final String noPermissionMessage;
    private final boolean journalForeignPlugins;
    private final String timeZone;
    private final String noPermissionSound;
    private final String usageMessage;
    private final String invalidArgumentMessage;
    private final String playerNotFoundMessage;
    private final String playerOnlyMessage;
    private final String consoleOnlyMessage;
    private final String cooldownMessage;
    private final String commandFailedMessage;
    private final int journalJoinNoticeHours;
    private final int eventHistoryDays;

    /** The configuration with every default. */
    public PnLibraryConfig() {
        this(new PnLibraryConfigBuilder());
    }

    PnLibraryConfig(@NotNull PnLibraryConfigBuilder b) {
        this.upload = b.upload;
        this.uploadMode = b.uploadMode;
        this.uploadProviders = Collections.unmodifiableList(new ArrayList<>(b.uploadProviders));
        this.uploadEndpoint = b.uploadEndpoint;
        this.uploadPublicBase = b.uploadPublicBase;
        this.uploadPublicKey = b.uploadPublicKey;
        this.uploadKeyId = b.uploadKeyId;
        this.allowPlaintext = b.allowPlaintext;
        this.configs = b.configs;
        this.logs = b.logs;
        this.logRecords = b.logRecords;
        this.historyRetentionDays = b.historyRetentionDays;
        this.historyMaxBytes = b.historyMaxBytes;
        this.cooldown = b.cooldown;
        this.keepReports = b.keepReports;
        this.maxReportBytes = b.maxReportBytes;
        this.deleteAfterDays = b.deleteAfterDays;
        this.taskHistoryCapacity = b.taskHistoryCapacity;
        this.secretKeyPatterns = Collections.unmodifiableList(new ArrayList<>(b.secretKeyPatterns));
        this.redactValuePatterns = Collections.unmodifiableList(new ArrayList<>(b.redactValuePatterns));
        this.privacy = b.privacy;
        this.journalInfoDays = b.journalInfoDays;
        this.journalWarningDays = b.journalWarningDays;
        this.journalErrorDays = b.journalErrorDays;
        this.noPermissionMessage = b.noPermissionMessage;
        this.journalForeignPlugins = b.journalForeignPlugins;
        this.timeZone = b.timeZone;
        this.noPermissionSound = b.noPermissionSound;
        this.usageMessage = b.usageMessage;
        this.invalidArgumentMessage = b.invalidArgumentMessage;
        this.playerNotFoundMessage = b.playerNotFoundMessage;
        this.playerOnlyMessage = b.playerOnlyMessage;
        this.consoleOnlyMessage = b.consoleOnlyMessage;
        this.cooldownMessage = b.cooldownMessage;
        this.commandFailedMessage = b.commandFailedMessage;
        this.journalJoinNoticeHours = b.journalJoinNoticeHours;
        this.eventHistoryDays = b.eventHistoryDays;
        PnLibraryConfigValidation.check(this);
    }

    /** A builder holding the defaults. */
    @NotNull
    public static PnLibraryConfigBuilder builder() {
        return new PnLibraryConfigBuilder();
    }

    /** A builder holding the values of this configuration, for changing a few of them. */
    @NotNull
    public PnLibraryConfigBuilder toBuilder() {
        PnLibraryConfigBuilder b = new PnLibraryConfigBuilder();
        b.upload = upload;
        b.uploadMode = uploadMode;
        b.uploadProviders = uploadProviders;
        b.uploadEndpoint = uploadEndpoint;
        b.uploadPublicBase = uploadPublicBase;
        b.uploadPublicKey = uploadPublicKey;
        b.uploadKeyId = uploadKeyId;
        b.allowPlaintext = allowPlaintext;
        b.configs = configs;
        b.logs = logs;
        b.logRecords = logRecords;
        b.historyRetentionDays = historyRetentionDays;
        b.historyMaxBytes = historyMaxBytes;
        b.cooldown = cooldown;
        b.keepReports = keepReports;
        b.maxReportBytes = maxReportBytes;
        b.deleteAfterDays = deleteAfterDays;
        b.taskHistoryCapacity = taskHistoryCapacity;
        b.secretKeyPatterns = secretKeyPatterns;
        b.redactValuePatterns = redactValuePatterns;
        b.privacy = privacy;
        b.journalInfoDays = journalInfoDays;
        b.journalWarningDays = journalWarningDays;
        b.journalErrorDays = journalErrorDays;
        b.noPermissionMessage = noPermissionMessage;
        b.journalForeignPlugins = journalForeignPlugins;
        b.timeZone = timeZone;
        b.noPermissionSound = noPermissionSound;
        b.usageMessage = usageMessage;
        b.invalidArgumentMessage = invalidArgumentMessage;
        b.playerNotFoundMessage = playerNotFoundMessage;
        b.playerOnlyMessage = playerOnlyMessage;
        b.consoleOnlyMessage = consoleOnlyMessage;
        b.cooldownMessage = cooldownMessage;
        b.commandFailedMessage = commandFailedMessage;
        b.journalJoinNoticeHours = journalJoinNoticeHours;
        b.eventHistoryDays = eventHistoryDays;
        return b;
    }

    public boolean isUpload() { return upload; }
    @NotNull public String getUploadMode() { return uploadMode; }
    @NotNull public List<String> getUploadProviders() { return uploadProviders; }
    @NotNull public String getUploadEndpoint() { return uploadEndpoint; }
    @NotNull public String getUploadPublicBase() { return uploadPublicBase; }
    @NotNull public String getUploadPublicKey() { return uploadPublicKey; }
    @NotNull public String getUploadKeyId() { return uploadKeyId; }
    public boolean isAllowPlaintext() { return allowPlaintext; }
    public boolean isConfigs() { return configs; }
    public boolean isLogs() { return logs; }
    public int getLogRecords() { return logRecords; }
    public int getHistoryRetentionDays() { return historyRetentionDays; }
    public int getHistoryMaxBytes() { return historyMaxBytes; }
    @NotNull public Duration getCooldown() { return cooldown; }
    public int getKeepReports() { return keepReports; }
    public int getMaxReportBytes() { return maxReportBytes; }
    public int getDeleteAfterDays() { return deleteAfterDays; }
    public int getTaskHistoryCapacity() { return taskHistoryCapacity; }
    @NotNull public List<String> getSecretKeyPatterns() { return secretKeyPatterns; }
    @NotNull public List<String> getRedactValuePatterns() { return redactValuePatterns; }
    public boolean isPrivacy() { return privacy; }
    public int getJournalInfoDays() { return journalInfoDays; }
    public int getJournalWarningDays() { return journalWarningDays; }
    public int getJournalErrorDays() { return journalErrorDays; }
    @NotNull public String getNoPermissionMessage() { return noPermissionMessage; }
    public boolean isJournalForeignPlugins() { return journalForeignPlugins; }
    @NotNull public String getTimeZone() { return timeZone; }
    @NotNull public String getNoPermissionSound() { return noPermissionSound; }
    @NotNull public String getUsageMessage() { return usageMessage; }
    @NotNull public String getInvalidArgumentMessage() { return invalidArgumentMessage; }
    @NotNull public String getPlayerNotFoundMessage() { return playerNotFoundMessage; }
    @NotNull public String getPlayerOnlyMessage() { return playerOnlyMessage; }
    @NotNull public String getConsoleOnlyMessage() { return consoleOnlyMessage; }
    @NotNull public String getCooldownMessage() { return cooldownMessage; }
    @NotNull public String getCommandFailedMessage() { return commandFailedMessage; }
    public int getJournalJoinNoticeHours() { return journalJoinNoticeHours; }
    public int getEventHistoryDays() { return eventHistoryDays; }
    private Object[] values() {
        return new Object[] {upload, uploadMode, uploadProviders, uploadEndpoint, uploadPublicBase, uploadPublicKey, uploadKeyId, allowPlaintext, configs, logs, logRecords, historyRetentionDays, historyMaxBytes, cooldown, keepReports, maxReportBytes, deleteAfterDays, taskHistoryCapacity, secretKeyPatterns, redactValuePatterns, privacy, journalInfoDays, journalWarningDays, journalErrorDays, noPermissionMessage, journalForeignPlugins, timeZone, noPermissionSound, usageMessage, invalidArgumentMessage, playerNotFoundMessage, playerOnlyMessage, consoleOnlyMessage, cooldownMessage, commandFailedMessage, journalJoinNoticeHours, eventHistoryDays};
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof PnLibraryConfig && Arrays.equals(values(), ((PnLibraryConfig) other).values());
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(values());
    }

    @NotNull
    @Override
    public String toString() {
        return "PnLibraryConfig" + Arrays.toString(values());
    }
}
