package org.pnfolder.pnlibrary.bukkit.audience;

import java.lang.reflect.Array;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.pnfolder.pnlibrary.api.audience.OnlinePlayer;
import org.pnfolder.pnlibrary.api.audience.delivery.Delivery;
import org.pnfolder.pnlibrary.api.logging.LogLevel;
import org.pnfolder.pnlibrary.bukkit.PnLoggerBuffer;
import org.pnfolder.pnlibrary.common.lang.Failures;

/**
 * Lifecycle-owned Adventure transport for Bukkit players.
 *
 * <p>Paper players are native audiences and keep click and hover events. Older servers go through
 * {@code adventure-platform-bukkit}, and every kind of delivery falls back to the legacy Bukkit API when Adventure
 * is missing or fails. Close it with the owning plugin to release the platform resources.
 */
public final class BukkitAdventureTransport implements AutoCloseable {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private static final boolean NATIVE_ADVENTURE = Audience.class.isAssignableFrom(Player.class);

    private final PnLoggerBuffer log;
    private final BukkitAudiences audiences;
    private final AtomicBoolean fallbackOnly;
    private final Set<String> failedKinds = ConcurrentHashMap.newKeySet();

    public BukkitAdventureTransport(Plugin plugin, PnLoggerBuffer log) {
        this(plugin, log, createProvider(plugin, log));
    }

    public BukkitAdventureTransport(Plugin plugin, PnLoggerBuffer log, BukkitAudiences audiences) {
        this.log = log;
        this.audiences = audiences;
        this.fallbackOnly = new AtomicBoolean(audiences == null && !NATIVE_ADVENTURE);
    }

    /** Whether the native Adventure transport is still available for new deliveries. */
    public boolean isNativeTransportAvailable() {
        return !fallbackOnly.get();
    }

    /** Wraps an online Bukkit {@code player} for the library API. */
    public OnlinePlayer player(Player player) {
        return new BukkitOnlinePlayer(player, this);
    }

    void sendMessage(Player player, Component message) {
        if (!viaAdventure(player, "message", recipient -> recipient.sendMessage(message))) {
            player.sendMessage(LEGACY.serialize(message));
        }
    }

    void sendActionBar(Player player, Component message) {
        if (!viaAdventure(player, "action bar", recipient -> recipient.sendActionBar(message))) {
            sendLegacyActionBar(player, LEGACY.serialize(message));
        }
    }

    /** Tries Adventure sound delivery, then the native Bukkit sound API. */
    Delivery playSound(Player player, Sound sound) {
        if (viaAdventure(player, "sound", recipient -> recipient.playSound(sound))) {
            return Delivery.delivered();
        }
        return deliver(() -> player.playSound(player.getLocation(), sound.name().asString(), sound.volume(), sound.pitch()));
    }

    /** Shows a title through Adventure when available, otherwise through the legacy two-line API. */
    @SuppressWarnings("deprecation")
    Delivery showTitle(Player player, Title title) {
        if (viaAdventure(player, "title", recipient -> recipient.showTitle(title))) {
            return Delivery.delivered();
        }
        return deliver(() -> player.sendTitle(LEGACY.serialize(title.title()), LEGACY.serialize(title.subtitle())));
    }

    /**
     * Runs {@code send} on the Adventure recipient of {@code player} and says whether it did. A kind of delivery that
     * failed once stays on the legacy path, so a broken bridge is reported once and not on every message.
     */
    private boolean viaAdventure(Player player, String kind, Consumer<Audience> send) {
        if (fallbackOnly.get() || failedKinds.contains(kind)) {
            return false;
        }
        Audience recipient = recipient(player);
        if (recipient == null) {
            return false;
        }
        try {
            send.accept(recipient);
            return true;
        } catch (Throwable error) {
            Failures.rethrowIfFatal(error);
            if (failedKinds.add(kind)) {
                log.log(LogLevel.ERROR, "Adventure " + kind + " delivery failed; using the legacy fallback", error);
            }
            return false;
        }
    }

    /** Paper players are already audiences; elsewhere the platform provider finds them, and an empty answer means it is broken. */
    private Audience recipient(Player player) {
        Audience found = player instanceof Audience ? (Audience) player : null;
        if (found == null && audiences != null) {
            found = audiences.player(player.getUniqueId());
        }
        if (found != null && found != Audience.empty()) {
            return found;
        }
        fallbackOnly.set(true);
        return null;
    }

    private void sendLegacyActionBar(Player player, String message) {
        try {
            Object chatType = Class.forName("net.md_5.bungee.api.ChatMessageType").getField("ACTION_BAR").get(null);
            Class<?> componentType = Class.forName("net.md_5.bungee.api.chat.BaseComponent");
            Class<?> textComponent = Class.forName("net.md_5.bungee.api.chat.TextComponent");
            Object components = textComponent.getMethod("fromLegacyText", String.class).invoke(null, message);
            player.getClass().getMethod("spigot").invoke(player).getClass()
                .getMethod("sendMessage", chatType.getClass(), Array.newInstance(componentType, 0).getClass())
                .invoke(player.spigot(), chatType, components);
        } catch (Throwable error) {
            Failures.rethrowIfFatal(error);
            player.sendMessage(message);
        }
    }

    /** Releases the underlying Adventure audience provider when one was created. */
    @Override
    public void close() {
        if (audiences == null) {
            return;
        }
        try {
            audiences.close();
        } catch (Throwable error) {
            Failures.rethrowIfFatal(error);
            log.log(LogLevel.ERROR, "Could not close the Adventure transport", error);
        }
    }

    private static BukkitAudiences createProvider(Plugin plugin, PnLoggerBuffer log) {
        if (NATIVE_ADVENTURE) {
            return null;
        }
        try {
            return BukkitAudiences.create(plugin);
        } catch (Throwable error) {
            Failures.rethrowIfFatal(error);
            log.log(LogLevel.WARNING, "Adventure Bukkit transport is unavailable (" + error.getClass().getSimpleName()
                + "); using legacy text fallback");
            return null;
        }
    }

    /** Runs the last-resort native call, keeping the platform's error instead of a bare failure flag. */
    private static Delivery deliver(Runnable send) {
        try {
            send.run();
            return Delivery.delivered();
        } catch (Throwable error) {
            Failures.rethrowIfFatal(error);
            return Delivery.failed(error);
        }
    }
}
