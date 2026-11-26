package org.pnfolder.pnlibrary.core.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.pnfolder.pnlibrary.core.menu.data.MenuData;
import org.pnfolder.pnlibrary.core.menu.style.Row;

class MenuSessionTest {
    private static ItemSpec item(String name) {
        return new ItemSpec(null, "STONE", null, name, Collections.<String>emptyList());
    }

    private static MenuSession session() {
        return new MenuSession(mock(MenuData.class), mock(MenuHost.class));
    }

    private static List<Row> rows(int count) {
        List<Row> rows = new ArrayList<>();
        for (int index = 1; index <= count; index++) {
            rows.add(new Row(item("r" + index)));
        }
        return rows;
    }

    private static Menu shown(MenuSession session, List<Row> rows, int page) {
        MenuViewer viewer = mock(MenuViewer.class);
        final AtomicReference<Menu> menu = new AtomicReference<>();
        Mockito.doAnswer(call -> {
            menu.set(call.<Menu>getArgument(0));
            return null;
        }).when(viewer).open(Mockito.any(Menu.class));
        session.list(viewer, "Список", rows, page, 3, (who, to) -> { }, who -> { }, "пусто", rows.size(), builder -> { });
        return menu.get();
    }

    private static String plain(String text) {
        return text.replaceAll("§.", "");
    }

    @Test
    void builderRefusesSlotsOutsideTheChestAndMoreThanSixRows() {
        assertThrows(IllegalArgumentException.class, () -> Menus.chest("t").rows(7));
        assertThrows(IllegalArgumentException.class, () -> Menus.chest("t").rows(1).slot(9, item("x")));
    }

    @Test
    void fillNeverOverwritesASlotThatIsAlreadySet() {
        Menu menu = Menus.chest("t").rows(1).slot(0, item("own")).fill(item("pane")).build();

        assertEquals("own", menu.getItems().get(0).getItem().getName());
        assertEquals("pane", menu.getItems().get(1).getItem().getName());
        assertEquals(9, menu.getItems().size());
    }

    @Test
    void aMiddlePageShowsOnlyItsRowsAndBothPageButtons() {
        Menu menu = shown(session(), rows(7), 1);

        assertEquals("r4", menu.getItems().get(0).getItem().getName());
        assertEquals("r6", menu.getItems().get(2).getItem().getName());
        assertTrue(menu.getTitle().contains("2/3"));
        assertNotNull(menu.getItems().get(48).getHandler());
        assertNotNull(menu.getItems().get(50).getHandler());
    }

    @Test
    void anEmptyListSaysSoAndAPagePastTheEndIsPulledBack() {
        Menu empty = shown(session(), new ArrayList<Row>(), 5);
        assertTrue(plain(empty.getItems().get(22).getItem().getName()).contains("пусто"));
        assertTrue(empty.getTitle().contains("1/1"));

        Menu last = shown(session(), rows(4), 99);
        assertTrue(last.getTitle().contains("2/2"));
        assertNull(last.getItems().get(50).getHandler());
    }

    @Test
    void withoutTheRightThePlayerGetsARefusalAndTheActionDoesNotRun() {
        MenuViewer viewer = mock(MenuViewer.class);
        final AtomicBoolean ran = new AtomicBoolean();

        session().allowed(viewer, "pnlibrary.tasks", () -> ran.set(true));

        assertFalse(ran.get());
        verify(viewer).send(anyList());
    }

    @Test
    void anAdministratorPassesEveryCheck() {
        MenuViewer viewer = mock(MenuViewer.class);
        when(viewer.hasPermission("pnlibrary.admin")).thenReturn(true);
        final AtomicBoolean ran = new AtomicBoolean();

        session().allowed(viewer, "pnlibrary.tasks", () -> ran.set(true));

        assertTrue(ran.get());
    }
}
