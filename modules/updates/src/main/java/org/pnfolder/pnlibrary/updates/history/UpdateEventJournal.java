package org.pnfolder.pnlibrary.updates.history;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.updates.release.ProductChange;
import org.pnfolder.pnlibrary.api.updates.state.UpdateActor;
import org.pnfolder.pnlibrary.api.updates.state.UpdatePlanSnapshot;
import org.pnfolder.pnlibrary.api.updates.state.UpdateState;
import org.pnfolder.pnlibrary.common.io.AtomicFiles;
import org.pnfolder.pnlibrary.common.io.TextFiles;
import org.pnfolder.pnlibrary.updates.install.ComponentOperation;
import org.pnfolder.pnlibrary.updates.service.UpdateHealthReport;

/**
 * Durable observations, separate from mutable resolver snapshots and release publication dates.
 *
 * <p>The journal is bounded: events older than the maximum age (by file modification time) and everything beyond the
 * newest {@code maxEvents} are deleted. An observation identical to the one before it is not written again, so
 * repeated automatic checks of an unchanged state do not fill the history.
 */
public final class UpdateEventJournal {
    /** Events older than this many days are pruned. */
    private static final long DEFAULT_MAX_AGE_DAYS = 90L;
    /** Newest events kept when there are more. */
    private static final int DEFAULT_MAX_EVENTS = 1_000;
    /** Pruning runs once per this many writes. */
    private static final int PRUNE_EVERY_WRITES = 25;
    private static final long MAX_EVENT_BYTES = 1024 * 1024;
    private static final Set<String> COMPONENT_OPERATIONS = new HashSet<>(Arrays.asList(
        ComponentOperation.ADDED, ComponentOperation.REMOVED,
        ComponentOperation.VERSION_CHANGED, ComponentOperation.SETTINGS_CHANGED));
    private static final Set<String> OPERATIONS = operations();

    private final Path directory;
    private final Clock clock;
    private final Consumer<String> warning;
    private final Duration maxAge;
    private final int maxEvents;
    private UpdateEvent latest;
    private boolean latestLoaded;
    private int writesSincePrune;

    /** Creates a journal in {@code directory} that keeps 90 days and 1000 events. */
    public UpdateEventJournal(@NotNull Path directory) throws IOException {
        this(directory, Clock.systemUTC());
    }

    /** Creates a journal that takes the time from {@code clock}. */
    public UpdateEventJournal(@NotNull Path directory, @NotNull Clock clock) throws IOException {
        this(directory, clock, message -> {
        });
    }

    /** Creates a journal that takes the time from {@code clock} and reports unreadable entries to {@code warning}. */
    public UpdateEventJournal(@NotNull Path directory, @NotNull Clock clock, @NotNull Consumer<String> warning)
        throws IOException {
        this(directory, clock, warning, Duration.ofDays(DEFAULT_MAX_AGE_DAYS), DEFAULT_MAX_EVENTS);
    }

    /** Creates a journal with its own limits: events older than {@code maxAge} and beyond {@code maxEvents} go. */
    public UpdateEventJournal(@NotNull Path directory, @NotNull Clock clock, @NotNull Consumer<String> warning,
                              @NotNull Duration maxAge, int maxEvents) throws IOException {
        if (maxEvents <= 0) {
            throw new IllegalArgumentException("maxEvents must be positive");
        }
        this.directory = directory;
        this.clock = clock;
        this.warning = warning;
        this.maxAge = maxAge;
        this.maxEvents = maxEvents;
        Files.createDirectories(directory);
        prune();
    }

    private static Set<String> operations() {
        Set<String> all = new HashSet<>(Arrays.asList(
            "STATE_OBSERVED", "CHECK", "STAGE", "FORCE_STAGE", "ROLLBACK", "HEALTH", "HEALTH_ROLLBACK"));
        all.addAll(COMPONENT_OPERATIONS);
        return Collections.unmodifiableSet(all);
    }

    /** Records an observed state of the system, not an assertion about who initiated the operation. */
    @NotNull
    public UpdateEvent record(@NotNull UpdatePlanSnapshot snapshot) throws IOException {
        return record(snapshot, UpdateActor.UNKNOWN, "STATE_OBSERVED");
    }

