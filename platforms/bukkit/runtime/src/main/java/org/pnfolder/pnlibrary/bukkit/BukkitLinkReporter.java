package org.pnfolder.pnlibrary.bukkit;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
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

/**
 * Sends this server's report to the proxy over the link channel, every half minute while a player is online.
 *
 * <p>A plugin message needs a player connection to travel on, so a server with nobody online stays silent and the
 * proxy marks it as out of touch. Without the shared {@code link.key} the reporter does nothing.
 */
final class BukkitLinkReporter implements PluginMessageListener {
    private static final long FIRST_DELAY_TICKS = 100L;
    private static final long PERIOD_TICKS = 600L;

    private final JavaPlugin plugin;
    private final PnLibrary library;
    private final Link link;
    private final ServerScheduler scheduler;
    private final RemoteExecutor executor;
    private ServerScheduler.Scheduled task;
    private boolean reportFailing;

    BukkitLinkReporter(JavaPlugin plugin, PnLibrary library, Link link, ServerScheduler scheduler) {
        this.plugin = plugin;
        this.library = library;
        this.link = link;
        this.scheduler = scheduler;
        this.executor = new RemoteExecutor(library, Instant::now);
    }

    void start() {
        byte[] key = link.getKey();
        if (key == null) {
            return;
        }
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, LinkCodec.CHANNEL, this);
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, LinkCodec.CHANNEL);
        task = scheduler.repeat(FIRST_DELAY_TICKS, PERIOD_TICKS, () -> report(key));
    }

    void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        Failures.runCatchingNonFatal(() -> {
            plugin.getServer().getMessenger().unregisterIncomingPluginChannel(plugin, LinkCodec.CHANNEL);
            return null;
        });
        Failures.runCatchingNonFatal(() -> {
            plugin.getServer().getMessenger().unregisterOutgoingPluginChannel(plugin, LinkCodec.CHANNEL);
            return null;
        });
    }

    /**
     * Handles what the proxy sent: the network map, a request, a menu question, a piece of data or a result. Anything
     * not signed with the key is dropped.
     */
    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!channel.equals(LinkCodec.CHANNEL) || link.getKey() == null) {
            return;
        }
        byte[] key = link.getKey();
        Instant now = Instant.now();
        if (!handleNetworkOrRequest(player, message, key, now)) {
            handleDataOrResult(player, message, key, now);
        }
    }

    private boolean handleNetworkOrRequest(Player player, byte[] message, byte[] key, Instant now) {
        NetworkReport network = LinkCodec.decodeNetwork(message, key, now);
        if (network != null) {
            link.remember(network, now);
            return true;
        }
        RemoteRequest request = LinkCodec.decodeRequest(message, key, now);
        if (request == null) {
            return false;
        }
        executor.run(request, result -> sendResult(player, result, key));
        return true;
    }

    private void handleDataOrResult(Player player, byte[] message, byte[] key, Instant now) {
        DataCall call = LinkCodec.decodeCall(message, key, now);
        if (call != null) {
            answerCall(player, call, key);
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

    private void sendResult(Player carrier, RemoteResult result, byte[] key) {
        scheduler.nextTick(() -> {
            Player sender = carrier.isOnline() ? carrier : firstOnline();
            if (sender != null) {
                sender.sendPluginMessage(plugin, LinkCodec.CHANNEL, LinkCodec.encodeResult(result, key));
            }
        });
    }

    private void answerCall(Player player, DataCall call, byte[] key) {
        DataHandler data = link.getCalls();
        if (data == null) {
            answer(player, call.getId(), call.getOrigin(), false, "Меню этого сервера не готово.", key);
        } else {
            data.handle(call, (ok, text) -> answer(player, call.getId(), call.getOrigin(), ok, text, key));
        }
    }

    /** Sends the answer to a question back through the proxy; it can be called from any thread. */
    private void answer(Player carrier, UUID id, String to, boolean ok, String text, byte[] key) {
        List<byte[]> pieces = DataChunks.split(id, to, ok, text, key, Instant.now());
        if (pieces == null) {
            pieces = DataChunks.split(id, to, false, "Ответ слишком большой.", key, Instant.now());
        }
        List<byte[]> toSend = pieces == null ? new ArrayList<>() : pieces;
        scheduler.nextTick(() -> {
            Player sender = carrier.isOnline() ? carrier : firstOnline();
            for (byte[] piece : toSend) {
                if (sender != null) {
                    sender.sendPluginMessage(plugin, LinkCodec.CHANNEL, piece);
                }
            }
        });
    }

    private void report(byte[] key) {
        if (library.isClosed()) {
            return;
        }
        Collection<? extends Player> players = Bukkit.getOnlinePlayers();
        Player carrier = firstOnline();
        if (carrier == null) {
            return;
        }
        String platform = Bukkit.getName() + " " + Bukkit.getBukkitVersion();
        Attempt<Void> sent = Failures.runCatchingNonFatal(() -> {
            carrier.sendPluginMessage(plugin, LinkCodec.CHANNEL, LinkCodec.encode(
                LinkReports.build(library, platform, players.size(), Instant.now()), key));
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

    private static Player firstOnline() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            return player;
        }
        return null;
    }
}
