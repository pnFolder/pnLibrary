package org.pnfolder.pnlibrary.bukkit.commands;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import org.bukkit.entity.Player;
import org.pnfolder.pnlibrary.bukkit.audience.BukkitTitle;
import org.pnfolder.pnlibrary.bukkit.tasks.ServerScheduler;
import org.pnfolder.pnlibrary.common.lang.Failures;
import org.pnfolder.pnlibrary.core.updates.catalog.UpdateCatalogCommands.SettingSaved;

/**
 * Short feedback after a setting was saved: the setting's name as the title and what changed, such as
 * {@code stable → beta}, as the subtitle, fading after about two seconds, and a small chime that rises for "on" and
 * falls for "off".
 *
 * <p>The 1.8 title API has no timings and different sound names, so both are chosen at runtime; nothing here may
 * break the command that was just completed.
 */
final class BukkitSettingFeedback {
    private static final int FADE_IN_TICKS = 5;
    private static final int STAY_TICKS = 30;
    private static final int FADE_OUT_TICKS = 10;
    private static final long NOTE_GAP_TICKS = 3L;
    private static final float VOLUME = 0.6f;

    /** The title with timings, added in 1.11. */
    private static final Method TIMED_TITLE = Failures.runCatchingNonFatal(() -> Player.class.getMethod(
        "sendTitle", String.class, String.class, Integer.TYPE, Integer.TYPE, Integer.TYPE)).getOrNull();

    /** Sound names changed in 1.9; the presence of the enum constant tells which generation this server is. */
    private static final boolean MODERN_SOUNDS = Failures.runCatchingNonFatal(
        () -> Class.forName("org.bukkit.Sound").getField("ENTITY_EXPERIENCE_ORB_PICKUP")).isSuccess();

    private final ServerScheduler scheduler;

    BukkitSettingFeedback(ServerScheduler scheduler) {
        this.scheduler = scheduler;
    }

    /** Shows the title and plays the chime for {@code player}; call it on the player's thread. */
    void show(Player player, SettingSaved saved) {
        Failures.runCatchingNonFatal(() -> {
            title(player, saved);
            return null;
        });
        Failures.runCatchingNonFatal(() -> {
            chime(player, notes(saved));
            return null;
        });
    }

    @SuppressWarnings("deprecation")
    private void title(Player player, SettingSaved saved) throws ReflectiveOperationException {
        BukkitTitle title = BukkitTitle.settingSaved(saved);
        if (TIMED_TITLE != null) {
            TIMED_TITLE.invoke(player, title.getTitle(), title.getSubtitle(), FADE_IN_TICKS, STAY_TICKS, FADE_OUT_TICKS);
            return;
        }
        player.sendTitle(title.getTitle(), title.getSubtitle());
    }

    /** Rising notes for turning something on or picking a value, falling notes for turning it off. */
    private static List<Float> notes(SettingSaved saved) {
        if (saved.getTo().equals("выключена")) {
            return Arrays.asList(1.5f, 1.0f);
        }
        if (saved.getSetting().equals("Пауза") && !saved.getTo().equals("нет")) {
            return Arrays.asList(0.8f, 1.0f);
        }
        return Arrays.asList(1.0f, 1.26f, 1.5f);
    }

    private void chime(Player player, List<Float> pitches) {
        String sound = MODERN_SOUNDS ? "entity.experience_orb.pickup" : "random.orb";
        for (int index = 0; index < pitches.size(); index++) {
            float pitch = pitches.get(index);
            Runnable play = () -> {
                if (player.isOnline()) {
                    player.playSound(player.getLocation(), sound, VOLUME, pitch);
                }
            };
            if (index == 0) {
                play.run();
            } else {
                scheduler.onPlayerLater(player, index * NOTE_GAP_TICKS, play);
            }
        }
    }
}
