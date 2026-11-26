package org.pnfolder.pnlibrary.core.tasks;

import org.pnfolder.pnlibrary.api.tasks.info.TaskSnapshot;

/** A finished task as the history keeps it, with the owner and scope it belonged to. */
final class ArchivedTask {
    private final Object owner;
    private final Object scopeToken;
    private final TaskSnapshot snapshot;

    ArchivedTask(Object owner, Object scopeToken, TaskSnapshot snapshot) {
        this.owner = owner;
        this.scopeToken = scopeToken;
        this.snapshot = snapshot;
    }

    Object getOwner() {
        return owner;
    }

    Object getScopeToken() {
        return scopeToken;
    }

    TaskSnapshot getSnapshot() {
        return snapshot;
    }
}
