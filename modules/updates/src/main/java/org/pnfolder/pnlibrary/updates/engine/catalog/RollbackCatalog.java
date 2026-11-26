package org.pnfolder.pnlibrary.updates.engine.catalog;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.updates.engine.transaction.TransactionJournal;
import org.pnfolder.pnlibrary.updates.engine.transaction.TransactionJournalRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Finds saved component sets that can still be rolled back. */
public final class RollbackCatalog {
    private final Path root;
    private final TransactionJournalRepository journals;

    public RollbackCatalog(@NotNull Path root, @NotNull TransactionJournalRepository journals) {
        this.root = root;
        this.journals = journals;
    }

    /** Returns the most recently modified rollback candidate, if any. */
    @Nullable
    public Path latest() throws IOException {
        Path newest = null;
        long newestTime = Long.MIN_VALUE;
        for (Map.Entry<Path, TransactionJournal> candidate : candidates()) {
            long time = Files.getLastModifiedTime(candidate.getKey()).toMillis();
            if (newest == null || time > newestTime) {
                newest = candidate.getKey();
                newestTime = time;
            }
        }
        return newest;
    }

    /** Resolves exactly one saved transaction; never substitutes the latest one. */
    @NotNull
    public Path resolve(@NotNull UUID id) throws IOException {
        Path path = root.toAbsolutePath().normalize().resolve(id.toString()).resolve("journal.json");
        TransactionJournal journal = journals.load(path);
        if (!journal.isSavedSet()) {
            throw new IllegalArgumentException("Internal installation transaction is not a saved component set");
        }
        if (!journal.isRestorable()) {
            throw new IllegalArgumentException("Transaction " + id + " cannot be rolled back from " + journal.getState());
        }
        if (!journals.hasRollbackData(journal)) {
            throw new IllegalArgumentException("Transaction " + id + " has no complete rollback data");
        }
        return path;
    }

    /** Lists validated rollback candidates, newest first, skipping incomplete or corrupt transactions. */
    @NotNull
    public List<Path> all() throws IOException {
        List<Map.Entry<Path, TransactionJournal>> sorted = candidates();
        Collections.sort(sorted, new Comparator<Map.Entry<Path, TransactionJournal>>() {
            @Override
            public int compare(Map.Entry<Path, TransactionJournal> left, Map.Entry<Path, TransactionJournal> right) {
                return created(right.getValue()).compareTo(created(left.getValue()));
            }
        });
        List<Path> paths = new ArrayList<Path>();
        for (Map.Entry<Path, TransactionJournal> entry : sorted) {
            paths.add(entry.getKey());
        }
        return paths;
    }

    private static Instant created(TransactionJournal journal) {
        return journal.getCreatedAt() == null ? Instant.MIN : Instant.parse(journal.getCreatedAt());
    }

    /** An unreadable journal is reported by the repository and skipped, so it cannot hide the other sets. */
    private List<Map.Entry<Path, TransactionJournal>> candidates() throws IOException {
        List<Map.Entry<Path, TransactionJournal>> result = new ArrayList<Map.Entry<Path, TransactionJournal>>();
        for (Map.Entry<Path, TransactionJournal> entry : journals.loadAll()) {
            TransactionJournal journal = entry.getValue();
            if (journal.isSavedSet() && journal.isRestorable() && journals.hasReadableRollbackData(entry.getKey(), journal)) {
                result.add(entry);
            }
        }
        return result;
    }
}
