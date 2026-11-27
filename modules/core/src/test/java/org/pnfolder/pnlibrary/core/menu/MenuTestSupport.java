package org.pnfolder.pnlibrary.core.menu;

import org.pnfolder.pnlibrary.core.menu.session.ClickType;
import org.pnfolder.pnlibrary.core.menu.session.MenuClick;
import org.pnfolder.pnlibrary.core.menu.session.MenuClickHandler;
import org.pnfolder.pnlibrary.core.menu.session.MenuViewer;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;
import java.util.Map;
import org.pnfolder.pnlibrary.core.menu.style.MenuConstants;

/** What the menu tests share: reading names, lore and clicking a slot. */
final class MenuTestSupport {
    private MenuTestSupport() {
    }

    static String plain(String text) {
        return text.replaceAll("§.", "");
    }

    static void click(MenuViewer viewer, Menu menu, int slot) {
        click(viewer, menu, slot, ClickType.LEFT);
    }

    static void click(MenuViewer viewer, Menu menu, int slot, ClickType how) {
        MenuClickHandler handler = menu.getItems().get(slot).getHandler();
        assertNotNull(handler);
        handler.handle(new MenuClick(viewer, slot, how));
    }

    static int slotNamed(Menu menu, String name) {
        for (Map.Entry<Integer, MenuItem> entry : menu.getItems().entrySet()) {
            ItemSpec item = entry.getValue().getItem();
            if (item != null && plain(item.getName()).equals(name)) {
                return entry.getKey();
            }
        }
        throw new java.util.NoSuchElementException(name);
    }

    static List<String> loreOf(Menu menu, String name) {
        return menu.getItems().get(slotNamed(menu, name)).getItem().getLore();
    }

    static String nameAt(Menu menu, int slot) {
        MenuItem item = menu.getItems().get(slot);
        return item == null || item.getItem() == null ? null : plain(item.getItem().getName());
    }

    static String title(Menu menu) {
        return plain(menu.getTitle());
    }

    static int clickable(Menu menu) {
        int count = 0;
        for (Map.Entry<Integer, MenuItem> entry : menu.getItems().entrySet()) {
            MenuItem item = entry.getValue();
            if (entry.getKey() < MenuConstants.PAGE_SIZE && item.getItem() != null && item.getHandler() != null) {
                count++;
            }
        }
        return count;
    }
}
