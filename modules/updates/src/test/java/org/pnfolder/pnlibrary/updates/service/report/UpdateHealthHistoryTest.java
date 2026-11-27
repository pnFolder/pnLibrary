package org.pnfolder.pnlibrary.updates.service.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pnfolder.pnlibrary.api.platform.PlatformType;
import org.pnfolder.pnlibrary.api.updates.product.ProductId;
import org.pnfolder.pnlibrary.api.updates.state.UpdatePlanSnapshot;
import org.pnfolder.pnlibrary.api.updates.state.UpdateState;
import org.pnfolder.pnlibrary.api.version.ApiVersionRange;
import org.pnfolder.pnlibrary.api.version.SemanticVersion;
import org.pnfolder.pnlibrary.spi.platform.PlatformIdentity;
import org.pnfolder.pnlibrary.updates.databases.DatabaseBackups;
import org.pnfolder.pnlibrary.updates.databases.DatabaseSources;
import org.pnfolder.pnlibrary.updates.engine.net.ArtifactSpecification;
import org.pnfolder.pnlibrary.updates.engine.net.ArtifactVerifier;
import org.pnfolder.pnlibrary.updates.engine.net.TrustedHttpClient;
import org.pnfolder.pnlibrary.updates.engine.transaction.TransactionArtifact;
import org.pnfolder.pnlibrary.updates.engine.transaction.journal.TransactionJournal;
import org.pnfolder.pnlibrary.updates.engine.transaction.TransactionResult;
import org.pnfolder.pnlibrary.updates.engine.transaction.TransactionState;
import org.pnfolder.pnlibrary.updates.engine.transaction.UpdateTransaction;
import org.pnfolder.pnlibrary.updates.history.events.UpdateEvent;
import org.pnfolder.pnlibrary.updates.history.events.UpdateEventChange;
import org.pnfolder.pnlibrary.updates.history.events.UpdateEventJournal;
import org.pnfolder.pnlibrary.updates.history.state.UpdateStateStore;
import org.pnfolder.pnlibrary.updates.install.UpdateInstaller;
import org.pnfolder.pnlibrary.updates.paths.LibraryPaths;
import org.pnfolder.pnlibrary.updates.policy.UpdateConfiguration;

class UpdateHealthHistoryTest {
    @TempDir
    Path directory;

    private static Map<String, String> versions(String product, String version) {
        Map<String, String> versions = new HashMap<>();
        versions.put(product, version);
        return versions;
    }

    @Test
    @DisplayName("health history uses the original transaction rather than the latest resolver snapshot")
    void healthHistoryUsesTheOriginalTransactionRatherThanTheLatestResolverSnapshot() throws Exception {
        Fixture fixture = fixture(true);
        UUID unrelated = UUID.randomUUID();
        UpdateStateStore state = new UpdateStateStore(new LibraryPaths(fixture.data).getUpdates());
        state.save(new UpdatePlanSnapshot(unrelated, 99, UpdateState.UPDATE_AVAILABLE, null, Collections.emptyList(),
            "newer check"));
        UpdateEventJournal events = new UpdateEventJournal(new LibraryPaths(fixture.data).getEvents());
        fixture.installer.reconcileVersions(versions("pnlibrary", "2.0.0"), report -> events.recordHealth(report));
        List<UpdateEvent> recorded = events.read();
        assertEquals(1, recorded.size());
        UpdateEvent event = recorded.get(0);
        assertEquals(fixture.plan, event.getPlanId());
        assertEquals(Collections.singletonList(new UpdateEventChange("pnlibrary", "1.0.0", "2.0.0")), event.getChanges());
        assertEquals("HEALTH", event.getOperation());
        assertEquals(unrelated, state.current().getId());
        assertEquals(TransactionState.COMMITTED, TransactionJournal.load(fixture.journal).getState());
    }

    @Test
    @DisplayName("rollback is confirmed once with the actual restored version transition")
    void rollbackIsConfirmedOnceWithTheActualRestoredVersionTransition() throws Exception {
        Fixture fixture = fixture(true);
        fixture.installer.reconcileVersions(versions("pnlibrary", "2.0.0"), report -> { });
        fixture.transactions.rollback(fixture.journal);
        TransactionJournal rollback = TransactionJournal.load(fixture.journal);
        rollback.setRollbackSession(UUID.randomUUID().toString());
        TransactionJournal.save(fixture.journal, rollback);
        List<UpdateHealthReport> reports = new ArrayList<>();
        fixture.installer.reconcileVersions(versions("pnlibrary", "1.0.0"), reports::add);
        assertEquals(1, reports.size());
        assertTrue(reports.get(0).getHealthy());
        assertTrue(reports.get(0).getRollback());
        assertEquals("2.0.0", reports.get(0).getChanges().get(0).getFrom().toString());
        assertEquals("1.0.0", reports.get(0).getChanges().get(0).getTo().toString());
        fixture.installer.reconcileVersions(versions("pnlibrary", "1.0.0"), reports::add);
        assertEquals(1, reports.size());
        assertTrue(TransactionJournal.load(fixture.journal).getRollbackHealthVerified());
    }

