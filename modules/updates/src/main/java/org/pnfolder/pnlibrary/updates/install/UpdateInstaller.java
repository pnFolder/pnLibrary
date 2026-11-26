package org.pnfolder.pnlibrary.updates.install;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.runtime.PnLibraryIdentity;
import org.pnfolder.pnlibrary.api.updates.backup.BackupExport;
import org.pnfolder.pnlibrary.api.updates.backup.UpdateBackup;
import org.pnfolder.pnlibrary.api.updates.backup.UpdateConfigurationBackup;
import org.pnfolder.pnlibrary.api.updates.product.ProductId;
import org.pnfolder.pnlibrary.api.updates.release.ProductChange;
import org.pnfolder.pnlibrary.api.updates.release.UpdatePlan;
import org.pnfolder.pnlibrary.api.updates.state.UpdatePlanSnapshot;
import org.pnfolder.pnlibrary.api.updates.state.UpdateState;
import org.pnfolder.pnlibrary.api.version.SemanticVersion;
import org.pnfolder.pnlibrary.spi.platform.PlatformIdentity;
import org.pnfolder.pnlibrary.updates.databases.DatabaseBackups;
import org.pnfolder.pnlibrary.updates.engine.net.ArtifactVerifier;
import org.pnfolder.pnlibrary.updates.engine.net.TrustedHttpClient;
import org.pnfolder.pnlibrary.updates.engine.transaction.JournalArtifact;
import org.pnfolder.pnlibrary.updates.engine.transaction.TransactionJournal;
import org.pnfolder.pnlibrary.updates.engine.transaction.UpdateTransaction;
import org.pnfolder.pnlibrary.updates.history.BackupStorage;
import org.pnfolder.pnlibrary.updates.history.ConfigurationSnapshots;
import org.pnfolder.pnlibrary.updates.history.PrivateFiles;
import org.pnfolder.pnlibrary.updates.paths.LibraryPaths;
import org.pnfolder.pnlibrary.updates.policy.UpdateConfiguration;
import org.pnfolder.pnlibrary.updates.service.RegisteredUpdate;

/**
 * Stages update files together with their safety copies, and verifies a staged transaction after the next server
 * start. Also lists, prunes and applies the saved rollback sets.
 */
public final class UpdateInstaller {
    private final LibraryPaths paths;
    private final DatabaseBackups databases;
    private final ConfigurationSnapshots configurations;
    private final UpdateConfigurationBackup libraryConfiguration;
    private final UpdateTransaction transaction;
    private final UpdatePlanStager stager;
    private final BackupSets backupSets;
    private final UpdateHealthVerifier health;

    /** Creates an installer without Java build preferences that drops warnings. */
    public UpdateInstaller(@NotNull PlatformIdentity platform, @NotNull LibraryPaths paths,
                           @NotNull UpdateConfiguration configuration, @NotNull TrustedHttpClient http,
                           @NotNull DatabaseBackups databases) {
        this(platform, paths, configuration, http, databases, product -> null, product -> null, message -> { });
    }

    /**
     * Creates an installer.
     *
     * @param javaChoice the Java build the administrator chose for a product, if any
     * @param installedJava the Java version the installed build of a product was made for, if known
     * @param warn receives problems that do not stop the update
     */
    public UpdateInstaller(@NotNull PlatformIdentity platform, @NotNull LibraryPaths paths,
                           @NotNull UpdateConfiguration configuration, @NotNull TrustedHttpClient http,
                           @NotNull DatabaseBackups databases, @NotNull Function<ProductId, Integer> javaChoice,
                           @NotNull Function<ProductId, Integer> installedJava, @NotNull Consumer<String> warn) {
        this.paths = paths;
        this.databases = databases;
        this.configurations = new ConfigurationSnapshots(paths.getConfigurations(), paths.getPlugins());
        this.libraryConfiguration = new UpdateConfigurationBackup(paths.getPluginFolder(),
                Collections.singletonList(paths.getPluginFolder().relativize(paths.getSettings()).toString()
                        .replace(File.separatorChar, '/')));
        this.transaction = new UpdateTransaction(paths.getTransactions(),
                new ArtifactVerifier(configuration.getDownloads().getMaxArtifactBytes()), paths.getPlugins(), warn);
        this.stager = new UpdatePlanStager(platform.getType(), paths, http, transaction,
                configuration.getDownloads().getMaxArtifactBytes(), configuration.getInstallation().getRequireSha256(),
                javaChoice, installedJava);
        this.backupSets = new BackupSets(transaction, configurations, databases, paths, warn);
        this.health = new UpdateHealthVerifier(transaction);
    }

    /** Finishes or rolls back transactions a crash interrupted, then drops sets past their retention. */
    public void recoverInterruptedTransactions() throws IOException {
        transaction.recoverAll();
        pruneBackups();
    }

