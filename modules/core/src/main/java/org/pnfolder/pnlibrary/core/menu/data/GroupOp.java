package org.pnfolder.pnlibrary.core.menu.data;

/** Kind of change the menu asks a proxy to make to server groups. */
public enum GroupOp {
    CREATE,
    DELETE,
    ADD_SERVER,
    REMOVE_SERVER,
    ADD_PLUGIN,
    REMOVE_PLUGIN,
    NEED,
    /** One server of the group does not need a plugin, whatever the rest do. */
    SKIP,
    RESET
}
