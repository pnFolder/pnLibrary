package ru.privatenull.pnlibrary.gui.protocollib;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import ru.privatenull.pnlibrary.gui.GuiUpdateService;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Optional ProtocolLib transport for {@link GuiUpdateService}.
 *
 * <p>The Bukkit inventory remains the authoritative server-side container.
 * Client-side slot and title updates are sent directly through ProtocolLib.
 * Consumers must instantiate it only when ProtocolLib is installed.</p>
 */
public final class ProtocolLibSlotPacketSender
        implements GuiUpdateService.SlotPacketSender, GuiUpdateService.TitlePacketSender,
        GuiUpdateService.FullPacketSender, AutoCloseable {

    private final Plugin plugin;
    private final ProtocolManager protocol;
    private final Map<UUID, ContainerState> states = new ConcurrentHashMap<>();
    private final AtomicBoolean failureLogged = new AtomicBoolean();
    private final PacketAdapter tracker;

    public ProtocolLibSlotPacketSender(Plugin plugin) {
        this.plugin = plugin;
        this.protocol = ProtocolLibrary.getProtocolManager();
        this.tracker = new PacketAdapter(
                plugin,
                ListenerPriority.MONITOR,
                PacketType.Play.Server.OPEN_WINDOW,
                PacketType.Play.Server.WINDOW_ITEMS,
                PacketType.Play.Server.SET_SLOT,
                PacketType.Play.Server.CLOSE_WINDOW
        ) {
            @Override
            public void onPacketSending(PacketEvent event) {
                track(event);
            }
        };
        protocol.addPacketListener(tracker);
    }

    @Override
    public void send(Player player, Inventory top, int slot, ItemStack item) {
        if (player == null || top == null || player.getOpenInventory().getTopInventory() != top) return;
        ContainerState state = states.get(player.getUniqueId());
        if (state == null || state.containerId() <= 0) return;

        try {
            sendSlot(player, state, slot, item);
        } catch (RuntimeException exception) {
            if (failureLogged.compareAndSet(false, true)) {
                plugin.getLogger().warning(
                        "ProtocolLib SET_SLOT transport failed: "
                                + exception.getMessage()
                );
            }
            throw new IllegalStateException("ProtocolLib SET_SLOT transport failed", exception);
        }
    }

    @Override
    public boolean sendTitle(Player player, Inventory top, String title) {
        if (player == null || top == null || player.getOpenInventory().getTopInventory() != top) return false;
        ContainerState state = states.get(player.getUniqueId());
        if (state == null || state.openPacket() == null) return false;
        try {
            // Registry-backed MenuType must keep its original identity on 1.20.5+.
            // deepClone() creates an unregistered MenuType and breaks packet encoding.
            PacketContainer packet = state.openPacket().shallowClone();
            // InventoryView#getTitle() returns legacy formatting (including the
            // §x§R§R§G§G§B§B representation of RGB). fromText() treats those
            // codes as literal text, while fromLegacyText() converts them into
            // a real JSON chat component and keeps the RGB colour intact.
            packet.getChatComponents().writeSafely(0, WrappedChatComponent.fromLegacyText(title));
            protocol.sendServerPacket(player, packet, false);
            syncTop(player, top);
            return true;
        } catch (RuntimeException exception) {
            if (failureLogged.compareAndSet(false, true)) {
                plugin.getLogger().warning(
                        "ProtocolLib title transport failed: "
                                + exception.getMessage());
            }
            throw new IllegalStateException("ProtocolLib title transport failed", exception);
        }
    }

    @Override
    public void syncTop(Player player, Inventory top) {
        if (player == null || top == null || player.getOpenInventory().getTopInventory() != top) return;
        ContainerState state = states.get(player.getUniqueId());
        if (state == null || state.containerId() <= 0) return;
        for (int slot = 0; slot < top.getSize(); slot++) {
            sendSlot(player, state, slot, top.getItem(slot));
        }
    }

    private void sendSlot(Player player, ContainerState state, int slot, ItemStack item) {
        PacketContainer packet = protocol.createPacket(PacketType.Play.Server.SET_SLOT);
        packet.getIntegers()
                .writeSafely(0, state.containerId())
                .writeSafely(1, state.stateId())
                .writeSafely(2, slot);
        packet.getItemModifier().writeSafely(0,
                item == null ? new ItemStack(Material.AIR) : item.clone());
        protocol.sendServerPacket(player, packet, false);
    }

    @Override
    public void close() {
        protocol.removePacketListener(tracker);
        states.clear();
    }

    private void track(PacketEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        PacketType type = event.getPacketType();
        if (type == PacketType.Play.Server.CLOSE_WINDOW) {
            states.remove(playerId);
            return;
        }

        Integer containerId = event.getPacket().getIntegers().readSafely(0);
        if (containerId == null || containerId <= 0) return;
        if (type == PacketType.Play.Server.OPEN_WINDOW) {
            states.put(playerId, new ContainerState(containerId, 0, event.getPacket().shallowClone()));
            return;
        }

        Integer stateId = event.getPacket().getIntegers().readSafely(1);
        if (stateId == null) return;
        states.compute(playerId, (ignored, current) -> {
            if (current == null || current.containerId() != containerId) {
                return new ContainerState(containerId, stateId, current == null ? null : current.openPacket());
            }
            return stateId >= current.stateId()
                    ? new ContainerState(containerId, stateId, current.openPacket())
                    : current;
        });
    }

    private record ContainerState(int containerId, int stateId, PacketContainer openPacket) {
    }
}
