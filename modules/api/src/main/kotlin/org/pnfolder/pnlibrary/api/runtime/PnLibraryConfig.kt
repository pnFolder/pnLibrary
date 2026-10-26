package org.pnfolder.pnlibrary.api.runtime

import org.pnfolder.pnlibrary.common.lang.runCatchingNonFatal
import java.time.Duration
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

/**
 * Immutable runtime policy for diagnostic collection, retention, and upload.
 *
 * Paths in [excludedPaths] use dotted configuration notation, for example
 * `database.password`. Regular expressions are compiled when this object is
 * created, so an invalid redaction policy fails during startup rather than while
 * a support report is being generated.
 *
 * ```kotlin
 * val config = PnLibraryConfig(
 *     upload = false,
 *     logs = true,
 *     logRecords = 500,
 *     excludedPaths = listOf("database.password"),
 *     secretKeyPatterns = listOf("(?i).*(token|secret|password).*"),
 * )
 * ```
 *
 * @property upload whether completed reports may be sent to an upload provider
 * @property uploadMode upload transport policy: `encrypted` or `disabled`
 * @property uploadProviders providers attempted in order; supported values are
 * `catbox`, `fileio`, and `custom`
 * @property uploadEndpoint endpoint used by the custom upload provider
 * @property uploadPublicBase public URL prefix used to construct custom upload links
 * @property uploadPublicKey Base64-encoded public key used for encrypted reports
 * @property uploadKeyId operator-defined identifier written into encrypted envelopes
 * @property allowPlaintext whether an explicitly configured provider may receive an
 * unencrypted report
 * @property privacy whether personal data is excluded from diagnostic reports;
 * when enabled, player identity, network addresses, ping values, and current server names
 * are not collected
 * @property configs whether registered configuration files are collected by default
 * @property logs whether buffered warning and error records are collected by default
 * @property logRecords maximum number of recent log records included in one report
 * @property historyRetentionDays maximum age of persisted diagnostic history entries
 * @property historyMaxBytes total byte budget for persisted diagnostic history
 * @property cooldown minimum delay between diagnostic commands from one caller
 * @property keepReports maximum number of locally retained report archives
 * @property maxReportBytes maximum uncompressed report size in bytes
 * @property deleteAfterDays age at which local reports are deleted; `0` disables
 * age-based deletion
 * @property taskHistoryCapacity maximum number of completed task records retained in memory;
 * `0` disables task history
 * @property excludedPaths dotted configuration paths omitted from every report
 * @property secretKeyPatterns regular expressions matched against configuration keys
 * whose values must be redacted
 * @property redactValuePatterns regular expressions whose matches are redacted inside
 * scalar configuration values
 * @property journalInfoDays days of journal entries kept at the info level
 * @property journalWarningDays days of journal entries kept at the warning level
 * @property journalErrorDays days of journal entries kept at the error level
 * @property noPermissionMessage message shown when a caller lacks the required permission
 * @property journalForeignPlugins whether journal entries from other plugins are recorded
 * @property timeZone time zone id used when displaying times, such as `UTC`
 * @property journalJoinNotice whether players see a summary of recent journal entries on join
 * @property noPermissionSound sound key played with [noPermissionMessage]; empty disables it
 * @property usageMessage message shown for incorrect command usage; `{usage}` is replaced
 * @property invalidArgumentMessage message shown for an invalid argument value; supports
 * `{value}`, `{argument}`, and `{usage}` placeholders
 * @property playerNotFoundMessage message shown when a named player is not online; `{value}`
 * is replaced
 * @property playerOnlyMessage message shown when a player-only command runs from the console
 * @property consoleOnlyMessage message shown when a console-only command runs in game
 * @property journalJoinNoticeHours hours after join during which the join notice is shown
 * @throws IllegalArgumentException if a numeric limit, provider, mode, path, or regular
 * expression is invalid
 */
