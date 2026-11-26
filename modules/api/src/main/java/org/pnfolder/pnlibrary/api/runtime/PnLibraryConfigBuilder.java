package org.pnfolder.pnlibrary.api.runtime;

import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Collects the settings of a {@link PnLibraryConfig}; every setting has the default of a fresh installation. Values
 * are checked when {@link #build()} creates the configuration.
 */
public final class PnLibraryConfigBuilder {
    boolean upload = false;
    String uploadMode = "encrypted";
    List<String> uploadProviders = Arrays.asList("catbox", "fileio");
    String uploadEndpoint = "https://api.mclo.gs/1/log";
    String uploadPublicBase = "https://mclo.gs/";
    String uploadPublicKey = "";
    String uploadKeyId = "support-1";
    boolean allowPlaintext = false;
    boolean configs = true;
    boolean logs = true;
    int logRecords = 200;
    int historyRetentionDays = 30;
    int historyMaxBytes = 32 * 1024 * 1024;
    Duration cooldown = Duration.ofSeconds(10);
    int keepReports = 10;
    int maxReportBytes = 8 * 1024 * 1024;
    int deleteAfterDays = 90;
    int taskHistoryCapacity = 256;
    List<String> secretKeyPatterns = Collections.<String>emptyList();
    List<String> redactValuePatterns = Collections.<String>emptyList();
    boolean privacy = false;
    int journalInfoDays = 7;
    int journalWarningDays = 30;
    int journalErrorDays = 90;
    String noPermissionMessage = "Недостаточно прав для этой команды.";
    boolean journalForeignPlugins = false;
    String timeZone = "UTC";
    String noPermissionSound = "";
    String usageMessage = "Использование: {usage}";
    String invalidArgumentMessage = "Неверное значение «{value}» для {argument}. {usage}";
    String playerNotFoundMessage = "Игрок {value} сейчас не в сети.";
    String playerOnlyMessage = "Эту команду можно использовать только в игре.";
    String consoleOnlyMessage = "Эту команду можно использовать только из консоли.";
    String cooldownMessage = "Подождите {seconds} с перед повторным использованием.";
    String commandFailedMessage = "Не удалось выполнить команду.";
    int journalJoinNoticeHours = 24;
    int eventHistoryDays = 30;

    PnLibraryConfigBuilder() {
    }

    /** Sets whether completed reports may be sent to an upload provider. */
    @NotNull public PnLibraryConfigBuilder upload(boolean value) {
        this.upload = value;
        return this;
    }
    /** Sets upload transport policy: {@code encrypted} or {@code disabled}. */
    @NotNull public PnLibraryConfigBuilder uploadMode(@NotNull String value) {
        this.uploadMode = value;
        return this;
    }
    /** Sets providers attempted in order: {@code catbox}, {@code fileio}, {@code custom}. */
    @NotNull public PnLibraryConfigBuilder uploadProviders(@NotNull List<String> value) {
        this.uploadProviders = new ArrayList<>(value);
        return this;
    }
    /** Sets endpoint used by the custom upload provider. */
    @NotNull public PnLibraryConfigBuilder uploadEndpoint(@NotNull String value) {
        this.uploadEndpoint = value;
        return this;
    }
    /** Sets public URL prefix used to construct custom upload links. */
    @NotNull public PnLibraryConfigBuilder uploadPublicBase(@NotNull String value) {
        this.uploadPublicBase = value;
        return this;
    }
    /** Sets Base64-encoded public key used for encrypted reports. */
    @NotNull public PnLibraryConfigBuilder uploadPublicKey(@NotNull String value) {
        this.uploadPublicKey = value;
        return this;
    }
    /** Sets operator-defined identifier written into encrypted envelopes. */
    @NotNull public PnLibraryConfigBuilder uploadKeyId(@NotNull String value) {
        this.uploadKeyId = value;
        return this;
    }
    /** Sets whether an explicitly configured provider may receive an unencrypted report. */
    @NotNull public PnLibraryConfigBuilder allowPlaintext(boolean value) {
        this.allowPlaintext = value;
        return this;
    }
    /** Sets whether registered configuration files are collected by default. */
    @NotNull public PnLibraryConfigBuilder configs(boolean value) {
        this.configs = value;
        return this;
    }
    /** Sets whether buffered warning and error records are collected by default. */
    @NotNull public PnLibraryConfigBuilder logs(boolean value) {
        this.logs = value;
        return this;
    }
    /** Sets maximum number of recent log records included in one report. */
    @NotNull public PnLibraryConfigBuilder logRecords(int value) {
        this.logRecords = value;
        return this;
    }
    /** Sets maximum age of persisted diagnostic history entries. */
    @NotNull public PnLibraryConfigBuilder historyRetentionDays(int value) {
        this.historyRetentionDays = value;
        return this;
    }
    /** Sets total byte budget for persisted diagnostic history. */
    @NotNull public PnLibraryConfigBuilder historyMaxBytes(int value) {
        this.historyMaxBytes = value;
        return this;
    }
    /** Sets minimum delay between diagnostic commands from one caller. */
    @NotNull public PnLibraryConfigBuilder cooldown(@NotNull Duration value) {
        this.cooldown = value;
        return this;
    }
    /** Sets maximum number of locally retained report archives. */
    @NotNull public PnLibraryConfigBuilder keepReports(int value) {
        this.keepReports = value;
        return this;
    }
    /** Sets maximum uncompressed report size in bytes. */
    @NotNull public PnLibraryConfigBuilder maxReportBytes(int value) {
        this.maxReportBytes = value;
        return this;
    }
    /** Sets age at which local reports are deleted; {@code 0} disables age-based deletion. */
    @NotNull public PnLibraryConfigBuilder deleteAfterDays(int value) {
        this.deleteAfterDays = value;
        return this;
    }
    /** Sets maximum number of completed task records retained in memory; {@code 0} disables task history. */
    @NotNull public PnLibraryConfigBuilder taskHistoryCapacity(int value) {
        this.taskHistoryCapacity = value;
        return this;
    }
    /** Sets regular expressions matched against configuration keys whose values must be redacted. */
    @NotNull public PnLibraryConfigBuilder secretKeyPatterns(@NotNull List<String> value) {
        this.secretKeyPatterns = new ArrayList<>(value);
        return this;
    }
    /** Sets regular expressions whose matches are redacted inside scalar configuration values. */
    @NotNull public PnLibraryConfigBuilder redactValuePatterns(@NotNull List<String> value) {
        this.redactValuePatterns = new ArrayList<>(value);
        return this;
    }
    /** Sets whether personal data is excluded from diagnostic reports. */
    @NotNull public PnLibraryConfigBuilder privacy(boolean value) {
        this.privacy = value;
        return this;
    }
    /** Sets days of journal entries kept at the info level. */
    @NotNull public PnLibraryConfigBuilder journalInfoDays(int value) {
        this.journalInfoDays = value;
        return this;
    }
    /** Sets days of journal entries kept at the warning level. */
    @NotNull public PnLibraryConfigBuilder journalWarningDays(int value) {
        this.journalWarningDays = value;
        return this;
    }
    /** Sets days of journal entries kept at the error level. */
    @NotNull public PnLibraryConfigBuilder journalErrorDays(int value) {
        this.journalErrorDays = value;
        return this;
    }
    /** Sets message shown when a caller lacks the required permission. */
    @NotNull public PnLibraryConfigBuilder noPermissionMessage(@NotNull String value) {
        this.noPermissionMessage = value;
        return this;
    }
    /** Sets whether journal entries from other plugins are recorded. */
    @NotNull public PnLibraryConfigBuilder journalForeignPlugins(boolean value) {
        this.journalForeignPlugins = value;
        return this;
    }
    /** Sets time zone id used when displaying times, such as {@code UTC}. */
    @NotNull public PnLibraryConfigBuilder timeZone(@NotNull String value) {
        this.timeZone = value;
        return this;
    }
    /** Sets sound key played with the no-permission message; empty disables it. */
    @NotNull public PnLibraryConfigBuilder noPermissionSound(@NotNull String value) {
        this.noPermissionSound = value;
        return this;
    }
    /** Sets message shown for incorrect command usage; {@code {usage}} is replaced. */
    @NotNull public PnLibraryConfigBuilder usageMessage(@NotNull String value) {
        this.usageMessage = value;
        return this;
    }
    /** Sets message shown for an invalid argument value. */
    @NotNull public PnLibraryConfigBuilder invalidArgumentMessage(@NotNull String value) {
        this.invalidArgumentMessage = value;
        return this;
    }
    /** Sets message shown when a named player is not online. */
    @NotNull public PnLibraryConfigBuilder playerNotFoundMessage(@NotNull String value) {
        this.playerNotFoundMessage = value;
        return this;
    }
    /** Sets message shown when a player-only command runs from the console. */
    @NotNull public PnLibraryConfigBuilder playerOnlyMessage(@NotNull String value) {
        this.playerOnlyMessage = value;
        return this;
    }
    /** Sets message shown when a console-only command runs in game. */
    @NotNull public PnLibraryConfigBuilder consoleOnlyMessage(@NotNull String value) {
        this.consoleOnlyMessage = value;
        return this;
    }
    /** Sets message shown while a command is on cooldown. */
    @NotNull public PnLibraryConfigBuilder cooldownMessage(@NotNull String value) {
        this.cooldownMessage = value;
        return this;
    }
    /** Sets message shown when a command fails. */
    @NotNull public PnLibraryConfigBuilder commandFailedMessage(@NotNull String value) {
        this.commandFailedMessage = value;
        return this;
    }
    /** Sets hours after join during which the join notice is shown. */
    @NotNull public PnLibraryConfigBuilder journalJoinNoticeHours(int value) {
        this.journalJoinNoticeHours = value;
        return this;
    }
    /** Sets days of per-day event call counts kept for {@code /pn bus history}; {@code 0} keeps none. */
    @NotNull public PnLibraryConfigBuilder eventHistoryDays(int value) {
        this.eventHistoryDays = value;
        return this;
    }
    /** Validates the settings and creates the immutable configuration. */
    @NotNull
    public PnLibraryConfig build() {
        return new PnLibraryConfig(this);
    }
}
