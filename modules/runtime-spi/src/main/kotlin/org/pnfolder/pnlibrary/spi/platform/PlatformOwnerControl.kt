package org.pnfolder.pnlibrary.spi.platform

import org.pnfolder.pnlibrary.api.remote.RemotePolicyContext

/** Native owner control operations used by remote policy enforcement. */
interface PlatformOwnerControl {
    /** Builds policy input from the registered native owner and caller-supplied values. */
    fun remotePolicyContext(owner: Any, values: Map<String, String>): RemotePolicyContext =
        throw UnsupportedOperationException("Remote policy is unavailable")

    /** Disables the native plugin [owner]; returns `false` when the platform cannot do it. */
    fun disableOwner(owner: Any): Boolean = false

    /**
     * Stops plugins that depend on [owner] from running while [owner] itself is being switched off, including those
     * the server has not enabled yet, and switches [owner] off once the server has started.
     * Returns `false` when the platform cannot do it; the caller then disables [owner] directly.
     */
    fun disableDependents(owner: Any): Boolean = false
}
