package org.pnfolder.pnlibrary.core.runtime.platform;

import java.util.function.Consumer;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.audience.ForwardingAudience;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.inventory.Book;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.sound.SoundStop;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.common.concurrent.FailureThrottle;
import org.pnfolder.pnlibrary.common.lang.Failures;

/**
 * One receiver whose failures stay inside it: a group keeps sending to the others, and the report hears about a
 * repeated failure only once in a while. Fatal errors still propagate.
 */
public final class FailureIsolatedAudience implements ForwardingAudience.Single {
    private final Audience receiver;
    private final FailureThrottle throttle;
    private final Consumer<Throwable> report;

    public FailureIsolatedAudience(@NotNull Audience receiver, @NotNull FailureThrottle throttle, @NotNull Consumer<Throwable> report) {
        this.receiver = receiver;
        this.throttle = throttle;
        this.report = report;
    }

    @Override
    public @NotNull Audience audience() {
        return receiver;
    }

    @Override
    public void sendMessage(@NotNull Component message) {
        guard(() -> receiver.sendMessage(message));
    }

    @Override
    public void sendActionBar(@NotNull Component message) {
        guard(() -> receiver.sendActionBar(message));
    }

    @Override
    public void sendPlayerListHeaderAndFooter(@NotNull Component header, @NotNull Component footer) {
        guard(() -> receiver.sendPlayerListHeaderAndFooter(header, footer));
    }

    @Override
    public void playSound(@NotNull Sound sound) {
        guard(() -> receiver.playSound(sound));
    }

    @Override
    public void playSound(@NotNull Sound sound, double x, double y, double z) {
        guard(() -> receiver.playSound(sound, x, y, z));
    }

    @Override
    public void stopSound(@NotNull SoundStop stop) {
        guard(() -> receiver.stopSound(stop));
    }

    @Override
    public void showTitle(@NotNull Title title) {
        guard(() -> receiver.showTitle(title));
    }

    @Override
    public void clearTitle() {
        guard(receiver::clearTitle);
    }

    @Override
    public void resetTitle() {
        guard(receiver::resetTitle);
    }

    @Override
    public void showBossBar(@NotNull BossBar bar) {
        guard(() -> receiver.showBossBar(bar));
    }

    @Override
    public void hideBossBar(@NotNull BossBar bar) {
        guard(() -> receiver.hideBossBar(bar));
    }

    @Override
    public void openBook(@NotNull Book book) {
        guard(() -> receiver.openBook(book));
    }

    private void guard(Runnable operation) {
        try {
            operation.run();
        } catch (Throwable error) {
            Failures.rethrowIfFatal(error);
            if (throttle.shouldReport(error.getClass().getName())) {
                report.accept(error);
            }
        }
    }
}
