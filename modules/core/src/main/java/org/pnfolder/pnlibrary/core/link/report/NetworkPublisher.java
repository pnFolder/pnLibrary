package org.pnfolder.pnlibrary.core.link.report;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.runtime.PnLibrary;
import org.pnfolder.pnlibrary.core.link.Link;
import org.pnfolder.pnlibrary.core.link.LinkEntry;
import org.pnfolder.pnlibrary.core.link.wire.LinkCodec;

/** The proxy end that builds the signed network map sent to every game server. */
public final class NetworkPublisher {
    private static final Duration ONLINE = Duration.ofMinutes(2);

    private final PnLibrary library;
    private final Link link;
    private final Supplier<String> platform;
    private final IntSupplier players;
    private final byte[] key;
    private final Function<String, String> addresses;

    /**
     * @param library the proxy's library
     * @param link the proxy's link; it must have a key
     * @param platform names the proxy software and version
     * @param players counts the players on the proxy
     */
    public NetworkPublisher(@NotNull PnLibrary library, @NotNull Link link, @NotNull Supplier<String> platform,
                            @NotNull IntSupplier players) {
        this(library, link, platform, players, name -> null);
    }

    /** As above; {@code addresses} gives where the game server of that name can be reached, or null. */
    public NetworkPublisher(@NotNull PnLibrary library, @NotNull Link link, @NotNull Supplier<String> platform,
                            @NotNull IntSupplier players, @NotNull Function<String, String> addresses) {
        this.addresses = addresses;
        if (link.getKey() == null) {
            throw new IllegalArgumentException("The network map is signed with the link key");
        }
        this.library = library;
        this.link = link;
        this.platform = platform;
        this.players = players;
        this.key = link.getKey();
    }

    /** The message to send now; the same bytes go to every server. */
    public @NotNull byte[] message() {
        return message(Instant.now());
    }

    /** The message to send at {@code now}; the same bytes go to every server. */
    public @NotNull byte[] message(@NotNull Instant now) {
        List<NetworkServer> servers = new ArrayList<>();
        for (LinkEntry entry : link.getRegistry().all()) {
            servers.add(new NetworkServer(entry.getName(), Duration.between(entry.getReceivedAt(), now).compareTo(ONLINE) < 0,
                entry.getReport().withAddress(addresses.apply(entry.getName()))));
        }
        LinkReport proxy = LinkReports.build(library, platform.get(), players.getAsInt(), now);
        return LinkCodec.encodeNetwork(new NetworkReport(proxy, servers, now, link.groups().all()), key);
    }
}
