package org.pnfolder.pnlibrary.api.audiences

/** Outcome of asking a platform to present something to a player or an audience. */
sealed interface Delivery {
    /** `true` only for [Delivered]. */
    val isDelivered: Boolean get() = this === Delivered

    /** The platform accepted the request, for every receiver of an audience. */
    data object Delivered : Delivery

    /** The platform has no way to do this, or the audience had no receivers. */
    data object Unsupported : Delivery

    /** The platform could do this, but the attempt failed with [cause]. */
    data class Failed(val cause: Throwable) : Delivery

    companion object {
        /** Maps a platform answer that only says accepted or not to [Delivered] or [Unsupported]. */
        @JvmStatic
        fun of(accepted: Boolean): Delivery = if (accepted) Delivered else Unsupported

        /**
         * Merges per-receiver outcomes: [Delivered] when every receiver got it, otherwise the first [Failed],
         * otherwise [Unsupported]. No receivers is [Unsupported].
         */
        @JvmStatic
        fun combine(results: Iterable<Delivery>): Delivery {
            val all = results.toList()
            return when {
                all.isEmpty() -> Unsupported
                all.all(Delivery::isDelivered) -> Delivered
                else -> all.firstOrNull { it is Failed } ?: Unsupported
            }
        }
    }
}
