package org.pnfolder.pnlibrary.api.placeholders.model

import org.pnfolder.pnlibrary.api.placeholders.adapter.PlaceholderAdapterState

/** Handle tracking one publication in an external placeholder system. */
interface ExternalPlaceholderRegistration : AutoCloseable {
    /** Current publication state, including unavailable and failed attempts. */
    val state: PlaceholderAdapterState
    /** Whether this publication has been permanently closed. */
    val isClosed: Boolean get() = state == PlaceholderAdapterState.CLOSED
}
