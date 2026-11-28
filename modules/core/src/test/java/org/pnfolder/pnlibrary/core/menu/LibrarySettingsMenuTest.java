package org.pnfolder.pnlibrary.core.menu;

import org.pnfolder.pnlibrary.core.menu.session.ClickType;
import org.pnfolder.pnlibrary.core.menu.session.MenuClickHandler;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.pnfolder.pnlibrary.core.menu.MenuTestSupport.click;
import static org.pnfolder.pnlibrary.core.menu.MenuTestSupport.loreOf;
import static org.pnfolder.pnlibrary.core.menu.MenuTestSupport.nameAt;
import static org.pnfolder.pnlibrary.core.menu.MenuTestSupport.plain;
import static org.pnfolder.pnlibrary.core.menu.MenuTestSupport.slotNamed;
import static org.pnfolder.pnlibrary.core.menu.MenuTestSupport.title;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.core.menu.style.MenuConstants;
import org.pnfolder.pnlibrary.core.settings.ChoiceSource;

class LibrarySettingsMenuTest extends LibrarySettingsMenuSupport {
    @Test
    void aSwitchShowsRussianWordsAndMarksTheDefaultOptionInGreyItalics() {
        List<String> lore = loreOf(settingsScreen(new TestMenuViewer(), rows()), "Приватность отчёта");
        String text = text(lore);

        assertTrue(text.contains("включено") && text.contains("выключено (по умолчанию)"), text);
        assertFalse(text.contains("false") || text.contains("true"), text);
        assertFalse(text.contains("По умолчанию"), "the mark replaces the separate default line");
        assertEquals(1, lore.stream().filter(line -> line.contains("(по умолчанию)")).count());
        String marked = lore.stream().filter(line -> line.contains("(по умолчанию)")).findFirst().get();
        assertTrue(marked.endsWith("§o(по умолчанию)"), "the note is italic and comes last");
        assertTrue(marked.indexOf("§o") > marked.indexOf("выключено"));
    }

    @Test
    void theDefaultMarkFollowsTheDefaultNotTheCurrentValue() {
        String text = text(loreOf(settingsScreen(new TestMenuViewer(), rows(with("updates.restart.auto", "true"))), "Перезапускать самой"));

        assertTrue(text.contains("выключено (по умолчанию)"), text);
        assertTrue(text.contains("➤ включено"), text);
    }

    @Test
    void aNumberShowsItsDefaultOnlyWhenItWasChanged() {
        String unchanged = text(loreOf(settingsScreen(new TestMenuViewer(), rows()), "Хранение истории"));
        String after = text(loreOf(settingsScreen(new TestMenuViewer(), rows(with("history-retention-days", "90"))), "Хранение истории"));

        assertFalse(unchanged.contains("По умолчанию"), unchanged);
        assertTrue(after.contains("По умолчанию") && after.contains("90"), after);
    }

    @Test
    void aTimeZoneOpensOneListWithTheTimeAndOffsetOfEveryZoneTheCurrentOneGreenAndNotClickable() {
        TestMenuViewer viewer = new TestMenuViewer();
        Menu list = zoneList(viewer);
        ChoiceSource zones = ChoiceSource.ZONES;

        assertTrue(title(list).contains("Часовой пояс "), title(list));
        assertTrue(title(list).endsWith("1/" + pages()), title(list));
        String first = nameAt(list, 0);
        assertEquals(zones.view(0, false, "").get(0), first, "the default order is by offset");
        String lore = text(loreOf(list, first));
        assertTrue(Pattern.compile("\\[ Сейчас там \\] \\d\\d:\\d\\d").matcher(lore).find(), lore);
        assertTrue(Pattern.compile("\\[ Смещение \\] UTC([+-]\\d\\d:\\d\\d)?").matcher(lore).find(), lore);
        List<String> shown = zones.view(0, false, "");
        if (shown.indexOf("Europe/Berlin") / MenuConstants.PAGE_SIZE == 0) {
            assertNull(list.getItems().get(slotNamed(list, "Europe/Berlin")).getHandler());
        }
        assertEquals("Ввести в чате", nameAt(list, MenuConstants.DATE_FILTER));
        assertEquals("Сортировка", nameAt(list, MenuConstants.FILTER));
        assertEquals("Популярные", nameAt(list, 51));
        assertEquals("Поиск", nameAt(list, 52));
    }

