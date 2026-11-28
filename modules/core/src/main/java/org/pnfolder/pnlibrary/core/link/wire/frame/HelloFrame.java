package org.pnfolder.pnlibrary.core.link.wire.frame;

import org.pnfolder.pnlibrary.core.link.wire.LinkHello;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

/** A hello from a game server. */
public final class HelloFrame implements LinkFrame {
    private final LinkHello hello;

    public HelloFrame(@NotNull LinkHello hello) {
        this.hello = Objects.requireNonNull(hello, "hello");
    }

    public @NotNull LinkHello getHello() {
        return hello;
    }
}
