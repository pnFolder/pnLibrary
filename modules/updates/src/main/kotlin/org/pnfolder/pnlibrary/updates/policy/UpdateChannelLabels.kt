package org.pnfolder.pnlibrary.updates.policy

import net.kyori.adventure.text.format.TextColor
import org.pnfolder.pnlibrary.api.chat.ChatPalette
import org.pnfolder.pnlibrary.api.updates.UpdateChannel
import java.util.Locale

/** The channel as it is typed in commands and shown in tags: `stable`, `rc`, `beta`. */
fun UpdateChannel.commandName(): String = name.lowercase(Locale.ROOT)

/** Channels are colored by risk, from green for stable to red for development builds. */
fun UpdateChannel.color(): TextColor = when (this) {
    UpdateChannel.STABLE -> ChatPalette.OK
    UpdateChannel.RC -> ChatPalette.INFO
    UpdateChannel.BETA -> ChatPalette.WARN
    UpdateChannel.ALPHA -> ChatPalette.ORANGE
    UpdateChannel.DEV -> ChatPalette.DANGER
}

/** What a server on this channel receives. */
fun UpdateChannel.hint(): String = when (this) {
    UpdateChannel.STABLE -> "Только стабильные релизы"
    UpdateChannel.RC -> "Кандидаты в релиз и стабильные"
    UpdateChannel.BETA -> "Бета-версии и стабильные"
    UpdateChannel.ALPHA -> "Экспериментальные alpha-версии: возможны ошибки"
    UpdateChannel.DEV -> "Все сборки, включая разрабатываемые: только для тестового сервера"
}

/** The channel in a sentence, such as `тестовый канал Beta`. */
fun UpdateChannel.title(): String = when (this) {
    UpdateChannel.STABLE -> "стабильный канал"
    UpdateChannel.RC -> "канал RC"
    UpdateChannel.BETA -> "тестовый канал Beta"
    UpdateChannel.ALPHA -> "экспериментальный канал Alpha"
    UpdateChannel.DEV -> "разрабатываемый канал Dev"
}
