package org.pnfolder.pnlibrary.api.scenario.actions;

/** Who a message, title or sound of an action goes to. */
public enum ActionTarget {
    /** The player the scenario runs for. */
    PLAYER,

    /** Everyone the scenario was started for, see {@link ActionContext#getAllPlayers}. */
    ALL_PLAYERS
}
