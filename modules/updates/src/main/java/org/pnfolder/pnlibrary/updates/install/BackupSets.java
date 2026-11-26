package org.pnfolder.pnlibrary.updates.install;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import org.pnfolder.pnlibrary.api.updates.backup.UpdateBackup;
import org.pnfolder.pnlibrary.api.updates.backup.UpdateBackupComponent;
import org.pnfolder.pnlibrary.api.updates.backup.UpdateBackupItem;
import org.pnfolder.pnlibrary.api.updates.product.ProductId;
import org.pnfolder.pnlibrary.common.lang.Attempt;
import org.pnfolder.pnlibrary.common.lang.Failures;
import org.pnfolder.pnlibrary.updates.databases.DatabaseBackups;
import org.pnfolder.pnlibrary.updates.engine.transaction.JournalArtifact;
import org.pnfolder.pnlibrary.updates.engine.transaction.PendingTransaction;
import org.pnfolder.pnlibrary.updates.engine.transaction.TransactionJournal;
import org.pnfolder.pnlibrary.updates.engine.transaction.UpdateTransaction;
import org.pnfolder.pnlibrary.updates.history.BackupItemText;
import org.pnfolder.pnlibrary.updates.history.BackupStorage;
import org.pnfolder.pnlibrary.updates.history.ConfigurationSnapshotFile;
import org.pnfolder.pnlibrary.updates.history.ConfigurationSnapshots;
import org.pnfolder.pnlibrary.updates.paths.LibraryPaths;

/** The saved rollback sets: listing, retention, removal and handing their data back to the administrator. */
final class BackupSets {
    /** Characters of the set id used in a handout folder name. */
    private static final int HANDOUT_ID = 8;

    private final UpdateTransaction transaction;
    private final ConfigurationSnapshots configurations;
    private final DatabaseBackups databases;
    private final LibraryPaths paths;
    private final Consumer<String> warn;

    BackupSets(UpdateTransaction transaction, ConfigurationSnapshots configurations, DatabaseBackups databases,
               LibraryPaths paths, Consumer<String> warn) {
        this.transaction = transaction;
        this.configurations = configurations;
        this.databases = databases;
        this.paths = paths;
        this.warn = warn;
    }

    /** Removes saved sets older than the retention period, except those a restart still has to verify. */
    int prune(Instant now) throws IOException {
        Set<String> busy = busy();
        List<Path> old = new ArrayList<>();
        for (Path path : transaction.rollbackCandidates()) {
            TransactionJournal journal = TransactionJournal.load(path);
            Instant created = journal.getCreatedAt() == null ? null : Instant.parse(journal.getCreatedAt());
            if (!busy.contains(journal.getId()) && BackupStorage.expired(created, now)) {
                old.add(path);
            }
        }
        for (Path path : old) {
            remove(path);
        }
        warnIfLarge();
        return old.size();
    }

    /** Deletes one saved set with its configuration and data copies; a set that a restart still verifies is refused. */
    void delete(UUID id) throws IOException {
        Path path = transaction.rollbackCandidate(id);
        if (busy().contains(id.toString())) {
            throw new IllegalArgumentException("Набор " + id + " ещё проверяется после перезапуска");
        }
        remove(path);
    }

    /** Puts a saved database of {@code product} back for the set {@code id}; what it replaces is kept first. */
    void restoreDatabase(UUID id, String product, String name) throws Exception {
        TransactionJournal journal = TransactionJournal.load(transaction.rollbackCandidate(id));
        databases.restore(UUID.fromString(plan(journal)), product, name);
    }

    /** Copies the saved databases and exports of {@code product} from the set {@code id} into a new folder and returns it. */
    Path extract(UUID id, String product) throws IOException {
        TransactionJournal journal = TransactionJournal.load(transaction.rollbackCandidate(id));
        String plan = plan(journal);
        Path target = paths.getHandout().resolve(id.toString().substring(0, HANDOUT_ID) + "-" + product);
        List<Path> sources = new ArrayList<>();
        for (Path folder : Arrays.asList(paths.getDatabases(), paths.getExports())) {
            Path child = BackupStorage.child(folder.resolve(plan), product);
            if (child != null) {
                sources.add(child);
            }
        }
        if (sources.isEmpty()) {
            throw new IllegalArgumentException("В наборе " + id + " нет сохранённых баз и выгрузок плагина " + product);
        }
        for (Path source : sources) {
            BackupStorage.copy(source, target);
        }
        return target;
    }

