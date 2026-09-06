package ru.privatenull.pnlibrary.api.runtime

/**
 * Settings controlling diagnostic report collection, security redaction, and uploading.
 */
data class PnLibraryConfig @JvmOverloads constructor(
    val upload: Boolean = true,
    val uploadMode: String = "encrypted-mclogs",
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
)
