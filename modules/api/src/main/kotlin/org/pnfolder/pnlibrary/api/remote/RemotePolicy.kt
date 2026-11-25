package org.pnfolder.pnlibrary.api.remote


/** Cross-platform contract implemented by a downloaded Java policy. */
fun interface RemotePolicy {
    /** Evaluates [context] and returns the resulting allow/deny decision. */
    @Throws(Exception::class)
    fun check(context: RemotePolicyContext): RemotePolicyResult
}
