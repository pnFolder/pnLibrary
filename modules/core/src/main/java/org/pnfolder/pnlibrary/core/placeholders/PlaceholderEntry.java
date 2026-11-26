package org.pnfolder.pnlibrary.core.placeholders;

import java.util.Objects;
import org.pnfolder.pnlibrary.core.placeholders.scan.PhNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** The tree of one group as one owner registered it, with the PlaceholderAPI prefix it is shown under. */
final class PlaceholderEntry {
    private final String owner;
    private final PhNode root;
    private final String papi;

    PlaceholderEntry(@NotNull String owner, @NotNull PhNode root, @Nullable String papi) {
        this.owner = Objects.requireNonNull(owner, "owner");
        this.root = Objects.requireNonNull(root, "root");
        this.papi = papi;
    }

    String getOwner() {
        return owner;
    }

    PhNode getRoot() {
        return root;
    }

    String getPapi() {
        return papi;
    }

    String getGroup() {
        return root.getPart().getName();
    }
}