    @Test
    @DisplayName("failed verification freezes automatic updates until matching versions are observed")
    void failedVerificationFreezesAutomaticUpdatesUntilMatchingVersionsAreObserved() throws Exception {
        Fixture fixture = fixture(true);
        List<UpdateHealthReport> reports = new ArrayList<>();
        fixture.installer.reconcileVersions(versions("pnlibrary", "1.0.0"), reports::add);
        assertEquals(1, reports.size());
        assertFalse(reports.get(0).getHealthy());
        assertFalse(fixture.installer.automaticUpdatesHealthy(Collections.singleton(ProductId.of("pnlibrary"))));
        fixture.installer.reconcileVersions(versions("pnlibrary", "2.0.0"), reports::add, true);
        assertTrue(reports.get(reports.size() - 1).getHealthy());
        assertTrue(fixture.installer.automaticUpdatesHealthy(Collections.singleton(ProductId.of("pnlibrary"))));
    }

    @Test
    @DisplayName("a preparation in this process is not mistaken for a successful restart")
    void aPreparationInThisProcessIsNotMistakenForASuccessfulRestart() throws Exception {
        Fixture fixture = fixture(false);
        List<UpdateHealthReport> reports = new ArrayList<>();
        fixture.installer.reconcileVersions(versions("pnlibrary", "2.0.0"), reports::add);
        assertTrue(reports.isEmpty());
        assertEquals(TransactionState.AWAITING_HEALTH, TransactionJournal.load(fixture.journal).getState());
    }

    @Test
    @DisplayName("failed audit persistence does not silently acknowledge a verified installation")
    void failedAuditPersistenceDoesNotSilentlyAcknowledgeAVerifiedInstallation() throws Exception {
        Fixture fixture = fixture(true);
        assertThrows(IllegalStateException.class, () -> fixture.installer.reconcileVersions(
            versions("pnlibrary", "2.0.0"), report -> {
                throw new IllegalStateException("audit disk unavailable");
            }));
        assertEquals(TransactionState.AWAITING_HEALTH, TransactionJournal.load(fixture.journal).getState());
        assertFalse(fixture.installer.automaticUpdatesHealthy(Collections.singleton(ProductId.of("pnlibrary"))));
        fixture.installer.reconcileVersions(versions("pnlibrary", "2.0.0"), report -> { });
        assertEquals(TransactionState.COMMITTED, TransactionJournal.load(fixture.journal).getState());
    }

    private static final class Fixture {
        private final Path data;
        private final UUID plan;
        private final Path journal;
        private final UpdateTransaction transactions;
        private final UpdateInstaller installer;

        Fixture(Path data, UUID plan, Path journal, UpdateTransaction transactions, UpdateInstaller installer) {
            this.data = data;
            this.plan = plan;
            this.journal = journal;
            this.transactions = transactions;
            this.installer = installer;
        }
    }

    private Fixture fixture(boolean previousProcess) throws IOException {
        Path plugins = directory.resolve("plugins");
        Path data = plugins.resolve("pnLibrary");
        Files.createDirectories(data);
        Path installed = plugins.resolve("library.jar");
        Files.write(installed, new byte[] {1});
        Path source = directory.resolve("release.jar");
        Files.write(source, new byte[] {2});
        ApiVersionRange api = new ApiVersionRange(8, 8);
        ArtifactSpecification specification = new ArtifactSpecification(ProductId.of("pnlibrary"),
            SemanticVersion.parse("2.0.0"), api, "release.jar");
        UpdateTransaction transactions = new UpdateTransaction(new LibraryPaths(data).getTransactions(),
            new ArtifactVerifier(1024), plugins);
        UUID plan = UUID.randomUUID();
        TransactionResult result = transactions.prepareForRestart(Collections.singletonList(new TransactionArtifact(
            specification, source, plugins.resolve("update/library.jar"), installed, "1.0.0", api, installed)), plan, 8, null);
        if (previousProcess) {
            TransactionJournal.save(result.getJournal(),
                TransactionJournal.load(result.getJournal()).withPreparedSession(UUID.randomUUID().toString()));
        }
        PlatformIdentity platform = new PlatformIdentity() {
            @Override
            public PlatformType getType() {
                return PlatformType.BUKKIT;
            }
        };
        TrustedHttpClient http = new TrustedHttpClient(Duration.ofSeconds(1), Duration.ofSeconds(1)) {
            @Override
            public byte[] get(URI uri, int maximumBytes) {
                throw new IllegalStateException("health verification must not access the network");
            }
        };
        DatabaseBackups databases = new DatabaseBackups(new LibraryPaths(data).getDatabases(), new DatabaseSources());
        return new Fixture(data, plan, result.getJournal(), transactions,
            new UpdateInstaller(platform, new LibraryPaths(data), new UpdateConfiguration(), http, databases));
    }
}
