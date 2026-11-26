package org.pnfolder.pnlibrary.core.placeholders.scan;

/** The first node of a scanned tree; {@code root} tells that it came from a class without a group word. */
public final class ScannedRoot {
    private final PhNode node;
    private final boolean root;

    ScannedRoot(PhNode node, boolean root) {
        this.node = node;
        this.root = root;
    }

    public PhNode getNode() {
        return node;
    }

    public boolean isRoot() {
        return root;
    }
}
