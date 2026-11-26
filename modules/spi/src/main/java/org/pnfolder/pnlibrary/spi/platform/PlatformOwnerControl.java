package org.pnfolder.pnlibrary.spi.platform;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.remote.context.RemotePolicyContext;

import java.util.Map;

/** Native owner control operations used by remote policy enforcement. */
public interface PlatformOwnerControl {
    /** Builds policy input from the registered native owner and caller-supplied values. */
    @NotNull
    default RemotePolicyContext remotePolicyContext(@NotNull Object owner, @NotNull Map<String, String> values) {
        throw new UnsupportedOperationException("Remote policy is unavailable");
    }

    /** Disables the native plugin {@code owner}; returns {@code false} when the platform cannot do it. */
    default boolean disableOwner(@NotNull Object owner) {
        return false;
    }

    /**
     * Stops plugins that depend on {@code owner} from running while {@code owner} itself is being switched off, including those
     * the server has not enabled yet, and switches {@code owner} off once the server has started.
     * Returns {@code false} when the platform cannot do it; the caller then disables {@code owner} directly.
     */
    default boolean disableDependents(@NotNull Object owner) {
        return false;
    }
}
