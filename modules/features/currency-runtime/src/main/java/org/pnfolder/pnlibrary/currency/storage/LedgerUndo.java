package org.pnfolder.pnlibrary.currency.storage;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.NotNull;

/** Reverse steps of the changes made to a {@link CurrencyLedgerState}; reverting restores it exactly. */
public final class LedgerUndo {
    private final List<Runnable> steps = new ArrayList<>();

    public void add(@NotNull Runnable step) {
        steps.add(step);
    }

    /** Position to pass to {@link #revertTo} to undo only what is added after this call. */
    int mark() {
        return steps.size();
    }

    void revertTo(int mark) {
        while (steps.size() > mark) {
            steps.remove(steps.size() - 1).run();
        }
    }

    void revert() {
        revertTo(0);
    }
}
