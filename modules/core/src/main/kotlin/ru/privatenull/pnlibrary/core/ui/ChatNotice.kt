package ru.privatenull.pnlibrary.core.ui

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
 * @property detail an optional second line, such as the reason of a failure
 */
data class ChatNotice(val tone: ChatTone, val text: String, val detail: String? = null)
