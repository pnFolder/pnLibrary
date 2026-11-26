package org.pnfolder.pnlibrary.bukkit.inventory;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.inventory.Inventory;

final class MenuListener implements Listener {
    private final MenuSessions sessions;

    MenuListener(MenuSessions sessions) {
        this.sessions = sessions;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        BukkitMenuSession session = shown(event.getWhoClicked(), event.getView().getTopInventory());
        if (session == null) {
            return;
        }
        if (!session.getOwner().isEnabled() || sessions.isClosed()) {
            event.setCancelled(true);
            session.discard();
            return;
        }
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= session.getInventory().getSize()) {
            if (session.getMenu().isCancelPlayerInventory()) {
                event.setCancelled(true);
            }
            return;
        }
        MenuItem item = session.getMenu().getItems().get(slot);
        event.setCancelled(item == null || !item.isEditable());
        String renameText = session.getMenu().getType() == MenuType.ANVIL
            ? AnvilText.renameText(session.getInventory()) : null;
        MenuClick click = new MenuClick(session, slot, event.getCurrentItem(), event.getCursor(), event.getClick(),
            event.getAction(), true, renameText);
        if (item != null && item.getHandler() != null) {
            item.getHandler().handle(click);
        }
        if (session.getMenu().getClickHandler() != null) {
            session.getMenu().getClickHandler().handle(click);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        BukkitMenuSession session = shown(event.getWhoClicked(), event.getView().getTopInventory());
        if (session == null) {
            return;
        }
        int size = session.getInventory().getSize();
        for (int slot : event.getRawSlots()) {
            MenuItem item = session.getMenu().getItems().get(slot);
            if (slot < size && (item == null || !item.isEditable())) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        BukkitMenuSession session = shown(event.getPlayer(), event.getInventory());
        if (session != null) {
            session.endAndNotify();
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        BukkitMenuSession session = sessions.of(event.getPlayer());
        if (session != null) {
            session.endAndNotify();
        }
    }

    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) {
        List<Runnable> actions = new ArrayList<>();
        for (BukkitMenuSession session : sessions.of(event.getPlugin())) {
            actions.add(session::discard);
        }
        ActionsRunner.runEach(actions);
    }

    private BukkitMenuSession shown(HumanEntity viewer, Inventory inventory) {
        if (!(viewer instanceof Player)) {
            return null;
        }
        BukkitMenuSession session = sessions.of((Player) viewer);
        return session != null && session.isShownIn(inventory) ? session : null;
    }
}
