package ru.privatenull.pnlibrary.api.updates

/** Lifecycle and manual controls for one updater registration. */
interface UpdateRegistration : AutoCloseable {

    /** Whether this registration has been removed and rejects new manual work. */
    val isClosed: Boolean get() = false

    /** Repository coordinate in `owner/name` form. */
    val repository: String

    /** Latest immutable state observed by this registration. */
    val snapshot: UpdateSnapshot

    /** Starts a background check without forcing a download. */
    fun checkNow()

    /** Starts a background check with downloading enabled for this invocation. */
    fun downloadNow()

    /** Stops periodic and manual work and removes this registration. */
    override fun close()
}
