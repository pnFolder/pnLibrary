package org.pnfolder.pnlibrary.api.placeholders.reply

import java.util.UUID

/** How a published prefix is answered: the text after the prefix goes in, the value (or `null`) comes out. */
fun interface PublishedAnswers {
    /** [params] is the text of the outside request without the prefix; [waitMillis] is how long to wait for a late value (0: not at all). */
    fun answer(playerId: UUID?, params: String, waitMillis: Long): String?
}
