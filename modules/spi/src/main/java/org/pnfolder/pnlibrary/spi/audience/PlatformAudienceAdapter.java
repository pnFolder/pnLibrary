package org.pnfolder.pnlibrary.spi.audience;

import net.kyori.adventure.audience.Audience;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.audience.Console;
import org.pnfolder.pnlibrary.api.audience.OnlinePlayer;

import java.util.List;
import java.util.UUID;

/** Native audience lookup boundary implemented by each platform runtime. */
public interface PlatformAudienceAdapter {
    /** Returns the native platform's console audience. */
    @NotNull
    Console console();

    /** Resolves an online player by {@code uniqueId}. */
    @Nullable
    OnlinePlayer player(@NotNull UUID uniqueId);

    /** Adapts a platform-native sender object. */
    @Nullable
    Audience of(@NotNull Object nativeSender);

    /** Returns a snapshot of players currently online. */
    @NotNull
    List<OnlinePlayer> players();
}
