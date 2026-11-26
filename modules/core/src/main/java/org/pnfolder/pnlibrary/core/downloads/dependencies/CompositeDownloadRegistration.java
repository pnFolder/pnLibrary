package org.pnfolder.pnlibrary.core.downloads.dependencies;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.downloads.DownloadRegistration;
import org.pnfolder.pnlibrary.api.downloads.DownloadSnapshot;
import org.pnfolder.pnlibrary.core.downloads.CleanupSteps;

/** Presents two download registrations, such as declared files and dependencies, as one. */
public final class CompositeDownloadRegistration implements DownloadRegistration {
    private final DownloadRegistration first;
    private final DownloadRegistration second;

    private CompositeDownloadRegistration(DownloadRegistration first, DownloadRegistration second) {
        this.first = first;
        this.second = second;
    }

    /** One registration for both; when one is null the other is returned as it is. */
    public static @Nullable DownloadRegistration combine(@Nullable DownloadRegistration first,
                                                         @Nullable DownloadRegistration second) {
        if (first == null) {
            return second;
        }
        if (second == null) {
            return first;
        }
        return new CompositeDownloadRegistration(first, second);
    }

    @Override
    public boolean isClosed() {
        return first.isClosed() && second.isClosed();
    }

    @Override
    public @NotNull List<DownloadSnapshot> snapshots() {
        List<DownloadSnapshot> all = new ArrayList<>(first.snapshots());
        all.addAll(second.snapshots());
        return all;
    }

    @Override
    public @NotNull CompletionStage<List<DownloadSnapshot>> downloadNow() {
        return first.downloadNow().thenCombine(second.downloadNow(), (left, right) -> {
            List<DownloadSnapshot> all = new ArrayList<>(left);
            all.addAll(right);
            return all;
        });
    }

    @Override
    public void whenFinished(@NotNull Consumer<List<DownloadSnapshot>> handler) {
        first.whenFinished(handler);
        second.whenFinished(handler);
    }

    @Override
    public void close() {
        CleanupSteps.closeAll(first::close, second::close);
    }
}
