package org.pnfolder.pnlibrary.core.plugin;

import org.pnfolder.pnlibrary.api.tasks.TaskScope;

/** A task scope that notes that it was closed. */
public final class TaskScopeRecorder {
    public boolean closed;
    public final TaskScope value;

    public TaskScopeRecorder(Object owner) {
        this.value = PluginRegistryFixture.proxy(TaskScope.class, name -> {
            switch (name) {
                case "getOwner":
                    return owner;
                case "close":
                case "cancelAll":
                    closed = true;
                    return null;
                default:
                    return null;
            }
        });
    }
}
