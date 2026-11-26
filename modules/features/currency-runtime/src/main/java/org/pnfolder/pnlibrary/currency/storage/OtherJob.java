package org.pnfolder.pnlibrary.currency.storage;

import java.util.Objects;
import java.util.function.Consumer;

import org.jetbrains.annotations.NotNull;

/** A read or an import that runs on the storage worker, between batches. */
final class OtherJob implements StorageJob {
    private final Runnable action;
    private final Consumer<Throwable> onFail;

    public OtherJob(@NotNull Runnable action, @NotNull Consumer<Throwable> onFail) {
        this.action = Objects.requireNonNull(action, "action");
        this.onFail = Objects.requireNonNull(onFail, "onFail");
    }

    void run() {
        action.run();
    }

    @Override
    public void fail(@NotNull Throwable error) {
        onFail.accept(error);
    }
}
