package org.pnfolder.pnlibrary.core.runtime;

import org.jetbrains.annotations.NotNull;

/** What differs between proxy platforms in the start of the library; {@link ProxyLifecycle} runs these steps in order. */
public interface ProxyStartup {
    /** Builds the platform adapter and launches the library on it. */
    @NotNull PnLibraryRuntimeHost launch();

    /** Creates the proxy side (the link to the game servers); the lifecycle starts it. */
    @NotNull ProxyRuntime createProxy();

    /** Registers what the library listens to on the proxy; closing the result takes it all back. */
    @NotNull AutoCloseable listen(@NotNull PnLibraryRuntimeHost host);
}
