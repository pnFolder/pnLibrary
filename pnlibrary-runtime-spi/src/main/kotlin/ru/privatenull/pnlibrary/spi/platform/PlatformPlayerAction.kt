package ru.privatenull.pnlibrary.spi.platform

import net.kyori.adventure.text.Component
import ru.privatenull.pnlibrary.api.actions.PlayerAction

/** Fully rendered action crossing from the shared core into a native platform. */
data class PlatformPlayerAction(
    val source: PlayerAction,
    val text: Component? = null,
    val title: Component? = null,
    val subtitle: Component? = null,
)
