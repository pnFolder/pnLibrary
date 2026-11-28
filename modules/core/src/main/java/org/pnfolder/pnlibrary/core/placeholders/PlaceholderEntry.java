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
    private final boolean fromRoot;

    PlaceholderEntry(@NotNull String owner, @NotNull PhNode root, @Nullable String papi) {
        this(owner, root, papi, false);
    }

    PlaceholderEntry(@NotNull String owner, @NotNull PhNode root, @Nullable String papi, boolean fromRoot) {
        this.fromRoot = fromRoot;
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

    /** The first word of the addresses; empty when the tree starts with an argument (a bare member of a root class). */
    String getGroup() {
        return root.getPart().isSlot() ? PlaceholderGroups.SLOT_GROUP : root.getPart().getName();
    }

    boolean isFromRoot() {
        return fromRoot;
    }
}
