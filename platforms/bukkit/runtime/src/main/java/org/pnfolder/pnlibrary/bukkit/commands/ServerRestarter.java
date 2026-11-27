package org.pnfolder.pnlibrary.bukkit.commands;

import java.io.File;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.pnfolder.pnlibrary.api.chat.style.ChatPalette;
import org.pnfolder.pnlibrary.api.logging.PnLogger;
import org.pnfolder.pnlibrary.bukkit.tasks.ServerScheduler;
import org.pnfolder.pnlibrary.core.settings.RestartSettings;
import org.pnfolder.pnlibrary.core.updates.restart.QuietClock;

/**
 * Restarts the server so staged updates apply: at once, after a countdown the players see, when nobody is online, or when few players stay online for a while.
 *
 * <p>With {@code restart-script} in {@code spigot.yml} the server restarts itself. Without it the server is stopped
 * cleanly and comes back only if something outside starts it again (a hosting panel, a {@code start.sh} loop,
 * Pterodactyl); the library cannot tell which, so it says so without calling it a problem.
 * Only one restart can be waiting; a new request replaces it and {@link #cancel()} withdraws it.
 */
final class ServerRestarter {
    private static final String SPIGOT_CONFIG = "spigot.yml";
    private static final long TICKS_PER_SECOND = 20L;
    private static final long EMPTY_CHECK_TICKS = 200L;
    private static final int SECONDS_PER_MINUTE = 60;
    private static final int QUICK_SECONDS = 2;
    private static final int NOTICE_SECONDS = 10;
    private static final int LATER_SECONDS = 300;
    private static final Set<Integer> WARNINGS = new HashSet<>(Arrays.asList(300, 60, 30, 10, 5, 3, 2, 1));

    /** When the restart happens. */
    enum Mode { NOW, IN_FIVE_MINUTES, WHEN_EMPTY, WHEN_FEW }

    /** What {@link #start} set up. */
    enum Started { COUNTDOWN, WAITING_FOR_EMPTY, WAITING_FOR_FEW }

    private final ServerScheduler scheduler;
    private final Supplier<PnLogger> log;
    private final Supplier<RestartSettings> settings;
    private volatile ServerScheduler.Scheduled waiting;

    ServerRestarter(ServerScheduler scheduler, Supplier<PnLogger> log, Supplier<RestartSettings> settings) {
        this.scheduler = scheduler;
        this.log = log;
        this.settings = settings;
    }

    /** Cancels any waiting restart, then begins the restart in the requested {@code mode}. */
    Started start(Mode mode) {
        cancel();
        switch (mode) {
            case NOW:
                return countdown(Bukkit.getOnlinePlayers().isEmpty() ? QUICK_SECONDS : NOTICE_SECONDS);
            case IN_FIVE_MINUTES:
                return countdown(LATER_SECONDS);
            case WHEN_FEW:
                return waitForFew();
            default:
                return waitForEmpty();
        }
    }

    /** The restart settings in force now. */
    RestartSettings settings() {
        return settings.get();
    }

    /** Withdraws the waiting restart; false when none was waiting. */
    boolean cancel() {
        ServerScheduler.Scheduled task = waiting;
        if (task == null) {
            return false;
        }
        waiting = null;
        task.cancel();
        return true;
    }

    private Started countdown(int seconds) {
        int[] left = {seconds};
        waiting = scheduler.repeat(0L, TICKS_PER_SECOND, () -> {
            if (WARNINGS.contains(left[0]) && !Bukkit.getOnlinePlayers().isEmpty()) {
                announce("Сервер перезапустится через " + wait(left[0]) + " для применения обновлений.");
            }
            if (left[0] <= 0) {
                cancel();
                restart();
            }
            left[0]--;
        });
        return Started.COUNTDOWN;
    }

    private Started waitForEmpty() {
        waiting = scheduler.repeat(0L, EMPTY_CHECK_TICKS, () -> {
            if (Bukkit.getOnlinePlayers().isEmpty()) {
                cancel();
                restart();
            }
        });
        return Started.WAITING_FOR_EMPTY;
    }

    /** Waits until the players stay at or below the limit for the quiet time; the limit and time are read at each look. */
    private Started waitForFew() {
        QuietClock clock = new QuietClock();
        waiting = scheduler.repeat(0L, EMPTY_CHECK_TICKS, () -> {
            RestartSettings now = settings.get();
            int players = Bukkit.getOnlinePlayers().size();
            if (clock.observe(players, now.getMaxPlayers(), Instant.now()).compareTo(now.getQuietTime()) >= 0) {
                cancel();
                countdown(players == 0 ? QUICK_SECONDS : NOTICE_SECONDS);
            }
        });
        return Started.WAITING_FOR_FEW;
    }

    /** Whether a restart is counting down or waiting for its condition. */
    boolean busy() {
        return waiting != null;
    }

    @SuppressWarnings("deprecation")
    private void restart() {
        if (script() != null) {
            announce("Сервер перезапускается для применения обновлений.");
            Bukkit.spigot().restart();
        } else {
            announce("Сервер останавливается для применения обновлений. Если у него есть автозапуск, он включится сам.");
            Bukkit.shutdown();
        }
    }

    /** The restart script {@code spigot.yml} names, or null when none is set or the file does not exist. */
    File script() {
        String name = YamlConfiguration.loadConfiguration(new File(SPIGOT_CONFIG)).getString("settings.restart-script");
        if (name == null || name.trim().isEmpty()) {
            return null;
        }
        File file = new File(name.trim().split(" ")[0]);
        return file.isFile() ? file : null;
    }

    private static void announce(String text) {
        Component line = Component.text("[pnFolder] ", ChatPalette.ACCENT).append(Component.text(text, ChatPalette.TEXT));
        Bukkit.broadcastMessage(LegacyComponentSerializer.legacySection().serialize(line));
    }

    private static String wait(int seconds) {
        return seconds >= SECONDS_PER_MINUTE ? seconds / SECONDS_PER_MINUTE + " мин" : seconds + " с";
    }
}
