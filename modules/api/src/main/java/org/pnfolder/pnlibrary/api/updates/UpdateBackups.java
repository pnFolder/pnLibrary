package org.pnfolder.pnlibrary.api.updates;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.updates.backup.UpdateBackup;
import org.pnfolder.pnlibrary.api.updates.state.UpdateActor;
import org.pnfolder.pnlibrary.api.updates.state.UpdatePlanSnapshot;

/** The rollback sets kept before each update, and going back to them. */
public interface UpdateBackups {
    /** The retained complete sets, newest first when their dates are known. */
    @NotNull
    CompletionStage<List<UpdateBackup>> list();

    /** Prepares the newest set that can be rolled back for the next restart. */
    @NotNull
    default CompletionStage<UpdatePlanSnapshot> rollback() {
        return rollback(UpdateActor.UNKNOWN);
    }

    /** Prepares the newest set that can be rolled back for the next restart, recorded for {@code actor}. */
    @NotNull
    CompletionStage<UpdatePlanSnapshot> rollback(@NotNull UpdateActor actor);

    /** Prepares exactly the set {@code transactionId} for the next restart and never substitutes another. */
    @NotNull
    default CompletionStage<UpdatePlanSnapshot> rollback(@NotNull UUID transactionId) {
        return rollback(transactionId, UpdateActor.UNKNOWN, false);
    }

    /** Prepares exactly the set {@code transactionId}, recorded for {@code actor}; saved settings stay as they are. */
    @NotNull
    default CompletionStage<UpdatePlanSnapshot> rollback(@NotNull UUID transactionId, @NotNull UpdateActor actor) {
        return rollback(transactionId, actor, false);
    }

    /**
     * Prepares exactly the set {@code transactionId} for the next restart and never substitutes another. With
     * {@code restoreConfigurations} the saved settings are restored as well, which is an explicit choice.
     */
    @NotNull
    CompletionStage<UpdatePlanSnapshot> rollback(@NotNull UUID transactionId, @NotNull UpdateActor actor,
                                                 boolean restoreConfigurations);

    /** Permanently deletes the set {@code transactionId} with its saved files; a set a restart still verifies is refused. */
    @NotNull
    default CompletionStage<Void> delete(@NotNull UUID transactionId) {
        return delete(transactionId, UpdateActor.UNKNOWN);
    }

    /** Permanently deletes the set {@code transactionId}, recorded for {@code actor}. */
    @NotNull
    CompletionStage<Void> delete(@NotNull UUID transactionId, @NotNull UpdateActor actor);

    /**
     * Puts the database {@code name} of {@code product} back as it was in the set {@code transactionId}: the tables in
     * the copy replace the current ones and rows written since are lost, but the state it replaces is kept first. Runs
     * at once.
     */
    @NotNull
    default CompletionStage<Void> restoreDatabase(@NotNull UUID transactionId, @NotNull String product,
                                                  @NotNull String name) {
        return restoreDatabase(transactionId, product, name, UpdateActor.UNKNOWN);
    }

    /** Restores a database as {@link #restoreDatabase(UUID, String, String)} does, recorded for {@code actor}. */
    @NotNull
    CompletionStage<Void> restoreDatabase(@NotNull UUID transactionId, @NotNull String product, @NotNull String name,
                                          @NotNull UpdateActor actor);

    /**
     * Copies the saved databases and exports of {@code product} from the set {@code transactionId} into a new folder
     * under {@code plugins/pnLibrary/data/updates/handout}, for a plugin that cannot take them back itself, and
     * returns its path.
     */
    @NotNull
    default CompletionStage<String> extract(@NotNull UUID transactionId, @NotNull String product) {
        return extract(transactionId, product, UpdateActor.UNKNOWN);
    }

    /** Extracts a set as {@link #extract(UUID, String)} does, recorded for {@code actor}. */
    @NotNull
    CompletionStage<String> extract(@NotNull UUID transactionId, @NotNull String product, @NotNull UpdateActor actor);
}
