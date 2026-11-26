package org.pnfolder.pnlibrary.velocity.api;

import com.velocitypowered.api.proxy.ProxyServer;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/** Native Velocity API exposed by pnLibrary on Velocity proxies. */
@ApiStatus.NonExtendable
public interface VelocityPlatform {

    /** Returns the active native proxy; its registered servers and players remain directly available. */
    @NotNull
    ProxyServer getProxy();
}
