package org.pnfolder.pnlibrary.core.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.pnfolder.pnlibrary.core.menu.MenuTestSupport.clickable;
import static org.pnfolder.pnlibrary.core.menu.MenuTestSupport.click;
import static org.pnfolder.pnlibrary.core.menu.MenuTestSupport.plain;
import static org.pnfolder.pnlibrary.core.menu.MenuTestSupport.title;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.placeholders.info.PlaceholderInfo;
import org.pnfolder.pnlibrary.api.updates.state.UpdateSnapshot;
import org.pnfolder.pnlibrary.api.updates.state.UpdateState;
import org.pnfolder.pnlibrary.core.menu.data.MenuData;
import org.pnfolder.pnlibrary.core.menu.data.PlanInfo;
import org.pnfolder.pnlibrary.core.menu.screens.config.ConfigRow;
import org.pnfolder.pnlibrary.core.menu.style.MenuConstants;

class LibraryMenuTest {
    /** Facts that are all empty, except what a test gives: lists are empty, texts are plain, the rest is nothing. */
    private static MenuData data(final List<PlaceholderInfo> placeholders, final List<ConfigRow> configs) {
        return (MenuData) Proxy.newProxyInstance(MenuData.class.getClassLoader(), new Class<?>[] {MenuData.class}, (proxy, method, args) -> {
            Class<?> type = method.getReturnType();
            if (method.getName().equals("placeholders")) {
                return placeholders;
            }
            if (method.getName().equals("configs")) {
                return configs;
            }
            if (type == List.class) {
                return Collections.emptyList();
            }
            if (type == CompletionStage.class) {
                return CompletableFuture.completedFuture(Collections.emptyList());
            }
            if (type == String.class) {
                return "1.0";
            }
            if (type == int.class) {
                return 0;
            }
            return type == boolean.class ? (Object) false : null;
        });
    }

    private static MenuData data() {
        return data(Collections.emptyList(), Collections.emptyList());
    }

    private static List<PlaceholderInfo> placeholders(int count) {
        List<PlaceholderInfo> list = new ArrayList<>();
        for (int index = 1; index <= count; index++) {
            list.add(new PlaceholderInfo("clan.war.p" + index, "", "pnclans/war", "Integer"));
        }
        return list;
    }

    private static TestMenuViewer viewer(String... held) {
        return new TestMenuViewer(new HashSet<>(Arrays.asList(held)));
    }

    @Test
    void theHomePageOpensWithACloseButtonAndTheWayToEverySection() {
        TestMenuViewer viewer = viewer("pnlibrary.admin");

        new LibraryMenu(data(), new ImmediateHost()).home(viewer);

        Menu home = viewer.opened.get(0);
        assertEquals(1, viewer.opened.size());
        assertTrue(home.getItems().containsKey(MenuConstants.HOME_CLOSE));
        assertNotNull(home.getItems().get(23).getHandler());
        assertNotNull(home.getItems().get(25).getHandler());
    }

    @Test
    void theBlockedLibraryCardCanPrepareAForcedUpgrade() {
        TestMenuViewer viewer = viewer("pnlibrary.updates.download");
        MenuData data = mock(MenuData.class);
        UpdateSnapshot snapshot = mock(UpdateSnapshot.class);
        when(snapshot.getProduct()).thenReturn("pnlibrary");
        when(snapshot.getState()).thenReturn(UpdateState.BLOCKED);
        when(snapshot.getCurrentVersion()).thenReturn("1.0.0");
        when(snapshot.getLatestVersion()).thenReturn("2.0.0");
        when(snapshot.getChannel()).thenReturn(org.pnfolder.pnlibrary.api.updates.product.UpdateChannel.STABLE);
        when(data.snapshot("pnlibrary")).thenReturn(snapshot);
        when(data.plan()).thenReturn(new PlanInfo(UpdateState.BLOCKED, true, Collections.emptyList()));
        when(data.forceLibrary(viewer.getActor())).thenReturn(CompletableFuture.completedFuture("2.0.0"));

        new MenuRouter(new MenuSession(data, new ImmediateHost())).card(viewer, "pnlibrary");
        click(viewer, last(viewer), 12);

        verify(data).forceLibrary(viewer.getActor());
        assertTrue(viewer.sent.stream().map(PlainTextComponentSerializer.plainText()::serialize)
                .anyMatch(line -> line.contains("2.0.0")));
    }

