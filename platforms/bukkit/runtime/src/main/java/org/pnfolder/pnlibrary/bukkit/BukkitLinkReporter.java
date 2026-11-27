package org.pnfolder.pnlibrary.bukkit;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.pnfolder.pnlibrary.api.network.channel.ServerChannel;
import org.pnfolder.pnlibrary.api.network.channel.ServerNetwork;
import org.pnfolder.pnlibrary.api.runtime.PnLibrary;
import org.pnfolder.pnlibrary.bukkit.tasks.ServerScheduler;
import org.pnfolder.pnlibrary.common.lang.Attempt;
import org.pnfolder.pnlibrary.common.lang.Failures;
import org.pnfolder.pnlibrary.core.link.Link;
import org.pnfolder.pnlibrary.core.link.remote.DataCall;
import org.pnfolder.pnlibrary.core.link.remote.DataChunk;
import org.pnfolder.pnlibrary.core.link.remote.DataHandler;
import org.pnfolder.pnlibrary.core.link.remote.RemoteExecutor;
import org.pnfolder.pnlibrary.core.link.remote.RemoteRequest;
import org.pnfolder.pnlibrary.core.link.remote.RemoteResult;
import org.pnfolder.pnlibrary.core.link.report.LinkReports;
import org.pnfolder.pnlibrary.core.link.report.NetworkReport;
import org.pnfolder.pnlibrary.core.link.wire.DataChunks;
import org.pnfolder.pnlibrary.core.link.wire.LinkCodec;
import org.pnfolder.pnlibrary.core.link.wire.LinkHello;

/**
 * Sends this server's report to the proxy over the link channel, every half minute while a player is online.
 *
 * <p>A plugin message needs a player connection to travel on, so a server with nobody online stays silent and the
 * proxy marks it as out of touch. Without the shared {@code link.key} the reporter does nothing.
 */
final class BukkitLinkReporter {
    private static final long FIRST_DELAY_TICKS = 100L;
    private static final long PERIOD_TICKS = 600L;
    private static final String PROXY = "Прокси";

    private final JavaPlugin plugin;
    private final PnLibrary library;
    private final Link link;
    private final ServerNetwork network;
    private final BukkitLink uplink;
    private final ServerScheduler scheduler;
    private final RemoteExecutor executor;
    private ServerChannel channel;
    private ServerScheduler.Scheduled task;
    private boolean reportFailing;

    BukkitLinkReporter(JavaPlugin plugin, PnLibrary library, ServerNetwork network, BukkitLink uplink,
                       ServerScheduler scheduler) {
        this.plugin = plugin;
        this.library = library;
        this.network = network;
        this.uplink = uplink;
        this.link = uplink.link();
        this.scheduler = scheduler;
        this.executor = new RemoteExecutor(library, Instant::now);
    }

    void start() {
        byte[] key = link.getKey();
        if (key == null) {
            return;
        }
        channel = network.channel(LinkCodec.CHANNEL);
        channel.listen(this::receive);
        uplink.attach(channel);
        task = scheduler.repeat(FIRST_DELAY_TICKS, PERIOD_TICKS, () -> report(key));
    }

    void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        uplink.detach();
        if (channel != null) {
            channel.close();
            channel = null;
        }
    }

    /**
     * Handles what the proxy sent: the hello, the network map, a request, a menu question, a piece of data or a
     * result. Anything not signed with the key is dropped.
     */
    private void receive(byte[] message) {
        byte[] key = link.getKey();
        if (key == null) {
            return;
        }
        Instant now = Instant.now();
        if (!handleHello(message, key, now) && !handleNetworkOrRequest(message, key, now)) {
            handleDataOrResult(message, key, now);
        }
    }

    private boolean handleHello(byte[] message, byte[] key, Instant now) {
        LinkHello hello = LinkCodec.decodeHello(message, key, now);
        if (hello == null) {
            return false;
        }
        String news = link.getPeers().meet(PROXY, hello, library.getVersion());
        if (news != null) {
            library.getLogging().logger(plugin, "Link").warning(news);
        }
        return true;
    }

    private boolean handleNetworkOrRequest(byte[] message, byte[] key, Instant now) {
        NetworkReport network = LinkCodec.decodeNetwork(message, key, now);
        if (network != null) {
            link.remember(network, now);
            return true;
        }
        RemoteRequest request = LinkCodec.decodeRequest(message, key, now);
        if (request == null) {
            return false;
        }
        executor.run(request, result -> reply(Collections.singletonList(LinkCodec.encodeResult(result, key))));
        return true;
    }

    private void handleDataOrResult(byte[] message, byte[] key, Instant now) {
        DataCall call = LinkCodec.decodeCall(message, key, now);
        if (call != null) {
            answerCall(call, key);
            return;
        }
        DataChunk chunk = LinkCodec.decodeChunk(message, key, now);
        if (chunk != null) {
            link.getData().accept(chunk);
            return;
        }
        RemoteResult result = LinkCodec.decodeResult(message, key, now);
        if (result != null) {
            link.getReplies().complete(result);
        }
    }

    private void answerCall(DataCall call, byte[] key) {
        DataHandler data = link.getCalls();
        if (data == null) {
            answer(call.getId(), call.getOrigin(), false, "Меню этого сервера не готово.", key);
        } else {
            data.handle(call, (ok, text) -> answer(call.getId(), call.getOrigin(), ok, text, key));
        }
    }

    /** Sends the answer to a question back through the proxy; it can be called from any thread. */
    private void answer(UUID id, String to, boolean ok, String text, byte[] key) {
        List<byte[]> pieces = DataChunks.split(id, to, ok, text, key, Instant.now());
        if (pieces == null) {
            pieces = DataChunks.split(id, to, false, "Ответ слишком большой.", key, Instant.now());
        }
        reply(pieces == null ? Collections.<byte[]>emptyList() : pieces);
    }

    /** Sends the messages on the next tick, from the server thread; they are dropped when nobody is online. */
    private void reply(List<byte[]> messages) {
        scheduler.nextTick(() -> {
            for (byte[] message : messages) {
                uplink.send(message);
            }
        });
    }

    private void report(byte[] key) {
        if (library.isClosed() || Bukkit.getOnlinePlayers().isEmpty()) {
            return;
        }
        String platform = Bukkit.getName() + " " + Bukkit.getBukkitVersion();
        Instant now = Instant.now();
        Attempt<Void> sent = Failures.runCatchingNonFatal(() -> {
            uplink.send(LinkCodec.encodeHello(LinkHello.current(LinkHello.SERVER, library.getVersion(), now), key));
            uplink.send(LinkCodec.encode(LinkReports.build(library, platform, Bukkit.getOnlinePlayers().size(), now), key));
            return null;
        });
        if (sent.isSuccess()) {
            reportFailing = false;
            return;
        }
        // The report repeats every half minute; the first failure is enough until one succeeds again.
        if (!reportFailing) {
            library.getLogging().logger(plugin, "Reporter")
                .error("Could not send the status report to the proxy", sent.exceptionOrNull());
        }
        reportFailing = true;
    }
}
