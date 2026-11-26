package org.pnfolder.pnlibrary.core.link.remote;

import org.jetbrains.annotations.NotNull;

/** What one server may ask another to do through the link. Only the update controls of pnLibrary; no commands. */
public enum RemoteAction {
    /** Look for updates now. */
    CHECK("check", false, "pnlibrary.updates.view"),

    /** Find and download the update of one plugin; it applies after a restart. */
    UPDATE("update", true, "pnlibrary.updates.download"),

    /** Turn automatic download on for one plugin. */
    AUTO_ON("auto-on", true, "pnlibrary.updates.settings"),

    /** Turn automatic download off for one plugin. */
    AUTO_OFF("auto-off", true, "pnlibrary.updates.settings");

    private final String word;
    private final boolean needsProduct;
    private final String permission;

    RemoteAction(String word, boolean needsProduct, String permission) {
        this.word = word;
        this.needsProduct = needsProduct;
        this.permission = permission;
    }

    /** The word used in the {@code /pn servers <name> <word>} command. */
    public @NotNull String getWord() {
        return word;
    }

    /** Whether the request must name a plugin. */
    public boolean getNeedsProduct() {
        return needsProduct;
    }

    /** The permission the person asking must have on the server they ask from. */
    public @NotNull String getPermission() {
        return permission;
    }
}