    @Test
    void aSectionOpensForAViewerWithItsPermissionAndIsRefusedWithoutIt() {
        TestMenuViewer allowed = viewer(MenuConstants.PLACEHOLDERS);
        new LibraryMenu(data(placeholders(2), Collections.emptyList()), new ImmediateHost()).home(allowed);
        click(allowed, last(allowed), 23);
        assertTrue(last(allowed).getTitle().contains("Плейсхолдеры"));

        TestMenuViewer refused = viewer();
        new LibraryMenu(data(), new ImmediateHost()).home(refused);
        click(refused, last(refused), 23);
        assertEquals(1, refused.opened.size());
        assertTrue(!refused.sent.isEmpty());
    }

    private static Menu last(TestMenuViewer viewer) {
        return viewer.opened.get(viewer.opened.size() - 1);
    }

    @Test
    void aLongListIsCutIntoPagesThatLeadToEachOther() {
        TestMenuViewer viewer = viewer(MenuConstants.PLACEHOLDERS);
        new LibraryMenu(data(placeholders(50), Collections.emptyList()), new ImmediateHost()).home(viewer);
        click(viewer, last(viewer), 23);

        Menu first = last(viewer);
        assertTrue(first.getTitle().contains("1/2"));
        assertNotNull(first.getItems().get(MenuConstants.NEXT).getHandler());
        assertNull(handlerAt(first, MenuConstants.PREVIOUS));

        click(viewer, first, MenuConstants.NEXT);
        Menu second = last(viewer);
        assertTrue(second.getTitle().contains("2/2"));
        assertNotNull(second.getItems().get(MenuConstants.PREVIOUS).getHandler());
        assertNull(handlerAt(second, MenuConstants.NEXT));
    }

    private static MenuClickHandler handlerAt(Menu menu, int slot) {
        MenuItem item = menu.getItems().get(slot);
        return item == null ? null : item.getHandler();
    }

    @Test
    void anEmptyListSaysSoInsteadOfShowingNothing() {
        TestMenuViewer viewer = viewer(MenuConstants.PLACEHOLDERS);
        new LibraryMenu(data(), new ImmediateHost()).home(viewer);
        click(viewer, last(viewer), 23);

        assertEquals("Плейсхолдеров нет", plain(last(viewer).getItems().get(22).getItem().getName()));
    }

    private static ConfigRow file(String id) {
        return new ConfigRow(id, "Model", true, false, 1, null);
    }

    @Test
    void configurationsGoFromPluginsToModulesToFilesAndBack() {
        TestMenuViewer viewer = viewer(MenuConstants.CONFIGS);
        List<ConfigRow> files = Arrays.asList(file("shop/main:a.yml"), file("shop/main:b.yml"), file("shop/vip:c.yml"), file("clans/core:d.yml"));
        new LibraryMenu(data(Collections.emptyList(), files), new ImmediateHost()).home(viewer);
        click(viewer, last(viewer), 21);

        Menu plugins = last(viewer);
        assertTrue(title(plugins).contains("Конфигурации"));
        assertEquals(2, clickable(plugins));

        assertNotNull(plugins.getItems().get(MenuConstants.FILTER).getHandler());
        click(viewer, plugins, MenuConstants.FILTER); // every file of every plugin, with plugin and module named
        assertEquals(4, clickable(last(viewer)));
        click(viewer, last(viewer), MenuConstants.FILTER); // back to the plugins

        click(viewer, plugins, 0); // clans: one module, so its files are shown at once
        assertTrue(title(last(viewer)).contains("clans"));
        assertNotNull(last(viewer).getItems().get(MenuConstants.FILTER).getHandler());

        click(viewer, plugins, 1); // shop: two modules
        Menu shop = last(viewer);
        assertEquals(2, clickable(shop));
        assertNotNull(shop.getItems().get(MenuConstants.FILTER).getHandler());

        click(viewer, shop, 0); // module main
        Menu main = last(viewer);
        assertTrue(title(main).contains("shop · main"));
        assertEquals(2, clickable(main));

        click(viewer, main, MenuConstants.BACK);
        assertTrue(title(last(viewer)).contains("shop"));

        click(viewer, shop, MenuConstants.FILTER); // every file of the plugin, with its module named
        assertEquals(3, clickable(last(viewer)));
    }

    @Test
    void aPluginWithManyFilesIsCutIntoPages() {
        TestMenuViewer viewer = viewer(MenuConstants.CONFIGS);
        List<ConfigRow> files = new ArrayList<>();
        for (int index = 1; index <= 50; index++) {
            files.add(file("big/core:f" + index + ".yml"));
        }
        new LibraryMenu(data(Collections.emptyList(), files), new ImmediateHost()).home(viewer);
        click(viewer, last(viewer), 21);
        click(viewer, last(viewer), 0);

        Menu first = last(viewer);
        assertTrue(first.getTitle().contains("1/2"));
        click(viewer, first, MenuConstants.NEXT);
        assertTrue(last(viewer).getTitle().contains("2/2"));
    }
}
