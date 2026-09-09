package ru.privatenull.pnlibrary.api.runtime

/**
 * Settings controlling diagnostic report collection, security redaction, and uploading.
 */
data class PnLibraryConfig @JvmOverloads constructor(
    val upload: Boolean = true,
    val uploadMode: String = "encrypted-catbox",
    val uploadEndpoint: String = "https://api.mclo.gs/1/log",
    val uploadPublicBase: String = "https://mclo.gs/",
    val uploadPublicKey: String = "",
    val uploadKeyId: String = "support-1",
    val allowPlaintext: Boolean = false,
    val configs: Boolean = true,
    val logs: Boolean = true,
    val logRecords: Int = 200,
    val cooldownSeconds: Int = 10,
    val keepReports: Int = 10,
    val maxReportBytes: Int = 8 * 1024 * 1024,
    val deleteAfterDays: Int = 90,
    val excludedPaths: List<String> = emptyList(),
    val secretKeyPatterns: List<String> = emptyList(),
    val redactValuePatterns: List<String> = emptyList(),
) {
    init {
        require(uploadMode in setOf("encrypted-catbox", "encrypted-mclogs", "encrypted", "mclogs", "disabled")) {
            "uploadMode must be encrypted-catbox, encrypted-mclogs, encrypted, mclogs, or disabled"
        }
        require(uploadMode != "mclogs" || allowPlaintext) {
            "allowPlaintext must be true when uploadMode is mclogs"
        }
        require(logRecords in 1..2_000) { "logRecords must be between 1 and 2000" }
        require(cooldownSeconds in 0..3_600) { "cooldownSeconds must be between 0 and 3600" }
        require(keepReports in 1..1_000) { "keepReports must be between 1 and 1000" }
        require(maxReportBytes in 65_536..64 * 1024 * 1024) { "maxReportBytes must be between 64 KiB and 64 MiB" }
        require(deleteAfterDays in 0..3_650) { "deleteAfterDays must be between 0 and 3650" }
        require(excludedPaths.size <= 128 && secretKeyPatterns.size <= 64 && redactValuePatterns.size <= 64) {
            "too many diagnostic redaction rules"
        }
        require(uploadKeyId.isNotBlank() && uploadKeyId.length <= 128) { "uploadKeyId is invalid" }
        excludedPaths.forEach { require(it.isNotBlank() && it.length <= 256) { "invalid excluded path: $it" } }
        (secretKeyPatterns + redactValuePatterns).forEach { expression ->
            require(expression.isNotBlank() && expression.length <= 256) { "invalid diagnostic regex" }
            require(runCatching { java.util.regex.Pattern.compile(expression) }.isSuccess) {
                "invalid diagnostic regex: $expression"
            }
        }
    }
}
