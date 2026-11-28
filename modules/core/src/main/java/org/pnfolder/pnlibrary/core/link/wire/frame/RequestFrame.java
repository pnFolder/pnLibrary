package org.pnfolder.pnlibrary.core.link.wire.frame;

import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.core.link.remote.RemoteRequest;

/** A remote update request for another server or the proxy. */
public final class RequestFrame implements LinkFrame {
    private final RemoteRequest request;

    public RequestFrame(@NotNull RemoteRequest request) {
        this.request = Objects.requireNonNull(request, "request");
    }

    public @NotNull RemoteRequest getRequest() {
        return request;
    }
}
