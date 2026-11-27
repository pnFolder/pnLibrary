package org.pnfolder.pnlibrary.updates.engine.transaction.journal;

import org.pnfolder.pnlibrary.updates.engine.transaction.TransactionArtifact;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Checks a transaction request and turns its artifacts into the durable records of the journal. */
public final class JournalRecords {
    private static final Set<String> NEW_PURPOSES = new HashSet<>(Arrays.asList("STAGE", "INSTALL", "RESTORE"));

    private JournalRecords() {
    }

    public static void requirePreparable(List<TransactionArtifact> artifacts, String purpose, Integer targetApi,
                                  Path publicationRoot, TransactionJournalRepository journals) throws IOException {
        if (!NEW_PURPOSES.contains(purpose)) {
            throw new IllegalArgumentException("Invalid transaction purpose");
        }
        if (targetApi != null && targetApi <= 0) {
            throw new IllegalArgumentException("Invalid target API");
        }
        if (artifacts.isEmpty()) {
            throw new IllegalArgumentException("Transaction must contain at least one artifact");
        }
        Set<Object> products = new HashSet<>();
        Set<Path> targets = new HashSet<>();
        for (TransactionArtifact artifact : artifacts) {
            products.add(artifact.getSpecification().getProduct());
            targets.add(artifact.getTarget().toAbsolutePath().normalize());
        }
        if (products.size() != artifacts.size()) {
            throw new IllegalArgumentException("Transaction contains duplicate components");
        }
        if (targets.size() != artifacts.size()) {
            throw new IllegalArgumentException("Transaction contains duplicate targets");
        }
        for (TransactionArtifact artifact : artifacts) {
            Path target = artifact.getTarget().toAbsolutePath().normalize();
            if (!target.startsWith(publicationRoot)) {
                throw new IllegalArgumentException("Publication target must stay inside " + publicationRoot);
            }
            journals.checkedPath(target, publicationRoot);
        }
    }

    public static List<JournalArtifact> create(List<TransactionArtifact> artifacts, Path staging, Path backups) {
        List<JournalArtifact> records = new ArrayList<>();
        for (int index = 0; index < artifacts.size(); index++) {
            TransactionArtifact artifact = artifacts.get(index);
            String version = artifact.getSpecification().getVersion().toString();
            String name = index + "-" + artifact.getTarget().getFileName();
            JournalVersion expected = new JournalVersion(version, artifact.getSpecification().getSupportedApi().getMinimum(),
                artifact.getSpecification().getSupportedApi().getMaximum());
            JournalVersion prior = artifact.getPreviousApi() == null || artifact.getPreviousVersion() == null ? null
                : new JournalVersion(artifact.getPreviousVersion(), artifact.getPreviousApi().getMinimum(),
                artifact.getPreviousApi().getMaximum());
            records.add(new JournalArtifact(artifact.getSpecification().getProduct().getValue(),
                artifact.getTarget().toAbsolutePath().normalize().toString(), staging.resolve(name).toString(),
                backups.resolve(name).toString(), Files.exists(artifact.getRollbackSource()), version,
                artifact.getPreviousVersion(), path(artifact.getInstalledTarget()), expected, prior, null));
        }
        return records;
    }

    private static String path(Path path) {
        return path == null ? null : path.toAbsolutePath().normalize().toString();
    }
}
