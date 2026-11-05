package org.pnfolder.pnlibrary.core.link

import java.time.Instant
import java.util.UUID

/**
 * A question one server puts to another about its library, or a thing it asks it to do.
 *
 * @property id identifies the question and its answer
 * @property target the server that answers, or [LinkNames.PROXY]
 * @property origin the server that asks; the proxy fills it in from the connection the question came on
 * @property viewer the name of the person on whose behalf it is asked, for the update history
 * @property args the arguments, as JSON
 */
data class DataCall(
    val id: UUID,
    val target: String,
    val origin: String,
    val viewer: String,
    val method: String,
    val args: String,
    val sentAt: Instant,
)

/**
 * One piece of the answer to a [DataCall]. An answer can be longer than a plugin message, so it travels in pieces.
 *
 * @property to the server that asked
 * @property ok whether the answer is the data, or the reason there is none
 * @property index the place of this piece, from 0
 * @property bytes the compressed text of this piece
 */
class DataChunk(
    val id: UUID,
    val to: String,
    val ok: Boolean,
    val index: Int,
    val count: Int,
    val bytes: ByteArray,
    val sentAt: Instant,
)

/** Answers the questions other servers ask of this one. */
fun interface DataHandler {
    /** Answers [call]; [reply] takes whether it worked and the answer, and may be called from any thread. */
    fun handle(call: DataCall, reply: (Boolean, String) -> Unit)
}

class DataException(message: String) : RuntimeException(message)
