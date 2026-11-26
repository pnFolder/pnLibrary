package org.pnfolder.pnlibrary.api.remote;

/** What happens when a policy cannot be checked at all: no network, no compiler, a broken file. */
public enum PolicyErrorAction {
    /** Treat it as a denial and apply {@link RemotePolicySettings#getOnDeny()}, at start and later. */
    DENY,

    /**
     * Deny until the policy has been checked once: a plugin that could not be checked at start does not run. After a
     * successful check a later failure, such as the network dropping for a while, only logs a warning.
     */
    DENY_UNTIL_CHECKED,

    /** Log a warning and keep running; only an explicit denial stops anything. Safe for offline servers and JREs. */
    KEEP_RUNNING
}
