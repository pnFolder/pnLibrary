package org.pnfolder.pnlibrary.core.placeholders;

import org.pnfolder.pnlibrary.core.placeholders.scan.PhNode;
import java.util.List;

/** A placeholder found in the tree and the nodes that lead to it. */
final class Match {
    private final PhNode node;
    private final List<PhNode> path;

    Match(PhNode node, List<PhNode> path) {
        this.node = node;
        this.path = path;
    }

    PhNode getNode() {
        return node;
    }

    List<PhNode> getPath() {
        return path;
    }
}
