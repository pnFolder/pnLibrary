package org.pnfolder.pnlibrary.core.commands.downloads;

import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.downloads.DownloadSnapshot;

/** One declared download: who asked ({@code plugin/module}) and its state. */
public final class DeclaredDownload {
    private final String owner;
    private final DownloadSnapshot snapshot;

    public DeclaredDownload(@NotNull String owner, @NotNull DownloadSnapshot snapshot) {
        this.owner = owner;
        this.snapshot = snapshot;
    }

    @NotNull
    public String getOwner() {
        return owner;
    }

    @NotNull
    public DownloadSnapshot getSnapshot() {
        return snapshot;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof DeclaredDownload)) {
            return false;
        }
        DeclaredDownload that = (DeclaredDownload) other;
        return owner.equals(that.owner) && snapshot.equals(that.snapshot);
    }

    @Override
    public int hashCode() {
        return Objects.hash(owner, snapshot);
    }

    @Override
    public String toString() {
        return "DeclaredDownload(owner=" + owner + ", snapshot=" + snapshot + ")";
    }
}
