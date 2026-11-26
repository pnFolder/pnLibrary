package org.pnfolder.pnlibrary.api.runtime;

import org.jetbrains.annotations.ApiStatus;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** Checks the limits of a {@link PnLibraryConfig} when it is created. */
@ApiStatus.Internal
final class PnLibraryConfigValidation {
    private static final Pattern SOUND_KEY = Pattern.compile("[a-z0-9_.:/-]+");

    private PnLibraryConfigValidation() {
    }

    static void check(PnLibraryConfig c) {
        text("noPermissionMessage", c.getNoPermissionMessage());
        try {
            ZoneId.of(c.getTimeZone());
        } catch (DateTimeException invalid) {
            throw new IllegalArgumentException("Setting timeZone must be a time zone id such as UTC or Europe/Moscow", invalid);
        }
        range("eventHistoryDays", c.getEventHistoryDays(), 0, 3650);
        range("journalJoinNoticeHours", c.getJournalJoinNoticeHours(), 1, 720);
        require(c.getNoPermissionSound().isEmpty() || SOUND_KEY.matcher(c.getNoPermissionSound()).matches(),
                "Setting noPermissionSound must be empty or a sound key such as entity.villager.no");
        text("invalidArgumentMessage", c.getInvalidArgumentMessage());
        text("playerNotFoundMessage", c.getPlayerNotFoundMessage());
        text("playerOnlyMessage", c.getPlayerOnlyMessage());
        text("consoleOnlyMessage", c.getConsoleOnlyMessage());
        require(c.getUsageMessage().length() <= 256 && c.getUsageMessage().contains("{usage}"),
                "Setting usageMessage must contain {usage} and be at most 256 characters");
        range("journalInfoDays", c.getJournalInfoDays(), 1, 3650);
        range("journalWarningDays", c.getJournalWarningDays(), 1, 3650);
        range("journalErrorDays", c.getJournalErrorDays(), 1, 3650);
        checkUpload(c);
        range("logRecords", c.getLogRecords(), 1, 2_000);
        range("historyRetentionDays", c.getHistoryRetentionDays(), 1, 365);
        require(c.getHistoryMaxBytes() >= 1024 * 1024 && c.getHistoryMaxBytes() <= 256 * 1024 * 1024,
                "Setting historyMaxBytes must be between 1 and 256 MiB");
        require(!c.getCooldown().isNegative() && c.getCooldown().compareTo(Duration.ofHours(1)) <= 0,
                "Setting cooldown must be between 0 and 1 hour");
        range("keepReports", c.getKeepReports(), 1, 1_000);
        require(c.getMaxReportBytes() >= 65_536 && c.getMaxReportBytes() <= 64 * 1024 * 1024,
                "Setting maxReportBytes must be between 64 KiB and 64 MiB");
        range("deleteAfterDays", c.getDeleteAfterDays(), 0, 3_650);
        range("taskHistoryCapacity", c.getTaskHistoryCapacity(), 0, 10_000);
        checkPatterns(c);
    }

    private static void checkUpload(PnLibraryConfig c) {
        require(c.getUploadMode().equals("encrypted") || c.getUploadMode().equals("disabled"),
                "Setting uploadMode must be encrypted or disabled");
        require(!c.getUploadProviders().isEmpty() && Arrays.asList("catbox", "fileio", "custom").containsAll(c.getUploadProviders()),
                "Setting uploadProviders may contain only catbox, fileio, and custom");
        require(!c.getUploadKeyId().trim().isEmpty() && c.getUploadKeyId().length() <= 128, "Setting uploadKeyId is invalid");
    }

    private static void checkPatterns(PnLibraryConfig c) {
        require(c.getSecretKeyPatterns().size() <= 64 && c.getRedactValuePatterns().size() <= 64,
                "Too many diagnostic redaction rules");
        List<String> all = new ArrayList<>(c.getSecretKeyPatterns());
        all.addAll(c.getRedactValuePatterns());
        for (String expression : all) {
            require(!expression.trim().isEmpty() && expression.length() <= 256, "Invalid diagnostic regex");
            try {
                Pattern.compile(expression);
            } catch (PatternSyntaxException exception) {
                throw new IllegalArgumentException("Invalid diagnostic regex: " + expression, exception);
            }
        }
    }

    private static void text(String name, String value) {
        require(!value.trim().isEmpty() && value.length() <= 256, "Setting " + name + " must be 1 to 256 characters");
    }

    private static void range(String name, int value, int min, int max) {
        require(value >= min && value <= max, "Setting " + name + " must be between " + min + " and " + max);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
