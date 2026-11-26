package org.pnfolder.pnlibrary.updates.install;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.pnfolder.pnlibrary.api.updates.product.ProductId;
import org.pnfolder.pnlibrary.api.updates.release.ProductChange;
import org.pnfolder.pnlibrary.api.version.SemanticVersion;
import org.pnfolder.pnlibrary.updates.engine.transaction.JournalArtifact;
import org.pnfolder.pnlibrary.updates.engine.transaction.PendingTransaction;
import org.pnfolder.pnlibrary.updates.engine.transaction.TransactionJournal;
import org.pnfolder.pnlibrary.updates.engine.transaction.UpdateProcessSession;
import org.pnfolder.pnlibrary.updates.engine.transaction.UpdateTransaction;
import org.pnfolder.pnlibrary.updates.service.RegisteredUpdate;
import org.pnfolder.pnlibrary.updates.service.UpdateHealthReport;

/** Verifies a staged transaction after the next server start by comparing the loaded versions with the expected ones. */
final class UpdateHealthVerifier {
    private final UpdateTransaction transaction;

    UpdateHealthVerifier(UpdateTransaction transaction) {
        this.transaction = transaction;
    }

    /** Reports every set that waits for verification, oldest first, and closes it as healthy or failed. */
    void reconcile(Map<String, String> installedVersions, HealthReporter reportHealth,
                   boolean recoveredFailuresOnly) throws IOException {
        for (PendingCheck check : pendingChecks(installedVersions, recoveredFailuresOnly)) {
            PendingTransaction pending = check.pending;
            TransactionJournal source = check.journal;
            Map<String, String> mismatches = new LinkedHashMap<>();
            for (Map.Entry<String, String> expected : pending.getExpectedVersions().entrySet()) {
                if (!expected.getValue().equals(installedVersions.get(expected.getKey()))) {
                    mismatches.put(expected.getKey(), expected.getValue());
                }
            }
            boolean healthy = mismatches.isEmpty() && pending.getUnknownProducts().isEmpty();
            UUID id = UUID.fromString(source.getId());
            String message = "Набор " + id + ". " + outcome(pending, mismatches, installedVersions, healthy);
            reportHealth.report(new UpdateHealthReport(id,
                    source.getPlanId() == null ? id : UUID.fromString(source.getPlanId()),
                    healthChanges(pending, source), healthy, pending.getRollback(), message));
            if (pending.getRollback()) {
                transaction.completeRollbackHealth(pending.getJournal(), healthy);
            } else {
                transaction.completeHealth(pending.getJournal(), healthy);
            }
        }
    }

    /** True when nothing waits for verification and none of {@code products} has a failed check. */
    boolean automaticUpdatesHealthy(Set<ProductId> products) throws IOException {
        Set<String> names = new HashSet<>();
        for (ProductId product : products) {
            names.add(product.getValue());
        }
        return transaction.awaitingHealth().isEmpty() && transaction.awaitingRollbackHealth().isEmpty()
                && transaction.automaticUpdatesHealthy(names);
    }

    /** True when a prepared set expects a product that has not registered yet, so verification must wait. */
    boolean awaitingRegistrations(List<RegisteredUpdate> registrations) throws IOException {
        Set<String> installed = new HashSet<>();
        for (RegisteredUpdate registration : registrations) {
            installed.add(registration.getProduct());
        }
        for (PendingTransaction pending : awaiting()) {
            if (pending.getPreparedSession() != null && pending.getPreparedSession().equals(UpdateProcessSession.getId())) {
                continue;
            }
            Set<String> expected = new HashSet<>(pending.getExpectedVersions().keySet());
            expected.addAll(pending.getUnknownProducts());
            if (!installed.containsAll(expected)) {
                return true;
            }
        }
        return false;
    }

    private List<PendingTransaction> awaiting() throws IOException {
        List<PendingTransaction> awaiting = new ArrayList<>(transaction.awaitingHealth());
        awaiting.addAll(transaction.awaitingRollbackHealth());
        return awaiting;
    }

    private List<PendingCheck> pendingChecks(Map<String, String> installedVersions, boolean recoveredFailuresOnly)
            throws IOException {
        List<PendingTransaction> pending = new ArrayList<>();
        if (!recoveredFailuresOnly) {
            pending.addAll(awaiting());
        }
        for (PendingTransaction failed : transaction.failedHealthChecks()) {
            if (failed.getUnknownProducts().isEmpty() && allLoaded(failed, installedVersions)) {
                pending.add(failed);
            }
        }
        List<PendingCheck> checks = new ArrayList<>();
        for (PendingTransaction candidate : pending) {
            if (candidate.getPreparedSession() != null && candidate.getPreparedSession().equals(UpdateProcessSession.getId())) {
                continue;
            }
            checks.add(new PendingCheck(candidate, transaction.readJournal(candidate.getJournal())));
        }
        Collections.sort(checks, Comparator.comparing(PendingCheck::created));
        return checks;
    }

    private boolean allLoaded(PendingTransaction pending, Map<String, String> installedVersions) {
        for (Map.Entry<String, String> expected : pending.getExpectedVersions().entrySet()) {
            if (!expected.getValue().equals(installedVersions.get(expected.getKey()))) {
                return false;
            }
        }
        return true;
    }

    private String outcome(PendingTransaction pending, Map<String, String> mismatches,
                           Map<String, String> installedVersions, boolean healthy) {
        if (healthy) {
            List<String> confirmed = new ArrayList<>();
            for (Map.Entry<String, String> entry : pending.getExpectedVersions().entrySet()) {
                confirmed.add(entry.getKey() + "=" + entry.getValue());
            }
            return "Подтверждены загруженные версии: " + String.join(", ", confirmed);
        }
        List<String> details = new ArrayList<>();
        for (Map.Entry<String, String> entry : mismatches.entrySet()) {
            String loaded = installedVersions.get(entry.getKey());
            details.add(entry.getKey() + ": ожидалась " + entry.getValue() + ", загружена "
                    + (loaded == null ? "не загружена" : loaded));
        }
        for (String unknown : pending.getUnknownProducts()) {
            details.add(unknown + ": ожидаемая версия не записана в старом журнале");
        }
        return "Версии после перезапуска не подтвердились: " + String.join(", ", details);
    }

    private List<ProductChange> healthChanges(PendingTransaction pending, TransactionJournal source) {
        List<ProductChange> changes = new ArrayList<>();
        for (JournalArtifact artifact : source.getArtifacts()) {
            SemanticVersion previous = artifact.getPreviousVersion() == null ? null
                    : SemanticVersion.tryParse(artifact.getPreviousVersion());
            SemanticVersion updated = artifact.getExpectedVersion() == null ? null
                    : SemanticVersion.tryParse(artifact.getExpectedVersion());
            ProductId product = ProductId.of(artifact.getComponent());
            if (pending.getRollback() && previous != null && updated != null) {
                changes.add(new ProductChange(product, updated, previous));
            } else if (!pending.getRollback() && updated != null && (previous != null || !artifact.getTargetExisted())) {
                changes.add(new ProductChange(product, previous, updated));
            }
        }
        return changes;
    }

    private static final class PendingCheck {
        private final PendingTransaction pending;
        private final TransactionJournal journal;

        PendingCheck(PendingTransaction pending, TransactionJournal journal) {
            this.pending = pending;
            this.journal = journal;
        }

        Instant created() {
            return journal.getCreatedAt() == null ? Instant.MIN : Instant.parse(journal.getCreatedAt());
        }
    }
}
