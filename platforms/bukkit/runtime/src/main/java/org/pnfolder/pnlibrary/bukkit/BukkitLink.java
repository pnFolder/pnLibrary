package org.pnfolder.pnlibrary.bukkit;

import java.nio.file.Path;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.pnfolder.pnlibrary.core.link.Link;
import org.pnfolder.pnlibrary.core.link.wire.LinkCodec;
import org.pnfolder.pnlibrary.core.link.wire.LinkSecret;

/** The link of a game server: it talks to the proxy through any player online, so nothing leaves while the server is empty. */
final class BukkitLink {
    private BukkitLink() {
    }

    static Link open(JavaPlugin plugin) {
        Path folder = plugin.getDataFolder().toPath();
        byte[] key = LinkSecret.read(folder);
        if (key == null) {
            return Link.OFF;
        }
        return new Link(key, null, null, bytes -> {
            Player carrier = null;
            for (Player player : org.bukkit.Bukkit.getOnlinePlayers()) {
                carrier = player;
                break;
            }
            if (carrier != null) {
                carrier.sendPluginMessage(plugin, LinkCodec.CHANNEL, bytes);
            }
            return carrier != null;
        });
    }
}
