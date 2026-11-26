package org.pnfolder.pnlibrary.core.observability;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.observability.ObservabilityService;
import org.pnfolder.pnlibrary.api.observability.observation.Observation;
import org.pnfolder.pnlibrary.api.observability.observation.ObservationKeys;
import org.pnfolder.pnlibrary.api.observability.observation.ObservationQuery;
import org.pnfolder.pnlibrary.api.observability.observation.ObservationRequest;
import org.pnfolder.pnlibrary.api.observability.report.ComponentStatus;
import org.pnfolder.pnlibrary.api.observability.report.ObservabilityReport;
import org.pnfolder.pnlibrary.api.observability.report.ObservabilityReportRequest;
import org.pnfolder.pnlibrary.core.observability.store.AttachmentStore;
import org.pnfolder.pnlibrary.core.observability.store.ComponentStatusRegistry;
import org.pnfolder.pnlibrary.core.observability.store.ObservationJournal;
import org.pnfolder.pnlibrary.core.observability.store.ObservationRetention;
import org.pnfolder.pnlibrary.core.observability.store.StoredAttachment;
import org.pnfolder.pnlibrary.updates.paths.LibraryPaths;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.LongSupplier;

/** Records observations with their attachments and component statuses, and applies retention to them. */
public final class ObservabilityRuntime implements ObservabilityService {
    private final Object recordLock = new Object();
    private final LongSupplier clock;
    private final ObservationJournal journal;
    private final AttachmentStore attachmentStore;
    private final ComponentStatusRegistry statusRegistry = new ComponentStatusRegistry();
    private volatile Function<ObservabilityReportRequest, ObservabilityReport> reportFactory;

    /** A runtime on the system clock; reports are not available until {@link #configureReportFactory} is called. */
    public ObservabilityRuntime(@NotNull LibraryPaths paths) {
        this(paths, System::currentTimeMillis);
    }

    /** A runtime on {@code clock} (epoch milliseconds) without a report builder. */
    public ObservabilityRuntime(@NotNull LibraryPaths paths, @NotNull LongSupplier clock) {
        this(paths, clock, null);
    }

    /**
     * Opens the stores under {@code paths} and drops what has already expired.
     *
     * @param reportFactory builds support reports, or {@code null} until one is configured
     */
    public ObservabilityRuntime(@NotNull LibraryPaths paths, @NotNull LongSupplier clock,
                                @Nullable Function<ObservabilityReportRequest, ObservabilityReport> reportFactory) {
        this.clock = clock;
        this.journal = new ObservationJournal(paths.getObservations(), clock);
        this.attachmentStore = new AttachmentStore(paths.getAttachments());
        this.reportFactory = reportFactory;
        maintain();
    }

    /** Drops expired observations and the attachments that no longer belong to a retained one. */
    public void maintain() {
        synchronized (recordLock) {
            Set<String> activeObservationIds = journal.applyRetention(clock.getAsLong());
            try {
                attachmentStore.removeOrphans(activeObservationIds);
            } catch (IOException failure) {
                throw new UncheckedIOException(failure);
            }
        }
    }

    @NotNull
    @Override
    public Observation record(@NotNull ObservationRequest request) {
        synchronized (recordLock) {
            long timestamp = clock.getAsLong();
            Observation observation = withRetention(request, timestamp);
            try {
                for (Path file : request.getFiles()) {
                    attachmentStore.save(observation.getId(), file);
                }
            } catch (IOException failure) {
                throw new UncheckedIOException(failure);
            }
            journal.append(observation);
            return observation;
        }
    }

    @NotNull
    @Override
    public List<Observation> recent(@NotNull ObservationQuery query) {
        List<Observation> matching = new ArrayList<>();
        for (Observation observation : journal.recent()) {
            if (matches(query, observation)) {
                matching.add(observation);
            }
        }
        int from = Math.max(0, matching.size() - Math.max(query.getLimit(), 0));
        return new ArrayList<>(matching.subList(from, matching.size()));
    }

