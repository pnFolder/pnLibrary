package org.pnfolder.pnlibrary.spi.audience;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.pointer.Pointers;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.audience.AudienceExtensions;
import org.pnfolder.pnlibrary.api.audience.Console;
import org.pnfolder.pnlibrary.api.audience.OnlinePlayer;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** No-op audience adapter used by runtimes without native audience support. */
public final class UnsupportedPlatformAudienceAdapter implements PlatformAudienceAdapter {
    /** The only instance. */
    public static final UnsupportedPlatformAudienceAdapter INSTANCE = new UnsupportedPlatformAudienceAdapter();

    private final Console console = new PlatformConsole(new Audience() {
        @Override
        public @NotNull Pointers pointers() {
            return AudienceExtensions.senderPointers("Console", null, permission -> false);
        }
    });

    private UnsupportedPlatformAudienceAdapter() {
    }

    /** Returns a console that shows nothing and grants nothing. */
    @NotNull
    @Override
    public Console console() {
        return console;
    }

    /** Always returns {@code null}. */
    @Nullable
    @Override
    public OnlinePlayer player(@NotNull UUID uniqueId) {
        return null;
    }

    /** Always returns {@code null}. */
    @Nullable
    @Override
    public Audience of(@NotNull Object nativeSender) {
        return null;
    }

    /** Always returns an empty list. */
    @NotNull
    @Override
    public List<OnlinePlayer> players() {
        return Collections.emptyList();
    }
}