    /** Removes saved sets older than the retention period, except those a restart still has to verify. */
    public int pruneBackups() throws IOException {
        return pruneBackups(Instant.now());
    }

    /** Removes saved sets older than the retention period as of {@code now}. Returns how many went. */
    public int pruneBackups(@NotNull Instant now) throws IOException {
        return backupSets.prune(now);
    }

    /** Deletes one saved set with its configuration and data copies; a set that a restart still verifies is refused. */
    public void deleteBackup(@NotNull UUID id) throws IOException {
        backupSets.delete(id);
    }

    /** Puts a saved database of {@code product} back for the set {@code id}; what it replaces is kept first. */
    public void restoreDatabase(@NotNull UUID id, @NotNull String product, @NotNull String name) throws Exception {
        backupSets.restoreDatabase(id, product, name);
    }

    /** Copies the saved databases and exports of {@code product} from the set {@code id} into a new folder and returns it. */
    @NotNull
    public Path extractBackup(@NotNull UUID id, @NotNull String product) throws IOException {
        return backupSets.extract(id, product);
    }

    /** The saved rollback sets with the configurations and databases kept for each. */
    @NotNull
    public List<UpdateBackup> backups() throws IOException {
        return backupSets.list();
    }

    /** Saves exports, databases and configurations of the changed plugins, then downloads and prepares the plan for restart. */
    public void stage(@NotNull UpdatePlanSnapshot snapshot, @NotNull List<RegisteredUpdate> registrations) throws IOException {
        requireNothingAwaiting("A prepared rollback must be applied before preparing another update");
        UpdatePlan plan = snapshot.getPlan();
        if (plan == null) {
            throw new IllegalArgumentException("Update plan has no installable target");
        }
        Map<ProductId, InstalledUpdateTarget> targets = new HashMap<>();
        for (RegisteredUpdate registration : registrations) {
            targets.put(registration.getDescriptor().getId(), new InstalledUpdateTarget(registration.getJar(),
                    registration.getUpdateDirectory(), registration.getDescriptor().getSupportedApi()));
        }
        Set<ProductId> changedIds = new HashSet<>();
        for (ProductChange change : plan.getChanges()) {
            changedIds.add(change.getProduct());
        }
        List<RegisteredUpdate> changed = new ArrayList<>();
        Map<ProductId, UpdateConfigurationBackup> selections = new HashMap<>();
        List<String> changedNames = new ArrayList<>();
        for (RegisteredUpdate registration : registrations) {
            if (!changedIds.contains(registration.getDescriptor().getId())) {
                continue;
            }
            changed.add(registration);
            changedNames.add(registration.getProduct());
            UpdateConfigurationBackup selection = configurationOf(registration);
            if (selection != null) {
                selections.put(registration.getDescriptor().getId(), selection);
            }
        }
        runExports(snapshot.getId(), changed);
        databases.copyBefore(snapshot.getId(), changedNames);
        UUID configurationSnapshot = configurations.capture(snapshot.getId(), selections);
        stager.stage(snapshot.getId(), plan, targets, configurationSnapshot);
        backupSets.warnIfLarge();
    }

    private UpdateConfigurationBackup configurationOf(RegisteredUpdate registration) {
        if (registration.getRequest().getConfigurationBackup() != null) {
            return registration.getRequest().getConfigurationBackup();
        }
        return registration.getProduct().equals(PnLibraryIdentity.PRODUCT_ID) ? libraryConfiguration : null;
    }

    /** Lets each plugin write its own copies; a plugin that fails stops the update, since the copy would be missing. */
    private void runExports(UUID planId, List<RegisteredUpdate> changed) throws IOException {
        for (RegisteredUpdate registration : changed) {
            if (registration.getRequest().getExports().isEmpty()) {
                continue;
            }
            Path folder = paths.getExports().resolve(planId.toString()).resolve(registration.getProduct());
            PrivateFiles.directories(folder);
            for (BackupExport export : registration.getRequest().getExports()) {
                try {
                    export.getExport().write(folder.resolve(export.getFileName()));
                } catch (Exception error) {
                    BackupStorage.delete(paths.getExports().resolve(planId.toString()));
                    throw new IllegalStateException("Плагин " + registration.getProduct() + " не смог сохранить копию «"
                            + export.getName() + "»: " + error.getMessage(), error);
                }
            }
        }
    }

    /** Prepares a rollback to the newest saved set. */
    @NotNull
    public UpdatePlanSnapshot rollbackLatest() throws IOException {
        Path candidate = transaction.latestRollbackCandidate();
        if (candidate == null) {
            throw new IllegalStateException("Нет сохранённого набора JAR для отката");
        }
        return rollback(UUID.fromString(TransactionJournal.load(candidate).getId()));
    }

