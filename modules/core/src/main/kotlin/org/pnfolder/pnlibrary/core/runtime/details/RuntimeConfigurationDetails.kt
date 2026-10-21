package org.pnfolder.pnlibrary.core.runtime.details

import org.pnfolder.pnlibrary.api.runtime.PnLibraryConfig

/** Stable, privacy-aware representation of runtime configuration for diagnostics. */
internal object RuntimeConfigurationDetails {
    fun of(config: PnLibraryConfig): Map<String, Any?> = linkedMapOf(
        "privacy" to config.privacy,
        "configs" to config.configs,
        "logs" to config.logs,
        "upload" to config.upload,
        "uploadMode" to config.uploadMode,
        "historyRetentionDays" to config.historyRetentionDays,
        "historyMaxBytes" to config.historyMaxBytes,
        "logRecords" to config.logRecords,
        "taskHistoryCapacity" to config.taskHistoryCapacity,
        "keepReports" to config.keepReports,
        "maxReportBytes" to config.maxReportBytes,
        "excludedPathCount" to config.excludedPaths.size,
        "redactionRuleCount" to (config.secretKeyPatterns.size + config.redactValuePatterns.size),
    )
}
