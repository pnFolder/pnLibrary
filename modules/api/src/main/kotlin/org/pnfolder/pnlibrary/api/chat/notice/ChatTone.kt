package org.pnfolder.pnlibrary.api.chat.notice

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
