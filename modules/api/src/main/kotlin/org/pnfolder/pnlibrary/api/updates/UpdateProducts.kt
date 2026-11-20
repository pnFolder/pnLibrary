package org.pnfolder.pnlibrary.api.updates

/** The products registered for updates on this server. */
interface UpdateProducts {
    /** An immutable copy of the current registrations. */
    fun all(): List<UpdateRegistration>

    /** Finds a registration by product or repository name, ignoring case. */
    fun get(product: String): UpdateRegistration?

    /** Finds a registration or fails naming the product that is missing. */
    fun require(product: String): UpdateRegistration =
        get(product) ?: error("Update registration '$product' is unavailable")
}
