package org.pnfolder.pnlibrary.api.currency;

/** Kind of principal responsible for requesting a transaction. */
public enum CurrencyActorType {
    /** A player initiated the operation. */
    PLAYER,

    /** A registered plugin initiated the operation. */
    PLUGIN,

    /** A named application service initiated the operation. */
    SERVICE,

    /** The current Minecraft server instance initiated the operation. */
    SERVER,

    /** An internal or automated system process initiated the operation. */
    SYSTEM
}
