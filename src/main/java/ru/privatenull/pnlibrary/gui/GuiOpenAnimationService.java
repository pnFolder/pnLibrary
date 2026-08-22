package ru.privatenull.pnlibrary.gui;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Opens an inventory and reveals its contents using a configurable animation.
 * The service also animates the window title when the server API supports it.
 */
public final class GuiOpenAnimationService {

    private static final int TARGET_REVEAL_STEPS = 8;
    private static final int TITLE_CHARACTERS_PER_TICK = 4;

    private final Plugin plugin;
    private final GuiUpdateService updates;
    private final Map<UUID, OpeningAnimation> tasks = new HashMap<>();

    public GuiOpenAnimationService(Plugin plugin) {
        this(plugin, GuiUpdateService.create(plugin));
    }

    /**
     * Creates an animation service using the supplied slot-update strategy.
     * The update service may use plain Bukkit or an optional packet adapter.
     */
    public GuiOpenAnimationService(Plugin plugin, GuiUpdateService updates) {
        if (plugin == null) throw new IllegalArgumentException("plugin cannot be null");
        if (updates == null) throw new IllegalArgumentException("updates cannot be null");
        this.plugin = plugin;
        this.updates = updates;
    }

    public void open(Player player, Inventory inventory) {
        openResolved(player, inventory, true, GuiAnimationType.CENTER_OUT);
    }

    public void open(Player player, Inventory inventory, boolean animateTitle) {
        openResolved(player, inventory, animateTitle, GuiAnimationType.CENTER_OUT);
    }

    public void open(Player player, Inventory inventory, GuiAnimationType type) {
        openResolved(player, inventory, true, type);
    }

    public void open(Player player, Inventory inventory, boolean animateTitle, GuiAnimationType type) {
        openResolved(player, inventory, animateTitle, type);
    }

    /** Opens an inventory using the profile section containing {@code sourceSlot}. */
    public void open(Player player, Inventory inventory, boolean animateTitle,
                     GuiAnimationProfile profile, int sourceSlot) {
        GuiAnimationType type = profile == null ? GuiAnimationType.CENTER_OUT : profile.resolve(sourceSlot);
        openResolved(player, inventory, animateTitle, type);
    }

