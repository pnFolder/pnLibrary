package org.pnfolder.pnlibrary.api.config;

/**
 * What to do when a configuration word is already taken at the same place by another registration.
 *
 * <pre>{@code
 * scope.type(Action.class, MyMessage.class).name("message-fancy").onConflict(ConfigTypeConflict.KEEP).register();
 * }</pre>
 */
public enum ConfigTypeConflict {
    /** Refuse with an error; the default, so that a clash is never silent. */
    FAIL,

    /** Keep the existing registration and publish nothing; the returned handle is inactive. */
    KEEP,

    /**
     * Take the word over: registrations of the same plugin and ones with a lower priority are closed.
     * A registration of another plugin with the same or a higher priority is never replaced, and the call fails.
     */
    REPLACE
}
