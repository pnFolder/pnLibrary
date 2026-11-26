package org.pnfolder.pnlibrary.updates.engine.transaction;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** Tells which interrupted journals only the offline installer may repair. */
final class ServerStartGuard {
    static final Set<String> INTERNAL_PURPOSES = new HashSet<String>(Arrays.asList("INSTALL", "RESTORE"));
    static final Set<TransactionState> UNSAFE_STATES = new HashSet<TransactionState>(Arrays.asList(
        TransactionState.PUBLISHING, TransactionState.ACTIVATING, TransactionState.ROLLING_BACK));

    private ServerStartGuard() {
    }

    /** An internal installation that was published and never confirmed. */
    static boolean isInterruptedInstallation(TransactionJournal journal) {
        return INTERNAL_PURPOSES.contains(journal.getPurpose()) && journal.getState() == TransactionState.AWAITING_HEALTH;
    }

    /** Whether the server may already have loaded jars this journal was in the middle of replacing. */
    static boolean blocksServerStart(TransactionJournal journal, Path publicationRoot) {
        if (isInterruptedInstallation(journal)) {
            return true;
        }
        if (!UNSAFE_STATES.contains(journal.getState())) {
            return false;
        }
        if (INTERNAL_PURPOSES.contains(journal.getPurpose())) {
            return true;
        }
        for (JournalArtifact artifact : journal.getArtifacts()) {
            if (!Paths.get(artifact.getTarget()).toAbsolutePath().normalize().getParent().equals(publicationRoot.resolve("update"))) {
                return true;
            }
        }
        return false;
    }
}