    /** The saved rollback sets with the configurations and databases kept for each. */
    List<UpdateBackup> list() throws IOException {
        List<UpdateBackup> result = new ArrayList<>();
        for (Path path : transaction.rollbackCandidates()) {
            result.add(describe(TransactionJournal.load(path)));
        }
        return result;
    }

    /** Warns once the saved sets together take a lot of disk space. */
    void warnIfLarge() throws IOException {
        long total = 0;
        for (Path folder : Arrays.asList(paths.getConfigurations(), paths.getTransactions(), paths.getExports(), paths.getDatabases())) {
            total += BackupStorage.bytes(folder);
        }
        if (total < BackupStorage.LARGE_BYTES) {
            return;
        }
        warn.accept("Резервные копии занимают " + BackupItemText.size(total) + ". Старше "
                + BackupStorage.RETENTION.toDays() + " дней удаляются сами; "
                + "копии плагинов лежат в data/updates/exports; ненужные можно удалить командой /pn updates backups delete <id>.");
    }

    private UpdateBackup describe(TransactionJournal journal) throws IOException {
        List<ConfigurationSnapshotFile> files = savedFiles(journal);
        List<UpdateBackupComponent> components = new ArrayList<>();
        for (JournalArtifact artifact : journal.getArtifacts()) {
            components.add(new UpdateBackupComponent(ProductId.of(artifact.getComponent()),
                    artifact.getExpectedVersion(), artifact.getPreviousVersion()));
        }
        List<UpdateBackupItem> items = new ArrayList<>();
        for (ConfigurationSnapshotFile file : files) {
            items.add(new UpdateBackupItem(ProductId.of(file.getProduct()), file.getBackupKind(),
                    file.getName() == null ? "" : file.getName(), file.getSize()));
        }
        if (journal.getPlanId() != null) {
            items.addAll(databases.items(UUID.fromString(journal.getPlanId())));
        }
        return new UpdateBackup(UUID.fromString(journal.getId()),
                journal.getCreatedAt() == null ? null : Instant.parse(journal.getCreatedAt()),
                journal.getPlanId() == null ? null : UUID.fromString(journal.getPlanId()),
                components, files.size(), items);
    }

    private List<ConfigurationSnapshotFile> savedFiles(TransactionJournal journal) {
        String snapshot = journal.getConfigurationSnapshot();
        if (snapshot == null) {
            return new ArrayList<>();
        }
        Attempt<List<ConfigurationSnapshotFile>> read = Failures.runCatchingNonFatal(() -> {
            List<ConfigurationSnapshotFile> existing = new ArrayList<>();
            for (ConfigurationSnapshotFile file : configurations.read(UUID.fromString(snapshot)).getFiles()) {
                if (file.getExisted()) {
                    existing.add(file);
                }
            }
            return existing;
        });
        read.onFailure(error -> warn.accept("Копия настроек " + snapshot + " набора " + journal.getId() + " нечитаема: "
                + (error.getMessage() != null ? error.getMessage() : error.getClass().getSimpleName())));
        List<ConfigurationSnapshotFile> files = read.getOrNull();
        return files == null ? new ArrayList<ConfigurationSnapshotFile>() : files;
    }

    private Set<String> busy() throws IOException {
        Set<String> ids = new HashSet<>();
        List<PendingTransaction> awaiting = new ArrayList<>(transaction.awaitingHealth());
        awaiting.addAll(transaction.awaitingRollbackHealth());
        for (PendingTransaction pending : awaiting) {
            ids.add(transaction.readJournal(pending.getJournal()).getId());
        }
        for (Path path : transaction.pendingInstallations()) {
            ids.add(transaction.readJournal(path).getId());
        }
        return ids;
    }

    private void remove(Path journalPath) throws IOException {
        TransactionJournal journal = TransactionJournal.load(journalPath);
        if (journal.getConfigurationSnapshot() != null) {
            BackupStorage.delete(paths.getConfigurations().resolve(journal.getConfigurationSnapshot()));
        }
        if (journal.getPlanId() != null) {
            BackupStorage.delete(paths.getExports().resolve(journal.getPlanId()));
            BackupStorage.delete(paths.getDatabases().resolve(journal.getPlanId()));
        }
        BackupStorage.delete(journalPath.getParent());
    }

    private static String plan(TransactionJournal journal) {
        if (journal.getPlanId() == null) {
            throw new IllegalArgumentException("У набора нет плана");
        }
        return journal.getPlanId();
    }
}