    @Test
    void theSortButtonCyclesTheOrderWithATooltipOfOptionsAndTheOrderSurvivesPaging() {
        TestMenuViewer viewer = new TestMenuViewer();
        Menu list = zoneList(viewer);
        ChoiceSource zones = ChoiceSource.ZONES;
        String tooltip = text(loreOf(list, "Сортировка"));

        assertTrue(tooltip.contains("➤ по смещению UTC") && tooltip.contains("по алфавиту"), tooltip);
        click(viewer, list, MenuConstants.FILTER);
        Menu alphabetical = last(viewer);
        assertEquals(zones.getAll().get(0), nameAt(alphabetical, 0));
        assertTrue(text(loreOf(alphabetical, "Сортировка")).contains("➤ по алфавиту"));

        click(viewer, alphabetical, MenuConstants.NEXT);
        assertEquals(zones.getAll().get(MenuConstants.PAGE_SIZE), nameAt(last(viewer), 0), "the next page keeps the alphabet");
        assertTrue(title(last(viewer)).endsWith("2/" + pages()));

        click(viewer, alphabetical, MenuConstants.FILTER);
        assertEquals(zones.view(0, false, "").get(0), nameAt(last(viewer), 0), "it cycles back");
    }

    @Test
    void thePopularButtonShowsTheCuratedZonesInTheirOrderAndHidesTheSort() {
        TestMenuViewer viewer = new TestMenuViewer();
        Menu list = zoneList(viewer);
        assertTrue(text(loreOf(list, "Популярные")).contains("➤ Все"));

        click(viewer, list, 51);
        Menu popular = last(viewer);

        assertTrue(title(popular).contains("популярные"), title(popular));
        List<String> curated = ChoiceSource.ZONES.getPopular();
        for (int index = 0; index < curated.size(); index++) {
            assertEquals(curated.get(index), nameAt(popular, index));
        }
        assertNull(handler(popular, MenuConstants.FILTER));
        assertTrue(text(loreOf(popular, "Популярные")).contains("➤ Популярные"));

        click(viewer, popular, 51);
        assertEquals(ChoiceSource.ZONES.view(0, false, "").get(0), nameAt(last(viewer), 0));
    }

    private static MenuClickHandler handler(Menu menu, int slot) {
        MenuItem item = menu.getItems().get(slot);
        return item == null ? null : item.getHandler();
    }

    @Test
    void aClickOnAZoneWritesTheSetting() {
        TestMenuViewer viewer = new TestMenuViewer();
        Menu list = zoneList(viewer);
        click(viewer, list, 51);
        Menu popular = last(viewer);

        click(viewer, popular, slotNamed(popular, "Europe/Moscow"));

        assertEquals(Collections.singletonList(Arrays.asList("time-zone", "Europe/Moscow")), changes);
        assertTrue(title(last(viewer)).contains("Параметры библиотеки"), "it returns to the settings");
    }

    @Test
    void pickOpensTheListNarrowedByAFilterTheResetButtonAndTheWheelOnSearchClearIt() {
        TestMenuViewer viewer = new TestMenuViewer();
        LibraryMenu menu = new LibraryMenu(data(rows()), new ImmediateHost());

        menu.pickSetting(viewer, "time-zone", "new york");
        Menu found = last(viewer);
        assertTrue(title(found).contains("«new york»"), title(found));
        assertEquals("America/New_York", nameAt(found, 0));
        assertNull(handler(found, 1), "only one zone matches");
        assertEquals("Сбросить поиск", nameAt(found, 51));

        click(viewer, found, 51);
        assertFalse(title(last(viewer)).contains("«"));
        assertNotNull(last(viewer).getItems().get(1).getHandler());

        menu.pickSetting(viewer, "time-zone", "new york");
        click(viewer, last(viewer), 52, ClickType.MIDDLE);
        assertFalse(title(last(viewer)).contains("«"));
    }

    @Test
    void aSearchWithNoMatchSaysNothingWasFoundAndAWrongKeyIsAnswered() {
        TestMenuViewer viewer = new TestMenuViewer();
        LibraryMenu menu = new LibraryMenu(data(rows()), new ImmediateHost());

        menu.pickSetting(viewer, "time-zone", "qqqq");
        boolean found = false;
        for (MenuItem item : last(viewer).getItems().values()) {
            found |= item.getItem() != null && plain(item.getItem().getName()).equals("Ничего не найдено");
        }
        assertTrue(found);

        int before = viewer.opened.size();
        menu.pickSetting(viewer, "privacy", "");
        assertEquals(before, viewer.opened.size(), "a setting without a list opens nothing");
    }

    @Test
    void theSearchButtonPutsTheCommandWithTheKeyIntoTheChat() {
        TestMenuViewer viewer = new TestMenuViewer();
        Menu list = zoneList(viewer);

        click(viewer, list, 52);

        List<String> commands = new ArrayList<>();
        for (net.kyori.adventure.text.Component line : viewer.sent) {
            if (line.clickEvent() != null) {
                commands.add(line.clickEvent().value());
            }
        }
        assertEquals(Collections.singletonList("/pn settings pick time-zone "), commands);
    }
}
