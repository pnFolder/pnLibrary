package org.pnfolder.pnlibrary.api.chat

import net.kyori.adventure.text.format.TextColor

/** What a short message reports, which decides its mark and color. */
enum class ChatTone {
    /** Work has started or is still running. */
    PROGRESS,

    /** A neutral fact, such as an available update. */
    INFO,

    /** The requested work is done. */
    SUCCESS,

    /** Nothing failed, but the reader should act or wait. */
    WARNING,

    /** The requested work failed or was refused. */
    FAILURE,
}

/**
 * One short message of a command, rendered by [ChatKit.notice].
 *
 * @property tone visual weight of the message, such as success or failure
 * @property text the main line of the message
 * @property detail an optional second line, such as the reason of a failure
 */
data class ChatNotice(val tone: ChatTone, val text: String, val detail: String? = null)

/** The color of this tone: lavender progress, aqua facts, green success, yellow warnings and red failures. */
val ChatTone.color: TextColor
    get() = when (this) {
        ChatTone.PROGRESS -> ChatPalette.ACCENT
        ChatTone.INFO -> ChatPalette.INFO
        ChatTone.SUCCESS -> ChatPalette.OK
        ChatTone.WARNING -> ChatPalette.WARN
        ChatTone.FAILURE -> ChatPalette.DANGER
    }
