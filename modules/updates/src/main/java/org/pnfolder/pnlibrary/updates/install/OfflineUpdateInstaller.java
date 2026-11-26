package org.pnfolder.pnlibrary.updates.install;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.common.lang.Failures;
import org.pnfolder.pnlibrary.updates.engine.net.ArtifactSpecification;
import org.pnfolder.pnlibrary.updates.engine.net.ArtifactVerifier;
import org.pnfolder.pnlibrary.updates.engine.transaction.TransactionArtifact;
import org.pnfolder.pnlibrary.updates.engine.transaction.UpdateTransaction;
import org.pnfolder.pnlibrary.updates.history.ConfigurationRestorer;
import org.pnfolder.pnlibrary.updates.history.ConfigurationSnapshots;
import org.pnfolder.pnlibrary.updates.paths.LegacyPathsMigration;
import org.pnfolder.pnlibrary.updates.paths.LibraryPaths;
import org.pnfolder.pnlibrary.updates.service.UpdateInstallationLock;

/** Pre-start installer. Run from its separate updater JAR, never from a loaded server plugin JAR. */
public final class OfflineUpdateInstaller {
    private static final long MAXIMUM_ARTIFACT_BYTES = 512L * 1024 * 1024;

    private OfflineUpdateInstaller() {
    }

    /** Requires explicit stopped-server acknowledgement; an active runtime also holds a file lock. */
    public static void main(@NotNull String[] arguments) throws IOException {
        if (arguments.length != 2 || !arguments[0].equals("--server-stopped")) {
            throw new IllegalArgumentException("Usage: java -jar pnLibrary-updater.jar --server-stopped <plugins/pnLibrary>");
        }
        int applied = apply(Paths.get(arguments[1]));
        System.out.println("Applied " + applied + " prepared component sets. Start the server to verify loaded versions.");
    }

    /** Installs every prepared set found in the plugin folder {@code pluginFolder} and returns how many were applied. */
    public static int apply(@NotNull Path pluginFolder) throws IOException {
        Path data = pluginFolder.toAbsolutePath().normalize();
        if (!Files.isDirectory(data) || data.getParent() == null) {
            throw new IllegalArgumentException("Library data directory does not exist");
        }
        LibraryPaths layout = new LibraryPaths(data);
        new LegacyPathsMigration(layout, System.err::println).run();
        try (UpdateInstallationLock lock = UpdateInstallationLock.acquire(layout)) {
            Path plugins = layout.getPlugins();
            ArtifactVerifier verifier = new ArtifactVerifier(MAXIMUM_ARTIFACT_BYTES);
            UpdateTransaction transactions = new UpdateTransaction(layout.getTransactions(), verifier, plugins);
            ConfigurationSnapshots snapshots = new ConfigurationSnapshots(layout.getConfigurations(), plugins);
            ConfigurationRestorer restorer = new ConfigurationRestorer(snapshots, layout.getConfigurationRestores());
            restorer.recoverInterrupted();
            transactions.recoverAllOffline();
            List<PreparedUpdateSet> pending = new ArrayList<>();
            for (Path path : transactions.pendingInstallations()) {
                pending.add(PreparedUpdateSet.read(transactions, verifier, snapshots, plugins, path));
            }
            requireNoConflicts(pending);
            for (PreparedUpdateSet set : pending) {
                install(set, transactions, verifier, restorer);
            }
            return pending.size();
        }
    }

    private static void requireNoConflicts(List<PreparedUpdateSet> pending) {
        Set<Path> targets = new HashSet<>();
        int total = 0;
        for (PreparedUpdateSet set : pending) {
            for (TransactionArtifact artifact : set.artifacts) {
                targets.add(artifact.getTarget());
                total++;
            }
        }
        if (targets.size() != total) {
            throw new IllegalArgumentException("Conflicting prepared component sets; resolve them before starting the server");
        }
    }

    /** Publishes one prepared set; on failure the configurations and JARs it touched are put back. */
    private static void install(PreparedUpdateSet prepared, UpdateTransaction transactions, ArtifactVerifier verifier,
                                ConfigurationRestorer restorer) throws IOException {
        Path child = transactions.appliedChildJournal(prepared.id, prepared.purpose);
        if (child == null) {
            child = transactions.prepareForRestart(prepared.artifacts, null, null, null, prepared.purpose, prepared.id)
                    .getJournal();
            // This child commits disk publication, not server/plugin health.
            transactions.completeHealth(child, true);
        }
        UUID[] restoredConfigurations = new UUID[1];
        try {
            if (prepared.configurationSnapshot != null) {
                restoredConfigurations[0] = restorer.restore(prepared.configurationSnapshot, prepared.id);
            }
            consumePendingFiles(prepared, verifier);
            transactions.markInstalled(prepared.journal, prepared.purpose);
        } catch (Throwable error) {
            undo(error, restoredConfigurations[0], child, transactions, restorer);
            throw error;
        }
    }

    private static void consumePendingFiles(PreparedUpdateSet prepared, ArtifactVerifier verifier) throws IOException {
        for (int index = 0; index < prepared.pendingFiles.size(); index++) {
            Path file = prepared.pendingFiles.get(index);
            if (!Files.exists(file)) {
                continue;
            }
            ArtifactSpecification expected = prepared.artifacts.get(index).getSpecification();
            verifier.verify(file, expected.withFileName(file.getFileName().toString()));
            Files.delete(file);
        }
    }

    private static void undo(Throwable error, UUID restoredConfigurations, Path child, UpdateTransaction transactions,
                             ConfigurationRestorer restorer) {
        if (restoredConfigurations != null) {
            Failures.runCatchingNonFatal(() -> {
                restorer.undo(restoredConfigurations);
                return null;
            }).onFailure(error::addSuppressed);
        }
        Failures.runCatchingNonFatal(() -> {
            transactions.revertInstallation(child);
            return null;
        }).onFailure(error::addSuppressed);
    }
}
