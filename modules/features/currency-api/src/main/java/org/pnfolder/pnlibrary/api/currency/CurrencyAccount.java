package org.pnfolder.pnlibrary.api.currency;

import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/** Currency account identified by a stable player UUID. */
public final class CurrencyAccount {
    private final UUID playerId;

    /** Creates the account of the player with the given UUID. */
    public CurrencyAccount(@NotNull UUID playerId) {
        this.playerId = playerId;
    }

    /** Player that owns the balance. */
    @NotNull
    public UUID getPlayerId() {
        return playerId;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof CurrencyAccount && playerId.equals(((CurrencyAccount) other).playerId);
    }

    @Override
    public int hashCode() {
        return playerId.hashCode();
    }

    @Override
    public String toString() {
        return "CurrencyAccount(playerId=" + playerId + ")";
    }
}
