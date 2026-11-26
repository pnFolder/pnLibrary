package org.pnfolder.pnlibrary.core.updates.catalog;

/** Describes the operation authorized by a single-use confirmation token. */
public enum UpdateAction {
    /** Restart the server at once (after a short countdown for players) to apply the staged plan. */
    RESTART,
    /** Restart the server after a five-minute countdown shown to the players. */
    RESTART_LATER,
    /** Restart the server as soon as no player is online. */
    RESTART_EMPTY,
    /** Roll back the whole current update plan. */
    PLAN_ROLLBACK,
    /** Restore one saved backup set; the grant is bound to the backup id. */
    ROLLBACK,
    /** Restore one saved backup set together with its configuration files. */
    ROLLBACK_CONFIGS
}
