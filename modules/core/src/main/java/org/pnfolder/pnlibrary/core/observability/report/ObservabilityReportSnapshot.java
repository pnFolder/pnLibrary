package org.pnfolder.pnlibrary.core.observability.report;

import java.util.Objects;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.observability.observation.Observation;
import org.pnfolder.pnlibrary.api.observability.observation.ObservationQuery;
import org.pnfolder.pnlibrary.api.observability.report.ComponentStatus;
import org.pnfolder.pnlibrary.core.observability.ObservabilityRuntime;
import org.pnfolder.pnlibrary.core.observability.store.StoredAttachment;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Observation journal, attachments, statuses and analytics captured for one support report. */
public final class ObservabilityReportSnapshot {
    private final byte[] journal;
    private final byte[] attachmentManifest;
    private final Map<String, byte[]> attachments;
    private final List<Map<String, Object>> statuses;
    private final Map<String, Object> analytics;

    /** A snapshot without statuses or analytics. */
    public ObservabilityReportSnapshot(@NotNull byte[] journal, @NotNull byte[] attachmentManifest,
                                       @NotNull Map<String, byte[]> attachments) {
        this(journal, attachmentManifest, attachments, Collections.emptyList());
    }

    /** A snapshot without analytics. */
    public ObservabilityReportSnapshot(@NotNull byte[] journal, @NotNull byte[] attachmentManifest,
                                       @NotNull Map<String, byte[]> attachments,
                                       @NotNull List<Map<String, Object>> statuses) {
        this(journal, attachmentManifest, attachments, statuses, Collections.emptyMap());
    }

    /**
     * Creates a snapshot.
     *
     * @param journal observations as JSON lines
     * @param attachmentManifest JSON list describing the attachments
     * @param attachments attachment content by attachment id
     */
    public ObservabilityReportSnapshot(@NotNull byte[] journal, @NotNull byte[] attachmentManifest,
                                       @NotNull Map<String, byte[]> attachments,
                                       @NotNull List<Map<String, Object>> statuses,
                                       @NotNull Map<String, Object> analytics) {
        this.journal = Objects.requireNonNull(journal, "journal");
        this.attachmentManifest = Objects.requireNonNull(attachmentManifest, "attachmentManifest");
        this.attachments = Objects.requireNonNull(attachments, "attachments");
        this.statuses = statuses;
        this.analytics = analytics;
    }

    /** A snapshot with nothing in it. */
    @NotNull
    public static ObservabilityReportSnapshot empty() {
        return new ObservabilityReportSnapshot(new byte[0], new byte[0], Collections.emptyMap());
    }

    /** Captures the journal, attachments, statuses and analytics of {@code runtime} now. */
    @NotNull
    public static ObservabilityReportSnapshot of(@NotNull ObservabilityRuntime runtime) {
        Gson gson = new GsonBuilder().disableHtmlEscaping().create();
        StringBuilder lines = new StringBuilder();
        for (Observation observation : runtime.recent(new ObservationQuery(Integer.MAX_VALUE))) {
            lines.append(gson.toJson(ObservationReportLines.reportLine(observation))).append('\n');
        }
        List<StoredAttachment> stored = runtime.allAttachments();
        List<Map<String, Object>> manifest = new ArrayList<>();
        Map<String, byte[]> content = new LinkedHashMap<>();
        try {
            for (StoredAttachment attachment : stored) {
                manifest.add(manifestEntry(attachment));
                content.put(attachment.getId(), runtime.attachmentBytes(attachment));
            }
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
        List<Map<String, Object>> statuses = new ArrayList<>();
        for (ComponentStatus status : runtime.statuses()) {
            statuses.add(statusEntry(status));
        }
        return new ObservabilityReportSnapshot(lines.toString().getBytes(StandardCharsets.UTF_8),
                gson.toJson(manifest).getBytes(StandardCharsets.UTF_8), content, statuses, runtime.analytics());
    }

    /** Observations as JSON lines. */
    @NotNull
    public byte[] getJournal() {
        return journal;
    }

    /** JSON list describing the attachments. */
    @NotNull
    public byte[] getAttachmentManifest() {
        return attachmentManifest;
    }

    /** Attachment content by attachment id. */
    @NotNull
    public Map<String, byte[]> getAttachments() {
        return attachments;
    }

    /** The latest status of every component. */
    @NotNull
    public List<Map<String, Object>> getStatuses() {
        return statuses;
    }

    /** Counts and rates over the observations. */
    @NotNull
    public Map<String, Object> getAnalytics() {
        return analytics;
    }

    private static Map<String, Object> manifestEntry(StoredAttachment attachment) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("id", attachment.getId());
        entry.put("eventId", attachment.getObservationId());
        entry.put("name", attachment.getOriginalName());
        entry.put("contentType", attachment.getContentType());
        entry.put("size", attachment.getSize());
        entry.put("sha256", attachment.getSha256());
        return entry;
    }

    private static Map<String, Object> statusEntry(ComponentStatus status) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("plugin", status.getPlugin());
        entry.put("component", status.getComponent());
        entry.put("state", status.getState());
        entry.put("detail", status.getDetail());
        entry.put("data", status.getData());
        entry.put("updatedAt", status.getUpdatedAt());
        return entry;
    }
}
