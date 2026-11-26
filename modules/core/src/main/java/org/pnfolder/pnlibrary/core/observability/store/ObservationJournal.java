package org.pnfolder.pnlibrary.core.observability.store;

import java.util.Objects;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.observability.observation.Observation;
import org.pnfolder.pnlibrary.common.io.TextFiles;
import org.pnfolder.pnlibrary.common.lang.Failures;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.LongSupplier;

/**
 * Append-only JSON Lines journal of observations that keeps only what retention allows.
 *
 * <p>Expired observations are cut from the file once a minute or when the list outgrows its bound by a margin,
 * not on every append; {@link #recent()} never shows an expired one meanwhile.
 */
public final class ObservationJournal implements AutoCloseable {
    private static final long RETENTION_INTERVAL_MILLIS = 60_000L;
    /** How far past the bound the list may grow before it is cut at once, so a full journal is not rewritten per append. */
    private static final int OVERFLOW_MARGIN = 500;

    private final Path file;
    private final LongSupplier clock;
    private final BiFunction<Collection<Observation>, Long, List<Observation>> retain;
    private final Gson gson = new GsonBuilder().disableHtmlEscaping().create();
    private final Object lock = new Object();
    private final ArrayDeque<Observation> observations = new ArrayDeque<>();
    private Writer writer;
    private boolean retentionRan;
    private long lastRetentionAt;

    /** A journal on the system clock with the standard retention. */
    public ObservationJournal(@NotNull Path file) {
        this(file, System::currentTimeMillis);
    }

    /** A journal with the standard retention. */
    public ObservationJournal(@NotNull Path file, @NotNull LongSupplier clock) {
        this(file, clock, ObservationRetention::retained);
    }

    /**
     * Creates the journal and reads the observations of earlier runs.
     *
     * @param clock current time in epoch milliseconds
     * @param retain keeps the observations that are still active at a time; replaceable so tests can count its use
     */
    public ObservationJournal(@NotNull Path file, @NotNull LongSupplier clock,
                              @NotNull BiFunction<Collection<Observation>, Long, List<Observation>> retain) {
        this.file = Objects.requireNonNull(file, "file");
        this.clock = clock;
        this.retain = retain;
        try {
            Files.createDirectories(file.getParent());
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
        load();
    }

    /** Adds an observation to memory and to the end of the file. */
    public void append(@NotNull Observation observation) {
        synchronized (lock) {
            observations.addLast(observation);
            try {
                write(observation);
            } catch (IOException failure) {
                throw new UncheckedIOException(failure);
            }
            long now = clock.getAsLong();
            if (retentionDue(now)) {
                retentionRan = true;
                lastRetentionAt = now;
                List<Observation> active = retain.apply(observations, now);
                if (active.size() != observations.size()) {
                    replaceAndRewrite(active);
                }
            }
        }
    }

    /** The observations that are still active now, oldest first. */
    @NotNull
    public List<Observation> recent() {
        synchronized (lock) {
            return retain.apply(observations, clock.getAsLong());
        }
    }

    /** Forgets every observation and deletes the file. */
    public void clear() {
        synchronized (lock) {
            try {
                closeWriter();
                observations.clear();
                Files.deleteIfExists(file);
            } catch (IOException failure) {
                throw new UncheckedIOException(failure);
            }
        }
    }

    /** Cuts the expired observations now and returns the ids of those that stay. */
    @NotNull
    public Set<String> applyRetention(long now) {
        synchronized (lock) {
            List<Observation> active = retain.apply(observations, now);
            replaceAndRewrite(active);
            Set<String> ids = new LinkedHashSet<>();
            for (Observation observation : active) {
                ids.add(observation.getId());
            }
            return ids;
        }
    }

    @Override
    public void close() {
        synchronized (lock) {
            try {
                closeWriter();
            } catch (IOException failure) {
                throw new UncheckedIOException(failure);
            }
        }
    }

    private boolean retentionDue(long now) {
        return observations.size() > ObservationRetention.MAX_OBSERVATIONS + OVERFLOW_MARGIN
                || !retentionRan || now - lastRetentionAt >= RETENTION_INTERVAL_MILLIS || now < lastRetentionAt;
    }

    private void write(Observation observation) throws IOException {
        if (writer == null) {
            writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        }
        writer.write(gson.toJson(observation));
        writer.write("\n");
        writer.flush();
    }

    private void replaceAndRewrite(List<Observation> active) {
        observations.clear();
        observations.addAll(active);
        try {
            rewrite();
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private void rewrite() throws IOException {
        closeWriter();
        if (observations.isEmpty()) {
            Files.deleteIfExists(file);
            return;
        }
        StringBuilder content = new StringBuilder();
        for (Observation observation : observations) {
            content.append(gson.toJson(observation)).append('\n');
        }
        TextFiles.write(file, content, StandardCharsets.UTF_8);
    }

    private void load() {
        synchronized (lock) {
            if (!Files.isRegularFile(file)) {
                return;
            }
            try {
                for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                    Observation observation = Failures.runCatchingNonFatal(
                            () -> gson.fromJson(line, Observation.class)).getOrNull();
                    if (observation != null) {
                        observations.addLast(observation);
                    }
                }
            } catch (IOException failure) {
                throw new UncheckedIOException(failure);
            }
        }
    }

    private void closeWriter() throws IOException {
        Writer open = writer;
        if (open == null) {
            return;
        }
        writer = null;
        open.close();
    }
}