    /** Records an observed state caused by {@code actor} through {@code operation}. */
    @NotNull
    public synchronized UpdateEvent record(@NotNull UpdatePlanSnapshot snapshot, @NotNull UpdateActor actor,
                                           @NotNull String operation) throws IOException {
        require(OPERATIONS.contains(operation), "Invalid update operation");
        List<UpdateEventChange> changes = new ArrayList<>();
        if (snapshot.getPlan() != null) {
            for (ProductChange change : snapshot.getPlan().getChanges()) {
                changes.add(new UpdateEventChange(change.getProduct().getValue(),
                    change.getFrom() == null ? null : change.getFrom().toString(), change.getTo().toString()));
            }
        }
        UpdateEvent event = new UpdateEvent(UUID.randomUUID(), clock.instant(), snapshot.getId(),
            snapshot.getRevision(), snapshot.getState().name(), changes, actor, operation, take(snapshot.getMessage()));
        UpdateEvent previous = latestEvent();
        if (previous != null && signature(previous).equals(signature(event))) {
            return previous;
        }
        return write(event);
    }

    /**
     * Records a change of a managed component that happened outside an update plan: it appeared, disappeared,
     * changed version without the updater or had its channel or automatic download changed.
     */
    @NotNull
    public UpdateEvent recordComponent(@NotNull String operation, @NotNull String product, @Nullable String from,
                                       @NotNull String to, @Nullable String message) throws IOException {
        return recordComponent(operation, product, from, to, message, UpdateActor.SYSTEM);
    }

    /** Like the shorter form, with the {@code actor} that caused the change. */
    @NotNull
    public synchronized UpdateEvent recordComponent(@NotNull String operation, @NotNull String product,
                                                    @Nullable String from, @NotNull String to,
                                                    @Nullable String message, @NotNull UpdateActor actor)
        throws IOException {
        require(COMPONENT_OPERATIONS.contains(operation), "Invalid component operation");
        return write(new UpdateEvent(UUID.randomUUID(), clock.instant(), UUID.randomUUID(), 0,
            UpdateState.UP_TO_DATE.name(), Collections.singletonList(new UpdateEventChange(product, from, to)), actor,
            operation, take(message)));
    }

    /** Writes the actual source transaction's transitions, never a newly resolved release plan. */
    @NotNull
    public synchronized UpdateEvent recordHealth(@NotNull UpdateHealthReport report) throws IOException {
        UpdateState state = !report.getHealthy() ? UpdateState.FAILED
            : report.getRollback() ? UpdateState.ROLLED_BACK : UpdateState.UP_TO_DATE;
        List<UpdateEventChange> changes = new ArrayList<>();
        for (ProductChange change : report.getChanges()) {
            changes.add(new UpdateEventChange(change.getProduct().getValue(),
                change.getFrom() == null ? null : change.getFrom().toString(), change.getTo().toString()));
        }
        return write(new UpdateEvent(UUID.randomUUID(), clock.instant(), report.getPlanId(), 0, state.name(), changes,
            UpdateActor.SYSTEM, report.getRollback() ? "HEALTH_ROLLBACK" : "HEALTH", take(report.getMessage())));
    }

    @Nullable
    private static String take(@Nullable String text) {
        return text == null || text.length() <= 4096 ? text : text.substring(0, 4096);
    }

    private static String signature(UpdateEvent event) {
        StringBuilder changes = new StringBuilder();
        for (UpdateEventChange change : event.getChanges()) {
            if (changes.length() > 0) {
                changes.append(';');
            }
            changes.append(change.getProduct()).append(':').append(change.getFrom()).append('>').append(change.getTo());
        }
        return event.getOperation() + "|" + event.getState() + "|" + event.getActor().getKind() + ":"
            + event.getActor().getId() + "|" + event.getMessage() + "|" + changes;
    }

