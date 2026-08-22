package ru.privatenull.pnlibrary.gui;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import ru.privatenull.pnlibrary.gui.protocollib.ProtocolLibSlotPacketSender;

import java.lang.reflect.Method;

/**
 * Updates individual slots in an already open top inventory without reopening
 * the GUI. Bukkit emits the correct version-specific container packet and
 * state id, which stays safe when ProtocolLib is installed as well.
 */
public final class GuiUpdateService implements AutoCloseable {

    @FunctionalInterface
    public interface SlotPacketSender {
        void send(Player player, Inventory top, int slot, ItemStack item);
    }

    public interface TitlePacketSender {
        boolean sendTitle(Player player, Inventory top, String title);
    }

    public interface FullPacketSender {
        void syncTop(Player player, Inventory top);
    }

    private final SlotPacketSender packetSender;
    private final boolean bukkitTitleFallback;

    public GuiUpdateService() {
        this(null, true);
    }

    public GuiUpdateService(SlotPacketSender packetSender) {
        this(packetSender, true);
    }

    private GuiUpdateService(SlotPacketSender packetSender, boolean bukkitTitleFallback) {
        this.packetSender = packetSender;
        this.bukkitTitleFallback = bukkitTitleFallback;
    }

    /** Uses ProtocolLib when it is installed; plain Bukkit remains the fallback. */
    public static GuiUpdateService create(Plugin plugin) {
        if (plugin != null && plugin.getServer().getPluginManager().isPluginEnabled("ProtocolLib")) {
            try {
                return new GuiUpdateService(new ProtocolLibSlotPacketSender(plugin));
            } catch (RuntimeException | LinkageError exception) {
                plugin.getLogger().warning("ProtocolLib GUI transport is unavailable; using Bukkit: "
                        + exception.getMessage());
            }
        }
        return new GuiUpdateService();
    }

    /** Creates a required ProtocolLib-only GUI transport without a Bukkit title fallback. */
    public static GuiUpdateService protocolLib(Plugin plugin) {
        if (plugin == null || !plugin.getServer().getPluginManager().isPluginEnabled("ProtocolLib")) {
            throw new IllegalStateException("ProtocolLib is required for GUI transport");
        }
        try {
            return new GuiUpdateService(new ProtocolLibSlotPacketSender(plugin), false);
        } catch (RuntimeException | LinkageError exception) {
            throw new IllegalStateException("Cannot initialize ProtocolLib GUI transport", exception);
        }
    }

    /**
     * Updates a slot only while the player still has this exact inventory open.
     * This prevents a delayed refresh from one GUI from changing another GUI
     * that the player opened immediately afterwards.
     */
    public void setTopSlot(Player player, Inventory expectedTop, int slot, ItemStack item) {
        if (player == null || expectedTop == null || slot < 0) return;
        Inventory top = player.getOpenInventory().getTopInventory();
        if (top != expectedTop || slot >= top.getSize()) return;
        if (sameItem(top.getItem(slot), item)) return;
        ItemStack replacement = item == null ? null : item.clone();
        top.setItem(slot, replacement);
        if (packetSender != null) {
            packetSender.send(player, top, slot, replacement);
        }
    }

    /** Updates the title of the current inventory without reopening it when the server supports it. */
    public boolean setTitle(Player player, Inventory expectedTop, String title) {
        if (player == null || expectedTop == null || title == null) return false;
        InventoryView view = player.getOpenInventory();
        if (view.getTopInventory() != expectedTop) return false;
        if (packetSender instanceof TitlePacketSender titles
                && titles.sendTitle(player, expectedTop, title)) return true;
        if (title.equals(view.getTitle())) return true;
        if (!bukkitTitleFallback) return false;
        try {
            Method method = InventoryView.class.getMethod("setTitle", String.class);
            method.invoke(view, title);
            return true;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    /** Sends the complete visible top inventory through the configured packet transport. */
    public void syncTop(Player player, Inventory expectedTop) {
        if (player == null || expectedTop == null
                || player.getOpenInventory().getTopInventory() != expectedTop) return;
        if (packetSender instanceof FullPacketSender full) {
            full.syncTop(player, expectedTop);
            return;
        }
        for (int slot = 0; slot < expectedTop.getSize(); slot++) {
            if (packetSender != null) packetSender.send(player, expectedTop, slot, expectedTop.getItem(slot));
        }
    }

    @Override
    public void close() {
        if (!(packetSender instanceof AutoCloseable closeable)) return;
        try {
            closeable.close();
        } catch (Exception ignored) {
        }
    }

    /**
     * @deprecated Use {@link #setTopSlot(Player, Inventory, int, ItemStack)}
     * so a delayed update cannot affect an unrelated open GUI.
     */
    @Deprecated
    public void setTopSlot(Player player, int slot, ItemStack item) {
        if (player == null || slot < 0) return;
        Inventory top = player.getOpenInventory().getTopInventory();
        if (top == null || slot >= top.getSize()) return;
        if (sameItem(top.getItem(slot), item)) return;
        ItemStack replacement = item == null ? null : item.clone();
        top.setItem(slot, replacement);
        if (packetSender != null) {
            packetSender.send(player, top, slot, replacement);
        }
    }

    private boolean sameItem(ItemStack current, ItemStack replacement) {
        boolean currentEmpty = current == null || current.getType().isAir();
        boolean replacementEmpty = replacement == null || replacement.getType().isAir();
        if (currentEmpty || replacementEmpty) return currentEmpty == replacementEmpty;
        return current.equals(replacement);
    }
}