    @NotNull
    @Override
    public ComponentStatus status(@NotNull ComponentStatus status) {
        return statusRegistry.update(status);
    }

    @NotNull
    @Override
    public ObservabilityReport createReport(@NotNull ObservabilityReportRequest request) {
        Function<ObservabilityReportRequest, ObservabilityReport> factory = reportFactory;
        if (factory == null) {
            throw new IllegalStateException("Observability report builder is not configured");
        }
        return factory.apply(request);
    }

    /** Sets the builder that {@link #createReport} uses. */
    public void configureReportFactory(@NotNull Function<ObservabilityReportRequest, ObservabilityReport> factory) {
        this.reportFactory = factory;
    }

    /** The latest status of every component. */
    @NotNull
    public List<ComponentStatus> statuses() {
        return statusRegistry.snapshot();
    }

    /** The attachments stored for one observation. */
    @NotNull
    public List<StoredAttachment> attachments(@NotNull String observationId) {
        return attachmentStore.forObservation(observationId);
    }

    /** Copies {@code path} into the store as an attachment of an observation. */
    @NotNull
    public StoredAttachment attachFile(@NotNull String observationId, @NotNull Path path) throws IOException {
        synchronized (recordLock) {
            return attachmentStore.save(observationId, path);
        }
    }

    /** Stores {@code bytes} as an attachment of an observation, without a temporary file. */
    @NotNull
    public StoredAttachment attachBytes(@NotNull String observationId, @NotNull String name,
                                        @NotNull String contentType, @NotNull byte[] bytes) throws IOException {
        synchronized (recordLock) {
            return attachmentStore.save(observationId, name, contentType, bytes);
        }
    }

    /** Every stored attachment. */
    @NotNull
    public List<StoredAttachment> allAttachments() {
        return attachmentStore.all();
    }

    /** The content of a stored attachment. */
    @NotNull
    public byte[] attachmentBytes(@NotNull StoredAttachment attachment) throws IOException {
        return attachmentStore.read(attachment);
    }

    /** Counts and rates over the stored observations, attachments and statuses. */
    @NotNull
    public Map<String, Object> analytics() {
        synchronized (recordLock) {
            return ObservationAnalytics.compute(journal.recent(), attachmentStore.all(), statusRegistry.snapshot());
        }
    }

    /** Deletes every observation and attachment. */
    public void clear() throws IOException {
        journal.clear();
        attachmentStore.clear();
    }

    @Override
    public void close() {
        journal.close();
    }

    private Observation withRetention(ObservationRequest request, long timestamp) {
        Map<String, String> data = new LinkedHashMap<>(request.getData());
        Long keepFor = ObservationRetention.parseMillis(data.remove(ObservationKeys.KEEP_FOR_MS));
        Observation stored = Observation.from(new ObservationRequest(request.getPlugin(), request.getSource(),
                request.getMessage(), request.getLevel(), data, request.getFiles(), request.getError()), timestamp);
        if (keepFor == null) {
            return stored;
        }
        Map<String, String> storedData = new LinkedHashMap<>(stored.getData());
        storedData.put(ObservationKeys.KEEP_UNTIL_MS, Long.toString(timestamp + keepFor));
        return new Observation(stored.getId(), stored.getTimestamp(), stored.getPlugin(), stored.getSource(),
                stored.getMessage(), stored.getLevel(), storedData, stored.getErrorType(), stored.getErrorMessage());
    }

    private static boolean matches(ObservationQuery query, Observation observation) {
        boolean matchesPlugin = query.getPlugin() == null || query.getPlugin().equals(observation.getPlugin());
        boolean matchesLevel = query.getMinimumLevel() == null
                || observation.getLevel().ordinal() >= query.getMinimumLevel().ordinal();
        boolean matchesStart = query.getSince() == null || observation.getTimestamp() >= query.getSince();
        boolean matchesEnd = query.getUntil() == null || observation.getTimestamp() <= query.getUntil();
        return matchesPlugin && matchesLevel && matchesStart && matchesEnd;
    }
}
