package org.pnfolder.pnlibrary.updates.service;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.logging.LogLevel;
import org.pnfolder.pnlibrary.api.updates.UpdateBackups;
import org.pnfolder.pnlibrary.api.updates.backup.UpdateBackup;
import org.pnfolder.pnlibrary.api.updates.state.UpdateActor;
import org.pnfolder.pnlibrary.api.updates.state.UpdatePlanSnapshot;
import org.pnfolder.pnlibrary.spi.platform.PlatformLogging;
import org.pnfolder.pnlibrary.updates.install.UpdateInstaller;

/** The saved rollback sets: listing, rolling back to one, and getting its data back. */
final class BackupOperations implements UpdateBackups {
    private final Object owner;
    private final PlatformLogging logging;
    private final UpdateInstaller installer;
    private final UpdateOrchestratorRuntime runtime;

    BackupOperations(Object owner, PlatformLogging logging, UpdateInstaller installer, UpdateOrchestratorRuntime runtime) {
        this.owner = owner;
        this.logging = logging;
        this.installer = installer;
        this.runtime = runtime;
    }

    @NotNull
    @Override
    public CompletionStage<List<UpdateBackup>> list() {
        return runtime.readAsync(() -> Collections.unmodifiableList(installer.backups()));
    }

    @NotNull
    @Override
    public CompletionStage<UpdatePlanSnapshot> rollback(@NotNull UpdateActor actor) {
        return runtime.getOrchestrator().rollbackTo(actor, installer::rollbackLatest);
    }

    @NotNull
    @Override
    public CompletionStage<UpdatePlanSnapshot> rollback(@NotNull UUID transactionId, @NotNull UpdateActor actor,
                                                        boolean restoreConfigurations) {
        return runtime.getOrchestrator().rollbackTo(actor, transactionId,
                () -> installer.rollback(transactionId, restoreConfigurations));
    }

    @NotNull
    @Override
    public CompletionStage<Void> delete(@NotNull UUID transactionId, @NotNull UpdateActor actor) {
        return runtime.readAsync(() -> {
            installer.deleteBackup(transactionId);
            logging.log(owner, LogLevel.INFO, "Резервный набор " + transactionId + " удалён (" + actor.getName() + ")");
            return null;
        });
    }

    @NotNull
    @Override
    public CompletionStage<Void> restoreDatabase(@NotNull UUID transactionId, @NotNull String product,
                                                 @NotNull String name, @NotNull UpdateActor actor) {
        return runtime.readAsync(() -> {
            installer.restoreDatabase(transactionId, product, name);
            logging.log(owner, LogLevel.WARNING, "База «" + name + "» плагина " + product + " возвращена из набора "
                    + transactionId + " (" + actor.getName() + ")");
            return null;
        });
    }

    @NotNull
    @Override
    public CompletionStage<String> extract(@NotNull UUID transactionId, @NotNull String product, @NotNull UpdateActor actor) {
        return runtime.readAsync(() -> {
            String folder = installer.extractBackup(transactionId, product).toAbsolutePath().toString();
            logging.log(owner, LogLevel.INFO, "Файлы плагина " + product + " из набора " + transactionId + " выданы в "
                    + folder + " (" + actor.getName() + ")");
            return folder;
        });
    }
}
