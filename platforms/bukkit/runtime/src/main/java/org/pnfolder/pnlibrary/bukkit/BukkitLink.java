package org.pnfolder.pnlibrary.bukkit;

import java.nio.file.Path;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.pnfolder.pnlibrary.api.network.channel.ServerChannel;
import org.pnfolder.pnlibrary.core.link.Link;
import org.pnfolder.pnlibrary.core.link.wire.seal.LinkSecret;

/**
 * The link of a game server and the way up to the proxy: the link channel, which {@link BukkitLinkReporter} opens.
 * Unlike a normal channel message, a link message is dropped when nobody is online, because a late answer is no answer.
 */
final class BukkitLink {
    private final Link link;
    private volatile ServerChannel channel;

    private BukkitLink(Link link) {
        this.link = link;
    }

    static BukkitLink open(JavaPlugin plugin) {
        Path folder = plugin.getDataFolder().toPath();
        byte[] key = LinkSecret.read(folder);
        BukkitLink[] self = new BukkitLink[1];
        Link link = key == null ? Link.OFF : new Link(key, null, null, bytes -> self[0].send(bytes));
        self[0] = new BukkitLink(link);
        return self[0];
    }

    Link link() {
        return link;
    }

    /** Sends over {@code opened} from now on. */
    void attach(ServerChannel opened) {
        channel = opened;
    }

    /** Stops sending; the channel is closed by whoever opened it. */
    void detach() {
        channel = null;
    }

    /** Whether {@code bytes} went to the proxy; false when the link is not up or nobody is online to carry them. */
    boolean send(byte[] bytes) {
        ServerChannel up = channel;
        if (up == null || !up.isOpen() || Bukkit.getOnlinePlayers().isEmpty()) {
            return false;
        }
        up.send(bytes);
        return true;
    }
}
