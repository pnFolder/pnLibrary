package org.pnfolder.pnlibrary.bukkit.inventory;

import java.lang.reflect.Method;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** Text typed into an anvil: {@code getRenameText} exists only on newer servers, older ones expose the item name. */
final class AnvilText {
    private AnvilText() {
    }

    static String renameText(Inventory inventory) {
        try {
            for (Method method : inventory.getClass().getMethods()) {
                if (method.getName().equals("getRenameText") && method.getParameterTypes().length == 0) {
                    Object value = method.invoke(inventory);
                    if (value instanceof String && !((String) value).trim().isEmpty()) {
                        return (String) value;
                    }
                    break;
                }
            }
        } catch (ReflectiveOperationException error) {
            // The accessor is missing on older servers; the item name is read instead.
        }
        return itemName(inventory);
    }

    @SuppressWarnings("deprecation")
    private static String itemName(Inventory inventory) {
        String result = displayName(inventory.getItem(2));
        return result != null ? result : displayName(inventory.getItem(0));
    }

    private static String displayName(ItemStack stack) {
        ItemMeta meta = stack == null ? null : stack.getItemMeta();
        return meta == null ? null : meta.getDisplayName();
    }
}
