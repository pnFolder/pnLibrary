package org.pnfolder.pnlibrary.core.runtime;

import java.util.Objects;
import java.time.Duration;
import java.util.function.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.audience.AudienceExtensions;
import org.pnfolder.pnlibrary.api.network.NetworkProxy;
import org.pnfolder.pnlibrary.api.network.NetworkServer;
import org.pnfolder.pnlibrary.common.lang.Failures;
import org.pnfolder.pnlibrary.core.exceptions.ResourceCleanup;
import org.pnfolder.pnlibrary.core.link.LinkReceiver;
import org.pnfolder.pnlibrary.core.link.wire.LinkCodec;
import org.pnfolder.pnlibrary.core.menu.OwnerInfo;
import org.pnfolder.pnlibrary.core.proxy.ProxyLink;
import org.pnfolder.pnlibrary.core.updates.catalog.proxy.ProxyUpdateReminder;
import org.pnfolder.pnlibrary.spi.platform.ProxyPlatformAdapter;
import org.pnfolder.pnlibrary.spi.tasks.PlatformTaskHandle;

/**
 * Shared runtime logic for all proxy platforms (Velocity, BungeeCord).
 *
 * <p>Uses the platform adapter for scheduling and proxy access, plus two platform-specific hooks, the link receiver
 * registration and the owner description, that cannot go through the adapter because their types live in core rather than spi.
 */
public final class ProxyRuntime implements AutoCloseable {
    private final ProxyPlatformAdapter adapter;
    private final Function<LinkReceiver, AutoCloseable> registerLinkReceiver;
    private final Function<Object, OwnerInfo> describe;
    private ProxyLink link;
    private AutoCloseable linkListener;
    private PlatformTaskHandle networkTask;
    private PlatformTaskHandle reminderTask;

    public ProxyRuntime(@NotNull ProxyPlatformAdapter adapter, @NotNull Function<LinkReceiver, AutoCloseable> registerLinkReceiver,
                        @NotNull Function<Object, OwnerInfo> describe) {
        this.adapter = Objects.requireNonNull(adapter, "adapter");
        this.registerLinkReceiver = Objects.requireNonNull(registerLinkReceiver, "registerLinkReceiver");
        this.describe = Objects.requireNonNull(describe, "describe");
    }

    private NetworkProxy proxy() {
        return adapter.getNetworkProxy();
    }

    /** Connects to the game servers and starts the periodic tasks; {@link #close} undoes whatever got started. */
    public void start(@NotNull PnLibraryRuntimeHost host) {
        connectLink(host);
        startUpdateReminder(host);
    }

    private void connectLink(PnLibraryRuntimeHost host) {
        ProxyLink proxyLink = createLink(host);
        proxy().registerChannel(LinkCodec.CHANNEL);
        linkListener = registerLinkReceiver.apply(proxyLink.getReceiver());
        link = proxyLink;
        if (!proxyLink.getAnnounces()) {
            return;
        }
        networkTask = adapter.getTaskAdapter().buildTask()
                .task(proxyLink::announce)
                .delay(Duration.ofSeconds(ProxyLink.NETWORK_DELAY_SECONDS))
                .every(Duration.ofSeconds(ProxyLink.NETWORK_PERIOD_SECONDS))
                .schedule();
    }

    private ProxyLink createLink(PnLibraryRuntimeHost host) {
        return new ProxyLink(host.getLibrary(), adapter.getDataFolder(), adapter::warn,
                (name, bytes) -> {
                    NetworkServer server = proxy().server(name);
                    return server != null && server.send(LinkCodec.CHANNEL, bytes);
                },
                describe,
                (viewer, permission) -> {
                    org.pnfolder.pnlibrary.api.audience.OnlinePlayer player = proxy().player(viewer);
                    return player != null && AudienceExtensions.hasPermission(player, permission);
                },
                () -> proxy().getName() + " " + proxy().getVersion(),
                () -> proxy().getPlayerCount(),
                bytes -> {
                    for (NetworkServer server : proxy().getServers()) {
                        Failures.runCatchingNonFatal(() -> server.send(LinkCodec.CHANNEL, bytes));
                    }
                },
                name -> {
                    NetworkServer server = proxy().server(name);
                    return server == null ? null : server.getAddress().getHostString() + ":" + server.getAddress().getPort();
                });
    }

    private void startUpdateReminder(PnLibraryRuntimeHost host) {
        Duration period = Duration.ofSeconds(ProxyUpdateReminder.INTERVAL_SECONDS);
        reminderTask = adapter.getTaskAdapter().buildTask()
                .task(() -> {
                    for (org.pnfolder.pnlibrary.api.audience.OnlinePlayer player : proxy().getPlayers()) {
                        if (ProxyUpdateReminder.canSee(permission -> AudienceExtensions.hasPermission(player, permission))) {
                            ProxyUpdateReminder.send(host.getLibrary(), player.getUniqueId());
                        }
                    }
                })
                .delay(period)
                .every(period)
                .schedule();
    }

    /** Stops scheduled tasks, unregisters the link listener and channel, then closes the link. */
    @Override
    public void close() {
        final PlatformTaskHandle network = networkTask;
        final PlatformTaskHandle reminder = reminderTask;
        final AutoCloseable listener = linkListener;
        final ProxyLink proxyLink = link;
        networkTask = null;
        reminderTask = null;
        linkListener = null;
        link = null;
        ResourceCleanup.closeAll(
                () -> cancel(network),
                () -> cancel(reminder),
                () -> {
                    if (listener != null) {
                        listener.close();
                    }
                },
                () -> proxy().unregisterChannel(LinkCodec.CHANNEL),
                () -> {
                    if (proxyLink != null) {
                        proxyLink.close();
                    }
                });
    }

    private static void cancel(@Nullable PlatformTaskHandle task) {
        if (task != null) {
            task.cancel();
        }
    }
}
