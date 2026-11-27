package org.pnfolder.pnlibrary.velocity.network;

import com.velocitypowered.api.proxy.ConnectionRequestBuilder;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.network.ConnectResult;
import org.pnfolder.pnlibrary.api.network.NetworkServer;

/** Translation between the portable and the native view of Velocity servers and move results. */
final class VelocityNetworkServers {
    private VelocityNetworkServers() {
    }

    /** The native server behind {@code server}, or {@code null} for a server of another platform. */
    @Nullable
    static RegisteredServer nativeOf(@NotNull NetworkServer server) {
        return server instanceof VelocityNetworkServer ? ((VelocityNetworkServer) server).getNativeServer() : null;
    }

    @NotNull
    static ConnectResult resultOf(@NotNull ConnectionRequestBuilder.Status status) {
        return switch (status) {
            case SUCCESS -> ConnectResult.CONNECTED;
            case ALREADY_CONNECTED -> ConnectResult.ALREADY_THERE;
            case CONNECTION_IN_PROGRESS -> ConnectResult.BUSY;
            case CONNECTION_CANCELLED -> ConnectResult.DENIED;
            default -> ConnectResult.FAILED;
        };
    }
}
