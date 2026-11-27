package org.pnfolder.pnlibrary.api.network;

/** How an attempt to move a player to another server ended; see {@link NetworkPlayer#connect(NetworkServer)}. */
public enum ConnectResult {
    /** The player is now on the target server. */
    CONNECTED,
    /** The player was already on the target server, nothing changed. */
    ALREADY_THERE,
    /** The player is being moved already; the new request was not taken. */
    BUSY,
    /** The proxy, a plugin or the server's access list refused the move. */
    DENIED,
    /** The server could not be reached or the player was left on the old one. */
    FAILED;

    /** Whether the player is on the target server after the attempt. */
    public boolean isOnTarget() {
        return this == CONNECTED || this == ALREADY_THERE;
    }
}
