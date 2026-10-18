package ru.privatenull.pnlibrary.core.updates

/** What a short update message reports, which decides its mark and color. */
enum class UpdateTone {
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
 * One short message of an update command, rendered by [UpdateCatalogView.notice].
 *
 * @property detail an optional second line, such as the reason of a failure
 */
data class UpdateNotice(val tone: UpdateTone, val text: String, val detail: String? = null)
