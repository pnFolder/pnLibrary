package org.pnfolder.pnlibrary.core.menu.data;

import org.pnfolder.pnlibrary.core.link.ServerGroups;

/** Applies one {@link GroupEdit} to the server groups of a proxy. */
final class GroupEdits {
    private GroupEdits() {
    }

    static boolean apply(ServerGroups store, GroupEdit edit) {
        final String server = edit.getServer() == null ? "" : edit.getServer();
        final String plugin = edit.getPlugin() == null ? "" : edit.getPlugin();
        String group = edit.getGroup();
        switch (edit.getOp()) {
            case CREATE:
                return store.create(group);
            case DELETE:
                return store.delete(group);
            case ADD_SERVER:
                return store.edit(group, current -> current.addServer(server));
            case REMOVE_SERVER:
                return store.edit(group, current -> current.removeServer(server));
            case ADD_PLUGIN:
                return store.edit(group, current -> current.addPlugin(plugin));
            case REMOVE_PLUGIN:
                return store.edit(group, current -> current.removePlugin(plugin));
            case NEED:
                return store.edit(group, current -> current.with(server, plugin, true));
            case SKIP:
                return store.edit(group, current -> current.with(server, plugin, false));
            default:
                return store.edit(group, current -> current.reset(server));
        }
    }
}