    /** Prepares a rollback to set {@code id}; configurations stay as they are. */
    @NotNull
    public UpdatePlanSnapshot rollback(@NotNull UUID id) throws IOException {
        return rollback(id, false);
    }

    /** Prepares a rollback to set {@code id} for the next restart, putting configurations back too when asked. */
    @NotNull
    public UpdatePlanSnapshot rollback(@NotNull UUID id, boolean restoreConfigurations) throws IOException {
        requireNothingAwaiting("Another rollback is already prepared for restart");
        Path candidate = transaction.rollbackCandidate(id);
        TransactionJournal journal = TransactionJournal.load(candidate);
        if (restoreConfigurations) {
            if (journal.getConfigurationSnapshot() == null) {
                throw new IllegalArgumentException("У этого набора нет снимка конфигураций");
            }
            String planOfSnapshot = configurations.read(UUID.fromString(journal.getConfigurationSnapshot())).getPlanId();
            if (!Objects.equals(planOfSnapshot, journal.getPlanId())) {
                throw new IllegalArgumentException("Configuration snapshot belongs to another plan");
            }
        }
        // Legacy backups may lack version information. Never invent a version from the current plan.
        List<ProductChange> changes = new ArrayList<>();
        for (JournalArtifact artifact : journal.getArtifacts()) {
            SemanticVersion updated = artifact.getExpectedVersion() == null ? null : SemanticVersion.tryParse(artifact.getExpectedVersion());
            SemanticVersion previous = artifact.getPreviousVersion() == null ? null : SemanticVersion.tryParse(artifact.getPreviousVersion());
            if (previous != null && updated != null) {
                changes.add(new ProductChange(ProductId.of(artifact.getComponent()), previous, updated));
            }
        }
        UpdatePlanSnapshot snapshot = new UpdatePlanSnapshot(
                journal.getPlanId() == null ? id : UUID.fromString(journal.getPlanId()), 0, UpdateState.ROLLED_BACK,
                journal.getTargetApi() == null ? null
                        : new UpdatePlan(journal.getTargetApi(), changes, Collections.emptyList()),
                Collections.emptyList(),
                "Резервный набор " + id + " подготовлен к следующему перезапуску"
                        + (restoreConfigurations ? " с восстановлением конфигураций" : " без изменения конфигураций") + ".");
        transaction.rollback(candidate, restoreConfigurations);
        return snapshot;
    }

    private void requireNothingAwaiting(String pendingInstallation) throws IOException {
        if (!transaction.awaitingHealth().isEmpty() || !transaction.awaitingRollbackHealth().isEmpty()) {
            throw new IllegalArgumentException("A prepared change is still awaiting restart verification");
        }
        if (!transaction.pendingInstallations().isEmpty()) {
            throw new IllegalArgumentException(pendingInstallation);
        }
    }

    /** After a restart, compares the versions that loaded with those a prepared set expected and reports the outcome. */
    public void reconcilePending(@NotNull List<RegisteredUpdate> registrations,
                                 @NotNull HealthReporter reportHealth) throws IOException {
        reconcileVersions(loadedVersions(registrations), reportHealth);
    }

    /** Reports every set that waits for verification, oldest first, and closes it as healthy or failed. */
    public void reconcileVersions(@NotNull Map<String, String> installedVersions,
                                  @NotNull HealthReporter reportHealth) throws IOException {
        health.reconcile(installedVersions, reportHealth, false);
    }

    /** Like {@link #reconcileVersions(Map, HealthReporter)}, but only sets whose earlier failed check now matches. */
    public void reconcileVersions(@NotNull Map<String, String> installedVersions,
                                  @NotNull HealthReporter reportHealth,
                                  boolean recoveredFailuresOnly) throws IOException {
        health.reconcile(installedVersions, reportHealth, recoveredFailuresOnly);
    }

    /** Closes sets whose check failed earlier but whose expected versions are loaded now. */
    public void reconcileRecoveredFailures(@NotNull List<RegisteredUpdate> registrations,
                                           @NotNull HealthReporter reportHealth) throws IOException {
        health.reconcile(loadedVersions(registrations), reportHealth, true);
    }

    /** True when nothing waits for verification and none of {@code products} has a failed check, so automatic updates may go on. */
    public boolean automaticUpdatesHealthy(@NotNull Set<ProductId> products) throws IOException {
        return health.automaticUpdatesHealthy(products);
    }

    /** True when a prepared set expects a product that has not registered yet, so verification must wait. */
    public boolean awaitingRegistrations(@NotNull List<RegisteredUpdate> registrations) throws IOException {
        return health.awaitingRegistrations(registrations);
    }

    private static Map<String, String> loadedVersions(List<RegisteredUpdate> registrations) {
        Map<String, String> versions = new HashMap<>();
        for (RegisteredUpdate registration : registrations) {
            versions.put(registration.getProduct(), registration.getVersion());
        }
        return versions;
    }
}
