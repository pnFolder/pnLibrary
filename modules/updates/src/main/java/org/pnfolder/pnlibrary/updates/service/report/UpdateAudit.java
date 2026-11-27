package org.pnfolder.pnlibrary.updates.service.report;

import org.pnfolder.pnlibrary.updates.service.registration.RegisteredUpdate;
import java.io.IOException;
import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import org.pnfolder.pnlibrary.api.logging.LogLevel;
import org.pnfolder.pnlibrary.api.updates.release.ProductChange;
import org.pnfolder.pnlibrary.api.updates.state.UpdateActor;
import org.pnfolder.pnlibrary.api.updates.state.UpdatePlanSnapshot;
import org.pnfolder.pnlibrary.common.lang.Failures;
import org.pnfolder.pnlibrary.spi.platform.logging.PlatformLogging;
import org.pnfolder.pnlibrary.updates.history.events.UpdateEventJournal;
import org.pnfolder.pnlibrary.updates.install.ledger.ComponentLedger;
import org.pnfolder.pnlibrary.updates.install.ledger.ComponentOperation;
import org.pnfolder.pnlibrary.updates.paths.LibraryPaths;

/** What the update service writes to the durable history: plan states, restart verifications and component changes. */
public final class UpdateAudit {
    private final LibraryPaths paths;
    private final PlatformLogging logging;
    private final Object owner;
    private final BooleanSupplier closed;
    private final Set<String> verifiedByHealth = ConcurrentHashMap.newKeySet();
    private UpdateEventJournal journal;
    private ComponentLedger ledger;

    public UpdateAudit(LibraryPaths paths, PlatformLogging logging, Object owner, BooleanSupplier closed) {
        this.paths = paths;
        this.logging = logging;
        this.owner = owner;
        this.closed = closed;
    }

    // Created on first use: the folder is not touched until there is something to write.
    private synchronized UpdateEventJournal journal() throws IOException {
        if (journal == null) {
            journal = new UpdateEventJournal(paths.getEvents(), Clock.systemUTC(),
                    message -> logging.log(owner, LogLevel.WARNING, message));
        }
        return journal;
    }

    private synchronized ComponentLedger ledger() throws IOException {
        if (ledger == null) {
            ledger = new ComponentLedger(paths.getComponents(), journal(),
                    message -> logging.log(owner, LogLevel.WARNING, message));
        }
        return ledger;
    }

    /** Records an observed plan state; a failure is logged and does not fail an already staged transaction. */
    public void recordState(UpdatePlanSnapshot snapshot, UpdateActor actor, String operation) {
        Failures.runCatchingNonFatal(() -> journal().record(snapshot, actor, operation)).onFailure(error -> {
            // An observation failure must not turn an already staged transaction into a failed operation.
            if (!closed.getAsBoolean()) {
                Failures.runCatchingNonFatal(() -> {
                    logging.log(owner, LogLevel.ERROR, "Could not persist update observation", error);
                    return null;
                });
            }
        });
    }

    /** Records what a restart loaded; a failure stops the verification so it is tried again. */
    public void recordHealth(UpdateHealthReport report) throws IOException {
        if (report.getHealthy()) {
            for (ProductChange change : report.getChanges()) {
                verifiedByHealth.add(change.getProduct().getValue());
            }
        }
        journal().recordHealth(report);
        if (!closed.getAsBoolean()) {
            Failures.runCatchingNonFatal(() -> {
                logging.log(owner, report.getHealthy() ? LogLevel.INFO : LogLevel.WARNING, report.getMessage());
                return null;
            });
        }
    }

    /** Records a change an administrator made to one component's settings and remembers it for the next start. */
    public void recordSettingsChange(RegisteredUpdate registration, String message, UpdateActor actor) {
        Failures.runCatchingNonFatal(() -> {
            journal().recordComponent(ComponentOperation.SETTINGS_CHANGED, registration.getProduct(),
                    registration.getVersion(), registration.getVersion(), message, actor);
            ledger().updateSettings(registration.getProduct(), registration.getSnapshot().getChannel().name().toLowerCase(Locale.ROOT),
                    registration.getSnapshot().getAutomaticDownload());
            return null;
        }).onFailure(error -> logFailure("Could not record a settings change", error));
    }

    /** Compares the components registered now with those of the last start and records the difference. */
    public void observeComponents(List<RegisteredUpdate> installed) {
        Failures.runCatchingNonFatal(() -> {
            Map<String, ComponentLedger.Component> current = new TreeMap<>();
            for (RegisteredUpdate registration : installed) {
                current.put(registration.getProduct(), new ComponentLedger.Component(registration.getVersion(),
                        registration.getSnapshot().getChannel().name().toLowerCase(Locale.ROOT),
                        registration.getSnapshot().getAutomaticDownload()));
            }
            ledger().observe(current, new HashSet<>(verifiedByHealth));
            return null;
        }).onFailure(error -> logFailure("Could not record component changes", error));
    }

    /** The journal to read history pages from. */
    public UpdateEventJournal events() throws IOException {
        return journal();
    }

    private void logFailure(String message, Throwable error) {
        if (!closed.getAsBoolean()) {
            logging.log(owner, LogLevel.ERROR, message, error);
        }
    }
}
