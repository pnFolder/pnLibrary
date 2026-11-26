package org.pnfolder.pnlibrary.bukkit.menu;

import java.util.List;
import java.util.UUID;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.pnfolder.pnlibrary.api.runtime.PnLibrary;
import org.pnfolder.pnlibrary.api.updates.state.UpdateActor;
import org.pnfolder.pnlibrary.bukkit.tasks.ServerScheduler;
import org.pnfolder.pnlibrary.core.menu.Menu;
import org.pnfolder.pnlibrary.core.menu.MenuViewer;

/** The viewer of one local player. */
final class BukkitMenuViewer implements MenuViewer {
    private final Player player;
    private final PnLibrary library;
    private final ServerScheduler scheduler;
    private final BukkitMenuAdapter adapter;

    BukkitMenuViewer(Player player, PnLibrary library, ServerScheduler scheduler, BukkitMenuAdapter adapter) {
        this.player = player;
        this.library = library;
        this.scheduler = scheduler;
        this.adapter = adapter;
    }

    Player getNative() {
        return player;
    }

    @Override
    public UUID getId() {
        return player.getUniqueId();
    }

    @Override
    public String getName() {
        return player.getName();
    }

    @Override
    public boolean isOnline() {
        return player.isOnline();
    }

    @Override
    public boolean isRemote() {
        return false;
    }

    @Override
    public UpdateActor getActor() {
        return new UpdateActor(UpdateActor.Kind.PLAYER, player.getUniqueId().toString(), player.getName());
    }

    @Override
    public boolean hasPermission(String permission) {
        return player.hasPermission(permission);
    }

    @Override
    public void open(Menu menu) {
        onPlayerThread(() -> adapter.open(player, menu));
    }

    @Override
    public void close() {
        onPlayerThread(player::closeInventory);
    }

    @Override
    public void send(List<? extends Component> lines) {
        Audience audience = library.getAudiences().player(player.getUniqueId());
        if (audience != null) {
            for (Component line : lines) {
                audience.sendMessage(line);
            }
        }
    }

    @Override
    public void runCommand(String command) {
        onPlayerThread(() -> {
            player.closeInventory();
            player.performCommand(command);
        });
    }

    private void onPlayerThread(Runnable action) {
        scheduler.onPlayer(player, () -> {
            if (!library.isClosed()) {
                action.run();
            }
        });
    }
}
