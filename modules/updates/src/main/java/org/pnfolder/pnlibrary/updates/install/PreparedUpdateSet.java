package org.pnfolder.pnlibrary.updates.install;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.updates.product.ProductId;
import org.pnfolder.pnlibrary.api.version.ApiVersionRange;
import org.pnfolder.pnlibrary.api.version.SemanticVersion;
import org.pnfolder.pnlibrary.updates.engine.UpdatePaths;
import org.pnfolder.pnlibrary.updates.engine.net.ArtifactSpecification;
import org.pnfolder.pnlibrary.updates.engine.net.ArtifactVerifier;
import org.pnfolder.pnlibrary.updates.engine.transaction.journal.JournalArtifact;
import org.pnfolder.pnlibrary.updates.engine.transaction.journal.JournalVersion;
import org.pnfolder.pnlibrary.updates.engine.transaction.TransactionArtifact;
import org.pnfolder.pnlibrary.updates.engine.transaction.journal.TransactionJournal;
import org.pnfolder.pnlibrary.updates.engine.transaction.TransactionState;
import org.pnfolder.pnlibrary.updates.engine.transaction.UpdateTransaction;
import org.pnfolder.pnlibrary.updates.history.configuration.ConfigurationSnapshots;

/** A set whose files are checked and ready to be published. */
final class PreparedUpdateSet {
    final UUID id;
    final Path journal;
    final String purpose;
    final List<TransactionArtifact> artifacts;
    final List<Path> pendingFiles;
    @Nullable
    final UUID configurationSnapshot;

    private PreparedUpdateSet(UUID id, Path journal, String purpose, List<TransactionArtifact> artifacts,
                              List<Path> pendingFiles, @Nullable UUID configurationSnapshot) {
        this.id = id;
        this.journal = journal;
        this.purpose = purpose;
        this.artifacts = artifacts;
        this.pendingFiles = pendingFiles;
        this.configurationSnapshot = configurationSnapshot;
    }

    /** Checks the journal and every artifact of a pending set before anything is touched. */
    static PreparedUpdateSet read(UpdateTransaction transactions, ArtifactVerifier verifier,
                                  ConfigurationSnapshots snapshots, Path plugins, Path path) throws IOException {
        TransactionJournal journal = transactions.readJournal(path);
        boolean restoring = journal.getState() == TransactionState.ROLLED_BACK;
        UUID configuration = restoring && journal.getRestoreConfigurations() ? checkedSnapshot(snapshots, journal) : null;
        List<TransactionArtifact> artifacts = new ArrayList<>();
        List<Path> pendingFiles = new ArrayList<>();
        for (JournalArtifact record : journal.getArtifacts()) {
            artifacts.add(artifact(record, restoring, verifier, plugins));
            pendingFiles.add(Paths.get(record.getTarget()));
        }
        return new PreparedUpdateSet(UUID.fromString(journal.getId()), path, restoring ? "RESTORE" : "INSTALL",
                artifacts, pendingFiles, configuration);
    }

    private static UUID checkedSnapshot(ConfigurationSnapshots snapshots, TransactionJournal journal) throws IOException {
        String saved = journal.getConfigurationSnapshot();
        if (saved == null) {
            throw new IllegalArgumentException("No configuration snapshot was retained");
        }
        UUID id = UUID.fromString(saved);
        if (!Objects.equals(snapshots.read(id).getPlanId(), journal.getPlanId())) {
            throw new IllegalArgumentException("Configuration snapshot belongs to another plan");
        }
        return id;
    }

    private static TransactionArtifact artifact(JournalArtifact record, boolean restoring, ArtifactVerifier verifier,
                                                Path plugins) throws IOException {
        if (record.getInstalledTarget() == null) {
            throw new IllegalArgumentException("Legacy transaction lacks an installation target; manual installation is required");
        }
        Path target = UpdatePaths.inside(Paths.get(record.getInstalledTarget()), plugins);
        require(plugins.equals(target.getParent()) && target.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".jar"),
                "Invalid installed JAR target");
        Path pending = Paths.get(record.getTarget()).toAbsolutePath().normalize();
        require(plugins.resolve("update").equals(pending.getParent()) && target.getFileName().equals(pending.getFileName()),
                "Invalid prepared update location");
        if (restoring) {
            require(record.getTargetExisted(), "Rollback of newly installed components requires explicit removal support");
        }
        JournalVersion version = restoring ? record.getPrior() : record.getExpected();
        if (version == null) {
            throw new IllegalArgumentException("Transaction lacks verified version metadata");
        }
        if (version.getSha256() == null) {
            throw new IllegalArgumentException("Transaction lacks a preserved artifact checksum");
        }
        Path source = Paths.get(restoring ? record.getBackup() : record.getStaged());
        ArtifactSpecification specification = new ArtifactSpecification(ProductId.of(record.getComponent()),
                SemanticVersion.parse(version.getVersion()),
                new ApiVersionRange(version.getMinimumApi(), version.getMaximumApi()),
                source.getFileName().toString(), Files.size(source), version.getSha256());
        verifier.verify(source, specification);
        return new TransactionArtifact(specification, source, target);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
