package org.pnfolder.pnlibrary.updates.service;

import org.pnfolder.pnlibrary.updates.service.orchestrator.UpdateOrchestratorRuntime;
import java.util.List;
import java.util.concurrent.CompletionStage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.updates.UpdateHistory;
import org.pnfolder.pnlibrary.api.updates.history.UpdateHistoryEntry;

/** The durable update history, read off the calling thread. */
final class HistoryReader implements UpdateHistory {
    private final UpdateOrchestratorRuntime runtime;

    HistoryReader(UpdateOrchestratorRuntime runtime) {
        this.runtime = runtime;
    }

    @NotNull
    @Override
    public CompletionStage<List<UpdateHistoryEntry>> page(int page, @Nullable String product) {
        return runtime.historyPage(page, product);
    }

    @NotNull
    @Override
    public CompletionStage<Integer> count(@Nullable String product) {
        return runtime.historyCount(product);
    }
}
