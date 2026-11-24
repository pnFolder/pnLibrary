package org.pnfolder.pnlibrary.core.runtime.platform

import net.kyori.adventure.audience.Audience
import net.kyori.adventure.audience.ForwardingAudience
import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.inventory.Book
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.sound.SoundStop
import net.kyori.adventure.text.Component
import net.kyori.adventure.title.Title
import org.pnfolder.pnlibrary.common.concurrent.FailureThrottle
import org.pnfolder.pnlibrary.common.lang.Failures

/**
 * One receiver whose failures stay inside it: a group keeps sending to the others, and [report] hears about a
 * repeated failure only once in a while. Fatal errors still propagate.
 */
internal class FailureIsolatedAudience(
    private val receiver: Audience,
    private val throttle: FailureThrottle,
    private val report: (Throwable) -> Unit,
) : ForwardingAudience.Single {
    override fun audience(): Audience = receiver

    override fun sendMessage(message: Component) = guard { receiver.sendMessage(message) }

    override fun sendActionBar(message: Component) = guard { receiver.sendActionBar(message) }

    override fun sendPlayerListHeaderAndFooter(header: Component, footer: Component) =
        guard { receiver.sendPlayerListHeaderAndFooter(header, footer) }

    override fun playSound(sound: Sound) = guard { receiver.playSound(sound) }

    override fun playSound(sound: Sound, x: Double, y: Double, z: Double) = guard { receiver.playSound(sound, x, y, z) }

    override fun stopSound(stop: SoundStop) = guard { receiver.stopSound(stop) }

    override fun showTitle(title: Title) = guard { receiver.showTitle(title) }

    override fun clearTitle() = guard { receiver.clearTitle() }

    override fun resetTitle() = guard { receiver.resetTitle() }

    override fun showBossBar(bar: BossBar) = guard { receiver.showBossBar(bar) }

    override fun hideBossBar(bar: BossBar) = guard { receiver.hideBossBar(bar) }

    override fun openBook(book: Book) = guard { receiver.openBook(book) }

    private inline fun guard(operation: () -> Unit) {
        try {
            operation()
        } catch (error: Throwable) {
            Failures.rethrowIfFatal(error)
            if (throttle.shouldReport(error.javaClass.name)) report(error)
        }
    }
}
