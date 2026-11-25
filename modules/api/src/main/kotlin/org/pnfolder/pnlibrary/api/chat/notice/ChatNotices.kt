package org.pnfolder.pnlibrary.api.chat.notice

import org.pnfolder.pnlibrary.api.chat.ChatPalette
import net.kyori.adventure.text.format.TextColor

/** The color of this tone: lavender progress, aqua facts, green success, yellow warnings and red failures. */
val ChatTone.color: TextColor
    get() = when (this) {
        ChatTone.PROGRESS -> ChatPalette.ACCENT
        ChatTone.INFO -> ChatPalette.INFO
        ChatTone.SUCCESS -> ChatPalette.OK
        ChatTone.WARNING -> ChatPalette.WARN
        ChatTone.FAILURE -> ChatPalette.DANGER
    }
