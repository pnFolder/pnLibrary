package org.pnfolder.pnlibrary.spi.audience;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.audience.ForwardingAudience;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.audience.Console;

/** The console of a platform: {@code audience}, the native console as an Adventure audience, seen as a {@link Console}. */
public final class PlatformConsole implements Console, ForwardingAudience.Single {
    private final Audience audience;

    public PlatformConsole(@NotNull Audience audience) {
        this.audience = audience;
    }

    @NotNull
    @Override
    public Audience audience() {
        return audience;
    }
}
