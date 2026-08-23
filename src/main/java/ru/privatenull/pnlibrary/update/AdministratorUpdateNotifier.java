package ru.privatenull.pnlibrary.update;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import ru.privatenull.pnlibrary.banner.PluginBanner;
import ru.privatenull.pnlibrary.text.ColorUtil;

/** Отправляет администраторам одно игровое уведомление на версию и состояние загрузки. */
final class AdministratorUpdateNotifier implements Listener, AutoCloseable {

    private final PluginBanner.Identity identity;
    private final JavaPlugin plugin;
    private final Supplier<UpdateSnapshot> snapshot;
    private final String permission;
    private final ConcurrentHashMap<UUID, String> notifiedVersions = new ConcurrentHashMap<>();
    private boolean registered;

    AdministratorUpdateNotifier(
            PluginBanner.Identity identity,
            Supplier<UpdateSnapshot> snapshot
        ) {
        this.identity = identity;
        this.plugin = identity.plugin();
        this.snapshot = snapshot;
        permission = identity.notificationPermission();
    }

    void start() {
        if (registered || !identity.notifyAdministrators() || !identity.notifyAdministratorsOnJoin()) return;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        registered = true;
    }

    void notifyOnline() {
        if (!identity.notifyAdministrators() || !identity.notifyOnlineAdministrators()) return;
        plugin.getServer().getOnlinePlayers().forEach(this::notifyPlayer);
    }

    void notifyPlayer(Player player) {
        if (player == null || permission == null
                || !identity.notifyAdministrators() || !player.hasPermission(permission)) return;

        UpdateSnapshot current = snapshot.get();
        if (!current.updateAvailable()) return;

        String key = current.latestVersion() + ':' + current.updateDownloaded();
        if (key.equals(notifiedVersions.put(player.getUniqueId(), key))) return;
        send(player, current);
    }

    String permission() {
        return permission;
    }

    @Override
    public void close() {
        if (registered) {
            HandlerList.unregisterAll(this);
            registered = false;
        }
        notifiedVersions.clear();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        notifyPlayer(event.getPlayer());
    }

    private void send(Player player, UpdateSnapshot current) {
        player.sendMessage(ColorUtil.component("&8&m                                                "));
        player.sendMessage(ColorUtil.component("&#94CA85&l" + plugin.getName() + " &8• &fОБНОВЛЕНИЕ"));
        player.sendMessage(Component.empty());
        player.sendMessage(ColorUtil.component("&fДоступна новая версия: &7"
                + plugin.getDescription().getVersion() + " &8→ &#9EFC65" + current.latestVersion()));

        if (current.updateDownloaded()) {
            player.sendMessage(ColorUtil.component("&#9EFC65Обновление уже загружено."
                    + " &7Оно установится после перезапуска сервера."));
        } else {
            player.sendMessage(ColorUtil.component("&7Скачайте новую версию и перезапустите сервер."));
        }

        player.sendMessage(Component.empty());
        Component actions = link(
                current.updateDownloaded() ? "&#9EFC65&l[ОТКРЫТЬ RELEASE]" : "&#9EFC65&l[СКАЧАТЬ]",
                current.downloadUrl() == null ? current.pageUrl() : current.downloadUrl(),
                "&fОткрыть страницу обновления"
        );
        if (identity.supportUrl() != null) {
            actions = actions.append(ColorUtil.component("  ")).append(link(
                    "&#5865F2&l[ПОДДЕРЖКА]",
                    identity.supportUrl(),
                    "&fОткрыть страницу поддержки"
            ));
        }
        player.sendMessage(actions);
        player.sendMessage(ColorUtil.component("&8&m                                                "));
        player.sendTitle(
                ColorUtil.colorize("&#429F91&l" + plugin.getName()),
                ColorUtil.colorize("&fДоступна версия &#D8DF9D" + current.latestVersion()),
                10,
                70,
                20
        );
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.45f, 1.6f);
    }

    private static Component link(String text, String url, String hover) {
        return ColorUtil.component(text)
                .clickEvent(ClickEvent.openUrl(url))
                .hoverEvent(HoverEvent.showText(ColorUtil.component(hover)));
    }

}
