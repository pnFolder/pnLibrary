package org.pnfolder.pnlibrary.bukkit.audience;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.UUID;
import java.util.logging.Logger;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.pnfolder.pnlibrary.api.audience.delivery.Delivery;
import org.pnfolder.pnlibrary.api.world.Particle;
import org.pnfolder.pnlibrary.api.world.StatusEffect;
import org.pnfolder.pnlibrary.bukkit.PnLoggerBuffer;

class BukkitOnlinePlayerTest {
    private final Server server = mock(Server.class);
    private final ConsoleCommandSender console = mock(ConsoleCommandSender.class);
    private final Player player = mock(Player.class);
    private final BukkitAdventureTransport audiences;
    private final BukkitOnlinePlayer library;
    private final Sound bell = Sound.sound(Key.key("minecraft:block.note_block.bell"), Sound.Source.MASTER, 1f, 1f);

    BukkitOnlinePlayerTest() {
        Plugin plugin = mock(Plugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));
        when(plugin.getServer()).thenReturn(server);
        audiences = new BukkitAdventureTransport(plugin, new PnLoggerBuffer(Logger.getAnonymousLogger(), () -> null), null);
        library = new BukkitOnlinePlayer(player, audiences);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getServer()).thenReturn(server);
        when(server.getConsoleSender()).thenReturn(console);
    }

    @Test
    void aSoundFallsBackToTheNativeApiAndIsDelivered() {
        assertEquals(Delivery.delivered(), audiences.playSound(player, bell));

        verify(player).playSound(ArgumentMatchers.<Location>any(), anyString(), anyFloat(), anyFloat());
    }

    @Test
    void aNativeSoundFailureKeepsItsCause() {
        IllegalStateException cause = new IllegalStateException("no sound");
        doThrow(cause).when(player).playSound(ArgumentMatchers.<Location>any(), anyString(), anyFloat(), anyFloat());

        Delivery failed = audiences.playSound(player, bell);

        assertSame(cause, failed.getFailure());
    }

    @Test
    void aLinkageErrorFromAnIncompatibleServerIsAFailedDeliveryNotACrash() {
        NoSuchMethodError cause = new NoSuchMethodError("playSound");
        doThrow(cause).when(player).playSound(ArgumentMatchers.<Location>any(), anyString(), anyFloat(), anyFloat());

        assertSame(cause, audiences.playSound(player, bell).getFailure());
    }

    @Test
    void aFailingJvmIsNotHidden() {
        doThrow(new OutOfMemoryError()).when(player).playSound(ArgumentMatchers.<Location>any(), anyString(), anyFloat(), anyFloat());

        assertThrows(OutOfMemoryError.class, () -> audiences.playSound(player, bell));
    }

    @Test
    void aCommandRunsAsThePlayerOrAsTheConsole() {
        when(server.dispatchCommand(player, "home")).thenReturn(true);
        when(server.dispatchCommand(console, "save-all")).thenReturn(false);

        assertEquals(Delivery.delivered(), library.runCommand("home"));
        assertEquals(Delivery.unsupported(), library.runConsoleCommand("save-all"));
    }

    @Test
    void anEffectTheServerDoesNotKnowIsUnsupported() {
        StatusEffect effect = new StatusEffect(Key.key("no_such_effect"), Duration.ofSeconds(5), 0, true, true, true);

        assertEquals(Delivery.unsupported(), library.addEffect(effect));
    }

    @Test
    void particlesAreUnsupportedWhereTheServerHasNoParticleApi() {
        assertEquals(Delivery.unsupported(),
            library.spawnParticle(new Particle(Key.key("flame"), 1, 0.0, 0.0, 0.0, 0.0)));
    }
}
