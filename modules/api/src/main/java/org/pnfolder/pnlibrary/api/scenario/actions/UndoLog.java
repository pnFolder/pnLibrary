package org.pnfolder.pnlibrary.api.scenario.actions;

import org.pnfolder.pnlibrary.api.logging.PnLogger;
import org.pnfolder.pnlibrary.common.lang.Failures;

import java.util.ArrayList;
import java.util.List;

/** The steps of one scenario run that can take back what they did, newest last. */
final class UndoLog {
    private static final class Step {
        final String description;
        final Runnable undo;

        Step(String description, Runnable undo) {
            this.description = description;
            this.undo = undo;
        }
    }

    private final List<Step> steps = new ArrayList<>();

    void add(String description, Runnable undo) {
        steps.add(new Step(description, undo));
    }

    int size() {
        return steps.size();
    }

    void discardAfter(int mark) {
        while (steps.size() > mark) {
            steps.remove(steps.size() - 1);
        }
    }

    void rollbackAfter(int mark, PnLogger logger) {
        while (steps.size() > mark) {
            Step step = steps.remove(steps.size() - 1);
            Failures.runCatchingNonFatal(() -> {
                step.undo.run();
                return null;
            }).onFailure(error -> logger.error("Could not undo: " + step.description, error));
        }
    }
}
