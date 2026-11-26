package org.pnfolder.pnlibrary.bukkit;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import org.bukkit.entity.Player;
import org.pnfolder.pnlibrary.bukkit.commands.BukkitCommandSender;
import org.pnfolder.pnlibrary.bukkit.tasks.ServerScheduler;
import org.pnfolder.pnlibrary.common.lang.Attempt;
import org.pnfolder.pnlibrary.common.lang.Failures;

/** Hands work to the thread that may run it, and stops handing anything out once the adapter is closed. */
final class BukkitDispatcher {
    private final ServerScheduler scheduler;
    private final BooleanSupplier closed;
    private final Consumer<Throwable> failed;

    BukkitDispatcher(ServerScheduler scheduler, BooleanSupplier closed, Consumer<Throwable> failed) {
        this.scheduler = scheduler;
        this.closed = closed;
        this.failed = failed;
    }

    void global(Runnable task) {
        if (closed.getAsBoolean()) {
            return;
        }
        dispatch(() -> scheduler.execute(task));
    }

    /** Routes player replies to the player's thread, everything else to global dispatch. */
    void reply(Object recipient, Runnable task) {
        if (closed.getAsBoolean()) {
            return;
        }
        Object target = recipient instanceof BukkitCommandSender ? ((BukkitCommandSender) recipient).getNative() : recipient;
        if (target instanceof Player) {
            dispatch(() -> scheduler.onPlayer((Player) target, task));
        } else {
            dispatch(() -> scheduler.execute(task));
        }
    }

    // A reply that cannot be scheduled (the plugin is being disabled) must not fail the code that sent it.
    private void dispatch(Runnable schedule) {
        Attempt<Void> result = Failures.runCatchingNonFatal(() -> {
            schedule.run();
            return null;
        });
        if (result.isFailure()) {
            failed.accept(result.exceptionOrNull());
        }
    }
}
