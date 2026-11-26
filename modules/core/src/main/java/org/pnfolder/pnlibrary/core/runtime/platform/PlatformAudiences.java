package org.pnfolder.pnlibrary.core.runtime.platform;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.pointer.Pointers;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.audience.AudienceExtensions;
import org.pnfolder.pnlibrary.api.audience.Audiences;
import org.pnfolder.pnlibrary.api.audience.Console;
import org.pnfolder.pnlibrary.api.audience.OnlinePlayer;
import org.pnfolder.pnlibrary.common.concurrent.FailureThrottle;
import org.pnfolder.pnlibrary.spi.audience.PlatformAudienceAdapter;
import org.pnfolder.pnlibrary.spi.audience.PlatformConsole;

/** Resolves consoles, players and composite audiences through the platform adapter; returns empty results once closed. */
public final class PlatformAudiences implements Audiences, AutoCloseable {
    private static final Console CLOSED_CONSOLE = new PlatformConsole(new Audience() {
        @Override
        public @NotNull Pointers pointers() {
            return AudienceExtensions.senderPointers("Console", permission -> false);
        }
    });

    private final PlatformAudienceAdapter adapter;
    private final Consumer<Throwable> onDeliveryFailure;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final FailureThrottle failureThrottle = new FailureThrottle();

    public PlatformAudiences(@NotNull PlatformAudienceAdapter adapter) {
        this(adapter, failure -> { });
    }

    public PlatformAudiences(@NotNull PlatformAudienceAdapter adapter, @NotNull Consumer<Throwable> onDeliveryFailure) {
        this.adapter = adapter;
        this.onDeliveryFailure = onDeliveryFailure;
    }

    @Override
    public @NotNull Console console() {
        return closed.get() ? CLOSED_CONSOLE : adapter.console();
    }

    @Override
    public @Nullable OnlinePlayer player(@NotNull UUID uniqueId) {
        return closed.get() ? null : adapter.player(uniqueId);
    }

    @Override
    public @Nullable Audience of(@NotNull Object nativeSender) {
        return closed.get() ? null : adapter.of(nativeSender);
    }

    @Override
    public @NotNull List<OnlinePlayer> players() {
        return closed.get() ? Collections.<OnlinePlayer>emptyList() : new ArrayList<OnlinePlayer>(adapter.players());
    }

    @Override
    public void close() {
        closed.set(true);
    }

    @Override
    public @NotNull Iterable<Audience> audiences() {
        Set<Audience> seen = Collections.newSetFromMap(new IdentityHashMap<Audience, Boolean>());
        List<Audience> members = new ArrayList<Audience>();
        members.add(console());
        members.addAll(players());
        List<Audience> isolated = new ArrayList<Audience>();
        for (Audience member : members) {
            if (seen.add(member)) {
                isolated.add(new FailureIsolatedAudience(member, failureThrottle, onDeliveryFailure));
            }
        }
        return isolated;
    }
}
