package org.pnfolder.pnlibrary.core.link;

import java.nio.file.Path;
import java.time.Instant;
import java.util.function.Predicate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.core.link.remote.DataClient;
import org.pnfolder.pnlibrary.core.link.remote.DataHandler;
import org.pnfolder.pnlibrary.core.link.remote.LinkTransport;
import org.pnfolder.pnlibrary.core.link.remote.RemoteControl;
import org.pnfolder.pnlibrary.core.link.remote.RemoteReplies;
import org.pnfolder.pnlibrary.core.link.report.NetworkReport;

/**
 * This server's side of the link between the proxy and its game servers.
 *
 * <p>The platform runtime makes one when it starts and hands it to the commands, menus and listeners that talk to
 * other servers. A link without a key is off: nothing is sent and nothing received.
 */
public final class Link {
    /** A link that is switched off, for places that run without one. */
    public static final Link OFF = new Link(null);

    private final byte[] key;
    private final LinkTransport transport;
    private final Predicate<byte[]> uplink;
    private final RemoteReplies replies = new RemoteReplies();
    private final LinkRegistry registry = new LinkRegistry();
    private final DataClient data;
    private final RemoteControl control;
    private final ServerGroups groups;
    private final LinkPeers peers = new LinkPeers();
    private volatile ReceivedNetwork network;
    private volatile DataHandler calls;

    public Link(@Nullable byte[] key) {
        this(key, null, null, null);
    }

    public Link(@Nullable byte[] key, @Nullable Path folder) {
        this(key, folder, null, null);
    }

    public Link(@Nullable byte[] key, @Nullable Path folder, @Nullable LinkTransport transport) {
        this(key, folder, transport, null);
    }

    /**
     * @param key the shared {@code link.key}, or null when the file is missing
     * @param folder the pnLibrary folder of the proxy, where {@code groups.tsv} lives; null on a game server
     * @param transport on the proxy: sends to a game server by name
     * @param uplink on a game server: sends to the proxy through any player online, false when nobody is online
     */
    public Link(@Nullable byte[] key, @Nullable Path folder, @Nullable LinkTransport transport,
                @Nullable Predicate<byte[]> uplink) {
        this.key = key;
        this.transport = transport;
        this.uplink = uplink;
        this.data = new DataClient(key, bytes -> uplink != null && uplink.test(bytes));
        this.control = new RemoteControl(this);
        this.groups = new ServerGroups(() -> folder == null ? null : folder.resolve(ServerGroups.FILE));
    }

    public @Nullable byte[] getKey() {
        return key;
    }

    /** Whether the link has a key; without one nothing is sent or received. */
    public boolean getEnabled() {
        return key != null;
    }

    /** Whether this link is the proxy's end, which can send to servers by name. */
    public boolean getOnProxy() {
        return transport != null;
    }

    /** On the proxy: the game servers heard from since it started. */
    public @NotNull LinkRegistry getRegistry() {
        return registry;
    }

    /** The remote requests this end made and has no answer to yet. */
    public @NotNull RemoteReplies getReplies() {
        return replies;
    }

    /** The data questions this end asked and has no answer to yet. */
    public @NotNull DataClient getData() {
        return data;
    }

    /** Sends update controls to other servers. */
    public @NotNull RemoteControl getControl() {
        return control;
    }

    /** On a game server: the latest network map from the proxy, or null until one arrives. */
    public @Nullable ReceivedNetwork getNetwork() {
        return network;
    }

    /** On a game server: what answers the menu questions of other servers, once the menu is ready. */
    public @Nullable DataHandler getCalls() {
        return calls;
    }

    /** Keeps the latest network map from the proxy, as received now. */
    public void remember(@NotNull NetworkReport report) {
        remember(report, Instant.now());
    }

    /** Keeps the latest network map from the proxy, as received at {@code now}. */
    public void remember(@NotNull NetworkReport report, @NotNull Instant now) {
        network = new ReceivedNetwork(report, now);
    }

    /** Sets what answers the data questions other servers ask of this one. */
    public void answerCallsWith(@NotNull DataHandler handler) {
        calls = handler;
    }

    /** The other ends that said hello, and whether their message formats match this one. */
    public @NotNull LinkPeers getPeers() {
        return peers;
    }

    /** The groups of the proxy; one instance, so its changes do not overwrite each other. */
    public @NotNull ServerGroups groups() {
        return groups;
    }

    /** Sends {@code message} to {@code target} from the proxy, or to the proxy from a game server. */
    public boolean send(@NotNull String target, @NotNull byte[] message) {
        if (transport != null) {
            return transport.send(target, message);
        }
        return uplink != null && uplink.test(message);
    }
}
