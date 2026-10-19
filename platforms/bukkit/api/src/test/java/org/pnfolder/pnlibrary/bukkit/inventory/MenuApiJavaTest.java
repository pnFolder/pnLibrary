package org.pnfolder.pnlibrary.bukkit.inventory;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MenuApiJavaTest {
    @Test
    void menuFactoriesAndBuilderAreJavaFriendly() {
        Menu menu = Menus.chest("Example")
            .rows(3)
            .slot(0, null)
            .build();

        assertEquals(27, menu.getSize());
        assertThrows(UnsupportedOperationException.class, () -> menu.getItems().clear());
    }
}
