package org.pnfolder.pnlibrary.core.proxy;

import java.util.Objects;
import java.nio.file.Path;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.runtime.PnLibrary;
import org.pnfolder.pnlibrary.core.commands.proxy.ProxyControlCommand;
import org.pnfolder.pnlibrary.core.link.Link;
import org.pnfolder.pnlibrary.core.link.LinkReceiver;
import org.pnfolder.pnlibrary.core.link.remote.LinkTransport;
import org.pnfolder.pnlibrary.core.link.remote.RemoteExecutor;
import org.pnfolder.pnlibrary.core.link.report.NetworkPublisher;
import org.pnfolder.pnlibrary.core.link.wire.seal.LinkSecret;
import org.pnfolder.pnlibrary.core.menu.owner.OwnerInfo;
import org.pnfolder.pnlibrary.core.menu.ProxyMenu;
import org.pnfolder.pnlibrary.core.menu.data.MenuDataServer;

/**
 * The proxy's side of the link, assembled once for every proxy platform: the link itself, {@code /pnproxy}, the menu
 * data game servers ask for, and the network map this proxy announces. A platform supplies only how bytes reach a game
 * server and how to describe a plugin.
 */
public final class ProxyLink implements AutoCloseable {
    public static final long NETWORK_DELAY_SECONDS = 10L;
    public static final long NETWORK_PERIOD_SECONDS = 30L;

    private final Consumer<byte[]> broadcast;
    private final MenuDataServer data;
    private final NetworkPublisher publisher;
    private final LinkReceiver receiver;

    /**
     * @param folder the pnLibrary folder, where {@code link.key} and {@code groups.tsv} live
     * @param warn receives a problem with {@code link.key}
     * @param describe the plugin behind a registration owner, or null
     * @param holds whether the player of that name, connected to this proxy, has the permission; menu actions asked
     * by a game server run only for such a player
     * @param platformName the proxy software and its version, shown in the network map
     * @param players how many players are online on the whole proxy
     * @param broadcast sends the network map to every game server
     * @param addresses where the game server of that name can be reached, or null; shown in important announcements
     */
    public ProxyLink(@NotNull PnLibrary library, @NotNull Path folder, @NotNull Consumer<String> warn,
                     @NotNull LinkTransport transport, @NotNull Function<Object, OwnerInfo> describe,
                     @NotNull BiPredicate<String, String> holds, @NotNull Supplier<String> platformName,
                     @NotNull IntSupplier players, @NotNull Consumer<byte[]> broadcast,
                     @NotNull Function<String, String> addresses) {
        this.broadcast = Objects.requireNonNull(broadcast, "broadcast");
        Link link = new Link(LinkSecret.readOrCreate(folder, warn), folder, transport);
        ProxyControlCommand control = new ProxyControlCommand(library, link);
        this.data = ProxyMenu.server(library, link, control, describe, holds);
        this.publisher = link.getEnabled() ? new NetworkPublisher(library, link, platformName, players, addresses) : null;
        this.receiver = new LinkReceiver(link, new RemoteExecutor(library), data, warn, library::getVersion);
        control.register();
    }

    /** Hand it the messages the platform receives from game servers. */
    public @NotNull LinkReceiver getReceiver() {
        return receiver;
    }

    /** Whether the network map is announced; false when there is no usable {@code link.key}. */
    public boolean getAnnounces() {
        return publisher != null;
    }

    /** Sends the hello and the current network map to every game server. The platform calls it every {@link #NETWORK_PERIOD_SECONDS}. */
    public void announce() {
        if (publisher != null) {
            broadcast.accept(publisher.hello());
            broadcast.accept(publisher.message());
        }
    }

    @Override
    public void close() {
        data.close();
    }
}
