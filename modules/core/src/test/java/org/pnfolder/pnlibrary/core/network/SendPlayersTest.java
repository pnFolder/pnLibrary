package org.pnfolder.pnlibrary.core.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.network.ConnectResult;
import org.pnfolder.pnlibrary.api.network.NetworkPlayer;
import org.pnfolder.pnlibrary.api.network.NetworkServer;

class SendPlayersTest {
    private static NetworkPlayer playerWho(CompletableFuture<ConnectResult> answer, NetworkServer target) {
        NetworkPlayer player = mock(NetworkPlayer.class);
        when(player.connect(target)).thenReturn(answer);
        return player;
    }

    @Test
    void countsOnlyThePlayersThatEndedOnTheTarget() {
        NetworkServer target = mock(NetworkServer.class);
        NetworkServer from = mock(NetworkServer.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));
        CompletableFuture<ConnectResult> broken = new CompletableFuture<>();
        broken.completeExceptionally(new IllegalStateException("boom"));
        List<NetworkPlayer> players = Arrays.asList(
                playerWho(CompletableFuture.completedFuture(ConnectResult.CONNECTED), target),
                playerWho(CompletableFuture.completedFuture(ConnectResult.ALREADY_THERE), target),
                playerWho(CompletableFuture.completedFuture(ConnectResult.DENIED), target),
                playerWho(broken, target));
        when(from.getPlayers()).thenReturn(players);

        assertEquals(2, from.sendPlayers(target).toCompletableFuture().join());
    }

    @Test
    void onlyTwoResultsMeanOnTarget() {
        assertTrue(ConnectResult.CONNECTED.isOnTarget());
        assertTrue(ConnectResult.ALREADY_THERE.isOnTarget());
        assertFalse(ConnectResult.BUSY.isOnTarget());
        assertFalse(ConnectResult.DENIED.isOnTarget());
        assertFalse(ConnectResult.FAILED.isOnTarget());
    }
}