    @Nullable
    private UpdateEvent latestEvent() throws IOException {
        if (!latestLoaded) {
            List<UpdateEvent> first = readPage(1, 1, null, true);
            latest = first.isEmpty() ? null : first.get(0);
            latestLoaded = true;
        }
        return latest;
    }

    private UpdateEvent write(UpdateEvent event) throws IOException {
        String json = UpdateEventJson.write(event).toString();
        require(json.getBytes(StandardCharsets.UTF_8).length <= MAX_EVENT_BYTES, "Event exceeds size limit");
        Path target = directory.resolve(event.getId() + ".json");
        Path temporary = Files.createTempFile(directory, ".event-", ".tmp");
        try {
            TextFiles.write(temporary, json, StandardCharsets.UTF_8);
            AtomicFiles.move(temporary, target, false);
        } finally {
            Files.deleteIfExists(temporary);
        }
        latest = event;
        latestLoaded = true;
        if (++writesSincePrune >= PRUNE_EVERY_WRITES) {
            prune();
        }
        return event;
    }

    /** Deletes events older than the maximum age and any beyond the newest {@code maxEvents}; returns the count. */
    public synchronized int prune() throws IOException {
        writesSincePrune = 0;
        int removed = UpdateEventFiles.prune(directory, clock.instant().minus(maxAge).toEpochMilli(), maxEvents);
        if (removed > 0) {
            latestLoaded = false;
        }
        return removed;
    }

    /** Number of readable events; routine checks are not counted. */
    public synchronized int count() throws IOException {
        return readAll(null, false).size();
    }

    /** Number of readable events of one managed product; routine checks are not counted. */
    public synchronized int count(@Nullable String product) throws IOException {
        return readAll(product, false).size();
    }

    /** The first page of ten events, newest first. */
    @NotNull
    public List<UpdateEvent> read() throws IOException {
        return read(1, 10, null);
    }

    /**
     * Reads bounded pages ordered by observation time, optionally restricted to one managed product. Routine checks
     * that found nothing are left out, so the pages show what actually happened.
     */
    @NotNull
    public synchronized List<UpdateEvent> read(int page, int pageSize, @Nullable String product) throws IOException {
        return readPage(page, pageSize, product, false);
    }

    private List<UpdateEvent> readPage(int page, int pageSize, @Nullable String product, boolean includeRoutine)
        throws IOException {
        require(page > 0, "Page must be positive");
        require(pageSize >= 1 && pageSize <= 100, "Page size must be between 1 and 100");
        long offset = Math.min((long) (page - 1) * pageSize, Integer.MAX_VALUE);
        List<UpdateEvent> all = readAll(product, includeRoutine);
        int from = (int) Math.min(offset, all.size());
        return new ArrayList<>(all.subList(from, Math.min(all.size(), from + pageSize)));
    }

    private List<UpdateEvent> readAll(@Nullable String product, boolean includeRoutine) throws IOException {
        List<UpdateEvent> events = new ArrayList<>();
        for (Path path : UpdateEventFiles.list(directory)) {
            UpdateEvent event = readEvent(path);
            if (event != null && matches(event, product) && (includeRoutine || !event.isRoutine())) {
                events.add(event);
            }
        }
        Collections.sort(events, Comparator.comparing(UpdateEvent::getOccurredAt).reversed()
            .thenComparing(Comparator.comparingLong(UpdateEvent::getRevision).reversed())
            .thenComparing(UpdateEvent::getId));
        return events;
    }

    private static boolean matches(UpdateEvent event, @Nullable String product) {
        if (product == null) {
            return true;
        }
        for (UpdateEventChange change : event.getChanges()) {
            if (change.getProduct().equalsIgnoreCase(product)) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    private UpdateEvent readEvent(Path path) {
        try {
            require(Files.size(path) <= MAX_EVENT_BYTES, "Event exceeds size limit");
            JsonObject json = JsonParser.parseString(TextFiles.read(path, StandardCharsets.UTF_8)).getAsJsonObject();
            return UpdateEventJson.read(json, OPERATIONS);
        } catch (Exception error) {
            warning.accept("Unreadable update event " + path.getFileName() + ": " + error.getClass().getSimpleName());
            return null;
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
