package org.pnfolder.pnlibrary.currency.storage;

import org.jetbrains.annotations.NotNull;

/** Work queued for the single writer of {@link FileCurrencyStorage}. */
interface StorageJob {
    /** Reports that the job will never run, so nobody waits for it forever. */
    void fail(@NotNull Throwable error);
}
