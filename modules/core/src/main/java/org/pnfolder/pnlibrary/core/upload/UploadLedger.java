package org.pnfolder.pnlibrary.core.upload;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.common.lang.Attempt;
import org.pnfolder.pnlibrary.common.lang.Failures;

/**
 * Remembers which uploads must be deleted later and deletes the due ones.
 *
 * <p>Receipts without a delete token or {@code deleteAfterDays <= 0} are intentionally skipped. Network failures keep
 * the entry for the next cleanup attempt.
 */
public final class UploadLedger {
    private static final long SECONDS_PER_DAY = 86_400L;

    private final Clock clock;
    private final UploadLedgerPersistence persistence;

    public UploadLedger(@NotNull Path file) {
        this(file, Clock.systemUTC());
    }

    public UploadLedger(@NotNull Path file, @NotNull Clock clock) {
        this.clock = clock;
        this.persistence = new UploadLedgerPersistence(file, clock);
    }

    public synchronized void record(@NotNull UploadReceipt receipt, int deleteAfterDays) throws IOException {
        if (!receipt.canDelete() || deleteAfterDays <= 0) {
            return;
        }
        List<UploadLedgerEntry> entries = persistence.read();
        long createdAt = receipt.getCreatedEpochSeconds() > 0
            ? receipt.getCreatedEpochSeconds() : clock.instant().getEpochSecond();
        UploadLedgerEntry entry = new UploadLedgerEntry(receipt.getBackend(), receipt.getLink().toString(),
            receipt.getId(), receipt.getDeleteToken(), createdAt + deleteAfterDays * SECONDS_PER_DAY);
        for (Iterator<UploadLedgerEntry> it = entries.iterator(); it.hasNext(); ) {
            UploadLedgerEntry old = it.next();
            if (old.getBackend().equals(entry.getBackend()) && old.getId().equals(entry.getId())) {
                it.remove();
            }
        }
        entries.add(entry);
        persistence.write(entries);
    }

    public synchronized int cleanup(@NotNull UploadProvider uploader) throws IOException {
        List<UploadLedgerEntry> remaining = new ArrayList<UploadLedgerEntry>();
        int deleted = 0;
        long now = clock.instant().getEpochSecond();
        for (UploadLedgerEntry entry : persistence.read()) {
            if (entry.getDeleteAt() > now) {
                remaining.add(entry);
            } else if (tryDelete(uploader, entry)) {
                deleted++;
            } else {
                remaining.add(entry);
            }
        }
        persistence.write(remaining);
        return deleted;
    }

    public synchronized @NotNull Map<String, Object> diagnosticSnapshot() {
        // Diagnostics must render even with an unreadable ledger; cleanup() surfaces the I/O error.
        Attempt<List<UploadLedgerEntry>> read = Failures.runCatchingNonFatal(persistence::read);
        List<UploadLedgerEntry> entries = read.getOrDefault(new ArrayList<UploadLedgerEntry>());
        long now = clock.instant().getEpochSecond();
        int due = 0;
        Long next = null;
        Map<String, Integer> byBackend = new LinkedHashMap<String, Integer>();
        for (UploadLedgerEntry entry : entries) {
            if (entry.getDeleteAt() <= now) {
                due++;
            }
            if (next == null || entry.getDeleteAt() < next) {
                next = entry.getDeleteAt();
            }
            Integer count = byBackend.get(entry.getBackend());
            byBackend.put(entry.getBackend(), count == null ? 1 : count + 1);
        }
        Map<String, Object> snapshot = new LinkedHashMap<String, Object>();
        snapshot.put("pendingCount", entries.size());
        snapshot.put("dueCount", due);
        snapshot.put("byBackend", byBackend);
        snapshot.put("nextDeletionEpochSeconds", next);
        return snapshot;
    }

    private static boolean tryDelete(UploadProvider uploader, UploadLedgerEntry entry) {
        try {
            return uploader.delete(new UploadReceipt(URI.create(entry.getLink()), entry.getId(), entry.getToken(), 0, 0,
                entry.getBackend()));
        } catch (Exception failed) {
            // A failed deletion keeps the entry in the ledger, so it is retried on the next cleanup.
            return false;
        }
    }
}
