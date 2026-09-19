package ru.privatenull.pnlibrary.api.runtime

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
 * @property configs whether registered configuration files are collected by default
 * @property logs whether buffered warning and error records are collected by default
 * @property logRecords maximum number of recent log records included in one report
 * @property historyRetentionDays maximum age of persisted diagnostic history entries
 * @property historyMaxBytes total byte budget for persisted diagnostic history
 * @property cooldownSeconds minimum delay between diagnostic commands from one caller
 * @property keepReports maximum number of locally retained report archives
 * @property maxReportBytes maximum uncompressed report size in bytes
 * @property deleteAfterDays age at which local reports are deleted; `0` disables
 * age-based deletion
 * @property excludedPaths dotted configuration paths omitted from every report
 * @property secretKeyPatterns regular expressions matched against configuration keys
 * whose values must be redacted
 * @property redactValuePatterns regular expressions whose matches are redacted inside
 * scalar configuration values
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
    val cooldownSeconds: Int = 10,
    val keepReports: Int = 10,
    val maxReportBytes: Int = 8 * 1024 * 1024,
    val deleteAfterDays: Int = 90,
    val taskHistoryCapacity: Int = 256,
    val excludedPaths: List<String> = emptyList(),
    val secretKeyPatterns: List<String> = emptyList(),
    val redactValuePatterns: List<String> = emptyList(),
) {
    init {
        require(uploadMode in setOf("encrypted", "disabled")) {
            "uploadMode must be encrypted or disabled"
        }
        require(uploadProviders.isNotEmpty() && uploadProviders.all { it in setOf("catbox", "fileio", "custom") }) {
            "uploadProviders may contain catbox, fileio, and custom"
        }
        require(logRecords in 1..2_000) { "logRecords must be between 1 and 2000" }
        require(historyRetentionDays in 1..365) { "historyRetentionDays must be between 1 and 365" }
        require(historyMaxBytes in 1024 * 1024..256 * 1024 * 1024) { "historyMaxBytes must be between 1 and 256 MiB" }
        require(cooldownSeconds in 0..3_600) { "cooldownSeconds must be between 0 and 3600" }
        require(keepReports in 1..1_000) { "keepReports must be between 1 and 1000" }
        require(maxReportBytes in 65_536..64 * 1024 * 1024) { "maxReportBytes must be between 64 KiB and 64 MiB" }
        require(deleteAfterDays in 0..3_650) { "deleteAfterDays must be between 0 and 3650" }
        require(taskHistoryCapacity in 0..10_000) { "taskHistoryCapacity must be between 0 and 10000" }
        require(excludedPaths.size <= 128 && secretKeyPatterns.size <= 64 && redactValuePatterns.size <= 64) {
            "too many diagnostic redaction rules"
        }
        require(uploadKeyId.isNotBlank() && uploadKeyId.length <= 128) { "uploadKeyId is invalid" }
        excludedPaths.forEach { require(it.isNotBlank() && it.length <= 256) { "invalid excluded path: $it" } }
        (secretKeyPatterns + redactValuePatterns).forEach { expression ->
            require(expression.isNotBlank() && expression.length <= 256) { "invalid diagnostic regex" }
            try {
                Pattern.compile(expression)
            } catch (exception: PatternSyntaxException) {
                throw IllegalArgumentException("invalid diagnostic regex: $expression", exception)
            }
        }
    }
}
