package org.pnfolder.pnlibrary.api.remote;

import org.pnfolder.pnlibrary.api.remote.result.RemotePolicyResult;
import org.pnfolder.pnlibrary.api.remote.context.RemotePolicyContext;
import org.jetbrains.annotations.NotNull;

/** Cross-platform contract implemented by a downloaded Java policy. */
@FunctionalInterface
public interface RemotePolicy {
    /** Evaluates {@code context} and returns the resulting allow/deny decision. */
    @NotNull
    RemotePolicyResult check(@NotNull RemotePolicyContext context) throws Exception;
}
