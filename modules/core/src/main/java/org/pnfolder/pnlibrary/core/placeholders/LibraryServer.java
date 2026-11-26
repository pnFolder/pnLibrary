package org.pnfolder.pnlibrary.core.placeholders;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.placeholders.annotation.Placeholder;
import org.pnfolder.pnlibrary.api.placeholders.reply.Reply;
import org.pnfolder.pnlibrary.spi.platform.PlatformIdentity;

/** What the server is: {@code {pnlibrary.server.platform}}. */
public final class LibraryServer {
    private final PlatformIdentity platform;

    public LibraryServer(@NotNull PlatformIdentity platform) {
        this.platform = platform;
    }

    @Placeholder("Platform name")
    public Reply<String> platform() {
        return Reply.of(platform.getType().getDisplayName());
    }

    @Placeholder("Server software")
    public Reply<String> implementation() {
        return Reply.of(platform.getImplementationName());
    }

    @Placeholder("Whether this is a proxy")
    public Reply<Boolean> proxy() {
        return Reply.of(platform.isProxy());
    }

    @Placeholder("Java version")
    public Reply<String> java() {
        return Reply.of(System.getProperty("java.version", "unknown"));
    }
}
