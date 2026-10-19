package org.pnfolder.pnlibrary.core.updates.catalog

import net.kyori.adventure.text.format.TextColor
import org.pnfolder.pnlibrary.api.updates.UpdateChannel
import org.pnfolder.pnlibrary.core.ui.ChatPalette
import java.util.Locale

/** The words and colors of update channels, shared by the chat catalog and every console card. */
object UpdateChannelLabels {
    /** The channel as it is typed in commands and shown in tags: `stable`, `rc`, `beta`. */
    @JvmStatic
    fun channelName(channel: UpdateChannel): String = channel.name.lowercase(Locale.ROOT)

    /** Channels are colored by risk, from green for stable to red for development builds. */
    @JvmStatic
    fun channelColor(channel: UpdateChannel): TextColor = when (channel) {
        UpdateChannel.STABLE -> ChatPalette.OK
        UpdateChannel.RC -> ChatPalette.INFO
        UpdateChannel.BETA -> ChatPalette.WARN
        UpdateChannel.ALPHA -> ChatPalette.ORANGE
        UpdateChannel.DEV -> ChatPalette.DANGER
    }

    /** What a server on this channel receives. */
    @JvmStatic
    fun channelHint(channel: UpdateChannel): String = when (channel) {
        UpdateChannel.STABLE -> "Только стабильные релизы"
        UpdateChannel.RC -> "Кандидаты в релиз и стабильные"
        UpdateChannel.BETA -> "Бета-версии и стабильные"
        UpdateChannel.ALPHA -> "Экспериментальные alpha-версии: возможны ошибки"
        UpdateChannel.DEV -> "Все сборки, включая разрабатываемые: только для тестового сервера"
    }

    /** The channel in a sentence, such as `тестовый канал Beta`. */
    @JvmStatic
    fun channelTitle(channel: UpdateChannel): String = when (channel) {
        UpdateChannel.STABLE -> "стабильный канал"
        UpdateChannel.RC -> "канал RC"
        UpdateChannel.BETA -> "тестовый канал Beta"
        UpdateChannel.ALPHA -> "экспериментальный канал Alpha"
        UpdateChannel.DEV -> "разрабатываемый канал Dev"
    }
}