    private void openResolved(Player player, Inventory inventory, boolean animateTitle, GuiAnimationType type) {
        if (player == null || inventory == null) return;
        cancel(player);

        GuiAnimationType resolvedType = type == null ? GuiAnimationType.CENTER_OUT : type;
        if (resolvedType == GuiAnimationType.NONE) {
            player.openInventory(inventory);
            return;
        }

        List<SlotItem> items = snapshot(inventory, resolvedType);
        int itemsPerStep = Math.max(1, (items.size() + TARGET_REVEAL_STEPS - 1) / TARGET_REVEAL_STEPS);
        inventory.clear();
        player.openInventory(inventory);

        if (!isOpen(player, inventory)) {
            restoreInventory(inventory, items, 0);
            return;
        }

        String title = player.getOpenInventory().getTitle();
        boolean titleAnimationActive = animateTitle && updates.setTitle(player, inventory, " ");
        UUID playerId = player.getUniqueId();
        OpeningAnimation animation = new OpeningAnimation(inventory, items, title, titleAnimationActive);

        tasks.put(playerId, animation);
        try {
            animation.task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
                if (tasks.get(playerId) != animation) {
                    cancelTask(animation);
                    return;
                }
                if (!isOpen(player, inventory)) {
                    restoreRemaining(animation);
                    finish(playerId, animation);
                    return;
                }
                for (int count = 0; count < itemsPerStep && animation.cursor < animation.items.size(); count++) {
                    SlotItem next = animation.items.get(animation.cursor++);
                    updates.setTopSlot(player, inventory, next.slot(), next.item());
                }
                if (animation.animateTitle && animation.titleCursor < animation.visibleTitleLength) {
                    animation.titleCursor = Math.min(animation.visibleTitleLength,
                            animation.titleCursor + TITLE_CHARACTERS_PER_TICK);
                    updates.setTitle(player, inventory, reveal(animation.title, animation.titleCursor));
                }
                if (animation.cursor >= animation.items.size()
                        && (!animation.animateTitle || animation.titleCursor >= animation.visibleTitleLength)) {
                    updates.syncTop(player, inventory);
                    finish(playerId, animation);
                }
            }, 1L, 1L);
        } catch (RuntimeException exception) {
            tasks.remove(playerId, animation);
            restoreRemaining(animation);
            if (isOpen(player, inventory) && animation.animateTitle) {
                updates.setTitle(player, inventory, animation.title);
            }
            throw exception;
        }
    }

    /**
     * Safely stops the current animation. Any slots that have not appeared yet
     * are restored first, so reopening the same Inventory can never expose a
     * partially cleared backing inventory.
     */
    public void cancel(Player player) {
        if (player == null) return;
        OpeningAnimation animation = tasks.remove(player.getUniqueId());
        if (animation == null) return;
        completeAnimation(player, animation);
    }

    /** Completes the current visual state before cancelling its scheduled task. */
    public void complete(Player player) {
        cancel(player);
    }

    public boolean isAnimating(Player player) {
        return player != null && tasks.containsKey(player.getUniqueId());
    }

    public void shutdown() {
        for (OpeningAnimation animation : new ArrayList<>(tasks.values())) {
            restoreRemaining(animation);
            cancelTask(animation);
        }
        tasks.clear();
    }

    private void completeAnimation(Player player, OpeningAnimation animation) {
        if (isOpen(player, animation.inventory)) {
            while (animation.cursor < animation.items.size()) {
                SlotItem next = animation.items.get(animation.cursor++);
                updates.setTopSlot(player, animation.inventory, next.slot(), next.item());
            }
            if (animation.animateTitle) {
                updates.setTitle(player, animation.inventory, animation.title);
            }
            updates.syncTop(player, animation.inventory);
        } else {
            restoreRemaining(animation);
        }
        cancelTask(animation);
    }

    private void restoreRemaining(OpeningAnimation animation) {
        restoreInventory(animation.inventory, animation.items, animation.cursor);
        animation.cursor = animation.items.size();
    }

    private static void restoreInventory(Inventory inventory, List<SlotItem> items, int fromIndex) {
        for (int index = fromIndex; index < items.size(); index++) {
            SlotItem next = items.get(index);
            inventory.setItem(next.slot(), next.item());
        }
    }

    private static boolean isOpen(Player player, Inventory inventory) {
        return player.getOpenInventory().getTopInventory() == inventory;
    }

    private static void cancelTask(OpeningAnimation animation) {
        if (animation.task != null) {
            animation.task.cancel();
            animation.task = null;
        }
    }

    private void finish(UUID playerId, OpeningAnimation animation) {
        tasks.remove(playerId, animation);
        cancelTask(animation);
    }

    private static List<SlotItem> snapshot(Inventory inventory, GuiAnimationType type) {
        List<SlotItem> result = new ArrayList<>();
        double centerX = 4.0D;
        double centerY = (inventory.getSize() / 9 - 1) / 2.0D;
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item != null && !item.getType().isAir()) result.add(new SlotItem(slot, item.clone()));
        }
        Comparator<SlotItem> order = switch (type) {
            // Smooth diagonal wave: upper-left -> lower-right.
            case LEFT_TO_RIGHT -> Comparator
                    .comparingInt((SlotItem entry) -> entry.slot() % 9 + entry.slot() / 9)
                    .thenComparingInt(entry -> entry.slot() / 9);
            // Exact reverse wave: lower-right -> upper-left.
            case RIGHT_TO_LEFT -> Comparator
                    .comparingInt((SlotItem entry) -> entry.slot() % 9 + entry.slot() / 9)
                    .thenComparingInt(entry -> entry.slot() / 9)
                    .reversed();
            case TOP_TO_BOTTOM -> Comparator.comparingInt(entry -> entry.slot() / 9);
            case BOTTOM_TO_TOP -> Comparator.comparingInt((SlotItem entry) -> entry.slot() / 9).reversed();
            case DIAGONAL_DOWN -> Comparator.comparingInt(entry -> entry.slot() % 9 + entry.slot() / 9);
            case DIAGONAL_UP -> Comparator.comparingInt(entry -> entry.slot() % 9 - entry.slot() / 9);
            case CENTER_OUT, NONE -> Comparator.comparingDouble(entry -> {
                double x = entry.slot() % 9;
                double y = entry.slot() / 9;
                return Math.max(Math.abs(x - centerX), Math.abs(y - centerY));
            });
        };
        result.sort(order.thenComparingInt(SlotItem::slot));
        return result;
    }

    private static int visibleLength(String text) {
        int length = 0;
        for (int index = 0; index < text.length(); index++) {
            if (text.charAt(index) == '\u00a7' && index + 1 < text.length()) index++;
            else length++;
        }
        return length;
    }

    private static String reveal(String text, int visibleCharacters) {
        StringBuilder result = new StringBuilder(text.length());
        int visible = 0;
        for (int index = 0; index < text.length() && visible < visibleCharacters; index++) {
            char character = text.charAt(index);
            result.append(character);
            if (character == '\u00a7' && index + 1 < text.length()) result.append(text.charAt(++index));
            else visible++;
        }
        return result.toString();
    }

    private record SlotItem(int slot, ItemStack item) {
    }

    private static final class OpeningAnimation {
        private final Inventory inventory;
        private final List<SlotItem> items;
        private final String title;
        private final boolean animateTitle;
        private final int visibleTitleLength;
        private int cursor;
        private int titleCursor;
        private BukkitTask task;

        private OpeningAnimation(Inventory inventory, List<SlotItem> items, String title, boolean animateTitle) {
            this.inventory = inventory;
            this.items = items;
            this.title = title;
            this.animateTitle = animateTitle;
            this.visibleTitleLength = visibleLength(title);
        }
    }
}
