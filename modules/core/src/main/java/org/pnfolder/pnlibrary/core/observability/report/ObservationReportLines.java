package org.pnfolder.pnlibrary.core.observability.report;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.observability.observation.Observation;
import org.pnfolder.pnlibrary.api.observability.observation.ObservationKeys;
import org.pnfolder.pnlibrary.core.observability.store.ObservationRetention;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** How one stored observation looks in a support report. */
public final class ObservationReportLines {
    private ObservationReportLines() {
    }

    /** The stored observation with readable UTC times, so support sees when it happened and when it expires. */
    @NotNull
    public static Map<String, Object> reportLine(@NotNull Observation observation) {
        Long expires = ObservationRetention.parseMillis(observation.getData().get(ObservationKeys.KEEP_UNTIL_MS));
        Map<String, String> data = new LinkedHashMap<>(observation.getData());
        data.remove(ObservationKeys.KEEP_UNTIL_MS);
        Map<String, Object> line = new LinkedHashMap<>();
        line.put("id", observation.getId());
        line.put("timestamp", observation.getTimestamp());
        line.put("timeUtc", Instant.ofEpochMilli(observation.getTimestamp()).toString());
        line.put("expiresUtc", expires == null ? null : Instant.ofEpochMilli(expires).toString());
        line.put("plugin", observation.getPlugin());
        line.put("source", observation.getSource());
        line.put("level", observation.getLevel());
        line.put("message", observation.getMessage());
        line.put("data", data);
        line.put("errorType", observation.getErrorType());
        line.put("errorMessage", observation.getErrorMessage());
        return line;
    }
}