data class PnLibraryConfig @JvmOverloads constructor(
    val upload: Boolean = true,
    val uploadMode: String = "encrypted",
    val uploadProviders: List<String> = listOf("catbox", "fileio"),
    val uploadEndpoint: String = "https://api.mclo.gs/1/log",
    val uploadPublicBase: String = "https://mclo.gs/",
    val uploadPublicKey: String = "",
    val uploadKeyId: String = "support-1",
    val allowPlaintext: Boolean = false,
    val configs: Boolean = true,
    val logs: Boolean = true,
    val logRecords: Int = 200,
    val historyRetentionDays: Int = 30,
    val historyMaxBytes: Int = 32 * 1024 * 1024,
    val cooldown: Duration = Duration.ofSeconds(10),
    val keepReports: Int = 10,
    val maxReportBytes: Int = 8 * 1024 * 1024,
    val deleteAfterDays: Int = 90,
    val taskHistoryCapacity: Int = 256,
    val excludedPaths: List<String> = emptyList(),
    val secretKeyPatterns: List<String> = emptyList(),
    val redactValuePatterns: List<String> = emptyList(),
    val privacy: Boolean = true,
    val journalInfoDays: Int = 7,
    val journalWarningDays: Int = 30,
    val journalErrorDays: Int = 90,
    val noPermissionMessage: String = "Недостаточно прав для этой команды.",
    val journalForeignPlugins: Boolean = false,
    val timeZone: String = "UTC",
    val journalJoinNotice: Boolean = true,
    val noPermissionSound: String = "",
    val usageMessage: String = "Использование: {usage}",
    val invalidArgumentMessage: String = "Неверное значение «{value}» для {argument}. {usage}",
    val playerNotFoundMessage: String = "Игрок {value} сейчас не в сети.",
    val playerOnlyMessage: String = "Эту команду можно использовать только в игре.",
    val consoleOnlyMessage: String = "Эту команду можно использовать только из консоли.",
    val journalJoinNoticeHours: Int = 24,
) {
    init {
        require(noPermissionMessage.isNotBlank() && noPermissionMessage.length <= 256) {
            "Setting noPermissionMessage must be 1 to 256 characters"
        }
        require(runCatchingNonFatal { java.time.ZoneId.of(timeZone) }.isSuccess) {
            "Setting timeZone must be a time zone id such as UTC or Europe/Moscow"
        }
        require(journalJoinNoticeHours in 1..720) { "Setting journalJoinNoticeHours must be between 1 and 720" }
        require(noPermissionSound.isEmpty() || Regex("[a-z0-9_.:/-]+").matches(noPermissionSound)) {
            "Setting noPermissionSound must be empty or a sound key such as entity.villager.no"
        }
        listOf(
            "invalidArgumentMessage" to invalidArgumentMessage, "playerNotFoundMessage" to playerNotFoundMessage,
            "playerOnlyMessage" to playerOnlyMessage, "consoleOnlyMessage" to consoleOnlyMessage,
        ).forEach { (name, text) ->
            require(text.isNotBlank() && text.length <= 256) { "Setting $name must be 1 to 256 characters" }
        }
        require(usageMessage.length <= 256 && usageMessage.contains("{usage}")) {
            "Setting usageMessage must contain {usage} and be at most 256 characters"
        }
        require(journalInfoDays in 1..3650) { "Setting journalInfoDays must be between 1 and 3650" }
        require(journalWarningDays in 1..3650) { "Setting journalWarningDays must be between 1 and 3650" }
        require(journalErrorDays in 1..3650) { "Setting journalErrorDays must be between 1 and 3650" }
        require(uploadMode in setOf("encrypted", "disabled")) {
            "Setting uploadMode must be encrypted or disabled"
        }
        require(uploadProviders.isNotEmpty() && uploadProviders.all { it in setOf("catbox", "fileio", "custom") }) {
            "Setting uploadProviders may contain only catbox, fileio, and custom"
        }
        require(logRecords in 1..2_000) { "Setting logRecords must be between 1 and 2000" }
        require(historyRetentionDays in 1..365) { "Setting historyRetentionDays must be between 1 and 365" }
        require(historyMaxBytes in 1024 * 1024..256 * 1024 * 1024) { "Setting historyMaxBytes must be between 1 and 256 MiB" }
        require(!cooldown.isNegative && cooldown <= Duration.ofHours(1)) { "Setting cooldown must be between 0 and 1 hour" }
        require(keepReports in 1..1_000) { "Setting keepReports must be between 1 and 1000" }
        require(maxReportBytes in 65_536..64 * 1024 * 1024) { "Setting maxReportBytes must be between 64 KiB and 64 MiB" }
        require(deleteAfterDays in 0..3_650) { "Setting deleteAfterDays must be between 0 and 3650" }
        require(taskHistoryCapacity in 0..10_000) { "Setting taskHistoryCapacity must be between 0 and 10000" }
        require(excludedPaths.size <= 128 && secretKeyPatterns.size <= 64 && redactValuePatterns.size <= 64) {
            "Too many diagnostic redaction rules"
        }
        require(uploadKeyId.isNotBlank() && uploadKeyId.length <= 128) { "Setting uploadKeyId is invalid" }
        excludedPaths.forEach { require(it.isNotBlank() && it.length <= 256) { "Invalid excluded path: $it" } }
        (secretKeyPatterns + redactValuePatterns).forEach { expression ->
            require(expression.isNotBlank() && expression.length <= 256) { "Invalid diagnostic regex" }
            try {
                Pattern.compile(expression)
            } catch (exception: PatternSyntaxException) {
                throw IllegalArgumentException("Invalid diagnostic regex: $expression", exception)
            }
        }
    }
}
