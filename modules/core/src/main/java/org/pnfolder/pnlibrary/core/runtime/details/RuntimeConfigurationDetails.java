package org.pnfolder.pnlibrary.core.runtime.details;

import java.util.LinkedHashMap;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.runtime.PnLibraryConfig;

/** Renders the library configuration as a map for the diagnostics report. */
public final class RuntimeConfigurationDetails {
    private RuntimeConfigurationDetails() {
    }

    public static @NotNull Map<String, Object> of(@NotNull PnLibraryConfig config) {
        Map<String, Object> details = new LinkedHashMap<String, Object>();
        details.put("privacy", config.isPrivacy());
        details.put("configs", config.isConfigs());
        details.put("logs", config.isLogs());
        details.put("upload", config.isUpload());
        details.put("uploadMode", config.getUploadMode());
        details.put("historyRetentionDays", config.getHistoryRetentionDays());
        details.put("historyMaxBytes", config.getHistoryMaxBytes());
        details.put("logRecords", config.getLogRecords());
        details.put("taskHistoryCapacity", config.getTaskHistoryCapacity());
        details.put("keepReports", config.getKeepReports());
        details.put("maxReportBytes", config.getMaxReportBytes());
        details.put("redactionRuleCount", config.getSecretKeyPatterns().size() + config.getRedactValuePatterns().size());
        return details;
    }
}
