package org.pnfolder.pnlibrary.currency;

import java.util.function.Consumer;

import org.jetbrains.annotations.NotNull;

/** A read or an import that runs on the storage worker, between batches. */
final class OtherJob implements StorageJob {
    private final Runnable action;
    private final Consumer<Throwable> onFail;

    OtherJob(@NotNull Runnable action, @NotNull Consumer<Throwable> onFail) {
        this.action = action;
        this.onFail = onFail;
    }

    void run() {
        action.run();
    }

    @Override
    public void fail(@NotNull Throwable error) {
        onFail.accept(error);
    }
}
