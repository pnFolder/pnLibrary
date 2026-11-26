package org.pnfolder.pnlibrary.bukkit.commands;

import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;

import org.bukkit.Bukkit;
import org.pnfolder.pnlibrary.api.logging.PnLogger;
import org.pnfolder.pnlibrary.api.runtime.PnLibrary;
import org.pnfolder.pnlibrary.api.updates.state.UpdatePlanSnapshot;
import org.pnfolder.pnlibrary.api.updates.state.UpdateState;
import org.pnfolder.pnlibrary.bukkit.tasks.ServerScheduler;
import org.pnfolder.pnlibrary.core.settings.RestartSettings;
import org.pnfolder.pnlibrary.core.updates.restart.AutoRestartRule;
import org.pnfolder.pnlibrary.core.updates.restart.AutoRestartRule.Verdict;
import org.pnfolder.pnlibrary.core.updates.restart.QuietClock;

/**
 * Watches for a staged update plan and, as the settings say, asks administrators to restart or restarts the server.
 *
 * <p>It only speeds up what any restart does anyway: a restart that happens earlier, by hand or by itself, applies
 * the plan, the plan disappears and the watcher forgets it. The administrators are always asked once
 * per plan; the restart alone happens only with {@code updates.restart.auto}.
 */
final class BukkitRestartWatcher {
    private static final long CHECK_TICKS = 200L;
    private static final String ROLLED_BACK = "План откатан. После отката сервер сам не перезапускается: решите, когда.";

    private final PnLibrary library;
    private final ServerRestarter restarter;
    private final BukkitRestartFlow flow;
    private final Supplier<RestartSettings> settings;
    private final Supplier<PnLogger> log;
    private final Instant started;
    private final QuietClock quiet = new QuietClock();
    private ServerScheduler.Scheduled timer;
    private boolean looked;
    private String ignored;
    private String asked;
    private String autoDone;

    BukkitRestartWatcher(PnLibrary library, ServerRestarter restarter, BukkitRestartFlow flow,
                         Supplier<RestartSettings> settings, Supplier<PnLogger> log, Instant started) {
        this.library = library;
        this.restarter = restarter;
        this.flow = flow;
        this.settings = settings;
        this.log = log;
        this.started = started;
    }

    void start(ServerScheduler scheduler) {
        timer = scheduler.repeat(CHECK_TICKS, CHECK_TICKS, this::check);
    }

    void close() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
    }

    /**
     * Withdraws the automatic restart of the plan that waits now.
     *
     * @return false when there was nothing automatic to withdraw
     */
    synchronized boolean suppressAuto() {
        UpdatePlanSnapshot plan = library.getUpdates().getPlans().current().orElse(null);
        String key = plan == null ? null : keyOf(plan);
        if (key == null || !settings.get().getAuto() || key.equals(autoDone)) {
            return false;
        }
        autoDone = key;
        return true;
    }

    synchronized void check() {
        if (library.isClosed()) {
            return;
        }
        UpdatePlanSnapshot plan = library.getUpdates().getPlans().current().orElse(null);
        if (plan == null || plan.getState() != UpdateState.UPDATE_STAGED && plan.getState() != UpdateState.ROLLED_BACK) {
            quiet.reset();
            return;
        }
        String key = keyOf(plan);
        boolean rolledBack = plan.getState() == UpdateState.ROLLED_BACK;
        if (!looked) {
            looked = true;
            // A rollback already there when the server started was dealt with by that restart.
            ignored = rolledBack ? key : null;
        }
        if (key.equals(ignored)) {
            return;
        }
        RestartSettings now = settings.get();
        Verdict verdict = verdict(now, rolledBack);
        askOnce(plan, key, now, verdict);
        if (verdict == Verdict.RESTART && !key.equals(autoDone) && !restarter.busy()) {
            restartAlone(key, now);
        }
    }

    private Verdict verdict(RestartSettings now, boolean rolledBack) {
        int players = Bukkit.getOnlinePlayers().size();
        Instant at = Instant.now();
        Duration calm = quiet.observe(players, now.getMaxPlayers(), at);
        return AutoRestartRule.check(now, players, calm, Duration.between(started, at), rolledBack);
    }

    private void askOnce(UpdatePlanSnapshot plan, String key, RestartSettings now, Verdict verdict) {
        if (key.equals(asked)) {
            return;
        }
        asked = key;
        flow.announce(plan, verdict == Verdict.ROLLED_BACK ? ROLLED_BACK : null);
    }

    private void restartAlone(String key, RestartSettings now) {
        autoDone = key;
        log.get().warning("Автоматический перезапуск для применения обновлений (действие: system, updates.restart.auto): "
            + "игроков не больше " + now.getMaxPlayers() + " уже " + now.getQuietTime().getSeconds() + " с.");
        restarter.start(ServerRestarter.Mode.NOW);
    }

    private static String keyOf(UpdatePlanSnapshot plan) {
        return plan.getId() + ":" + plan.getRevision();
    }
}
