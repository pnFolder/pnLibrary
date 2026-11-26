package org.pnfolder.pnlibrary.core.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.pnfolder.pnlibrary.core.menu.MenuTestSupport.click;
import static org.pnfolder.pnlibrary.core.menu.MenuTestSupport.loreOf;
import static org.pnfolder.pnlibrary.core.menu.MenuTestSupport.plain;
import static org.pnfolder.pnlibrary.core.menu.MenuTestSupport.slotNamed;
import static org.pnfolder.pnlibrary.core.menu.MenuTestSupport.title;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.core.settings.ChoiceSource;

class LibrarySettingsZoneTest extends LibrarySettingsMenuSupport {
    private static List<String> zoneTooltip(Menu menu) {
        List<String> lines = new ArrayList<>();
        for (String line : loreOf(menu, "Часовой пояс")) {
            lines.add(plain(line));
        }
        return lines;
    }

    private Menu clickZone(TestMenuViewer viewer, String current, ClickType how) {
        Menu screen = settingsScreen(viewer, rows(with("time-zone", current)));
        click(viewer, screen, slotNamed(screen, "Часовой пояс"), how);
        return screen;
    }

    @Test
    void aTimeZoneShowsTheRollingWindowOverEveryZoneWithCountsOffsetLocalTimeAndHints() {
        String text = String.join("\n", zoneTooltip(settingsScreen(new TestMenuViewer(), rows(with("time-zone", "Europe/Berlin")))));
        List<String> all = ChoiceSource.ZONES.getAll();

        assertTrue(text.contains("[ Значение ] Europe/Berlin"), text);
        assertTrue(text.contains("[ Вариант ] " + (all.indexOf("Europe/Berlin") + 1) + " из " + all.size()), text);
        assertTrue(Pattern.compile("\\[ Смещение \\] UTC([+-]\\d\\d:\\d\\d)?").matcher(text).find(), text);
        assertTrue(Pattern.compile("\\[ Сейчас там \\] \\d\\d:\\d\\d").matcher(text).find(), text);
        assertTrue(text.contains("➤ Europe/Berlin"), text);
        assertTrue(text.contains("▲ выше ещё") && text.contains("▼ ниже ещё"), text);
        assertTrue(text.contains("Клик  следующий") && text.contains("Правый клик  предыдущий")
            && text.contains("Shift + клик  весь список"), text);

        String here = java.time.ZoneId.systemDefault().getId();
        String unchanged = String.join("\n", zoneTooltip(settingsScreen(new TestMenuViewer(), rows(with("time-zone", here)))));
        assertFalse(unchanged.substring(0, Math.max(unchanged.indexOf("➤"), 0)).contains("По умолчанию"),
            "no default line while the zone is unchanged");
    }

    @Test
    void theWindowScrollsWithTheValueAndShowsTheLinesAbove() {
        String text = String.join("\n", zoneTooltip(settingsScreen(new TestMenuViewer(), rows(with("time-zone", "Europe/Moscow")))));

        assertTrue(text.contains("▲ выше ещё") && text.contains("▼ ниже ещё"), text);
        assertTrue(text.contains("➤ Europe/Moscow"), text);
    }

    @Test
    void aClickRollsToTheNextZoneARightClickToThePreviousOneAndBothWrap() {
        List<String> all = ChoiceSource.ZONES.getAll();
        clickZone(new TestMenuViewer(), "Europe/Berlin", ClickType.LEFT);
        clickZone(new TestMenuViewer(), "Europe/Berlin", ClickType.RIGHT);
        clickZone(new TestMenuViewer(), all.get(all.size() - 1), ClickType.LEFT);
        clickZone(new TestMenuViewer(), all.get(0), ClickType.RIGHT);

        int berlin = all.indexOf("Europe/Berlin");
        List<String> written = new ArrayList<>();
        for (List<String> change : changes) {
            assertEquals("time-zone", change.get(0));
            written.add(change.get(1));
        }
        assertEquals(Arrays.asList(all.get(berlin + 1), all.get(berlin - 1), all.get(0), all.get(all.size() - 1)), written);
    }

    @Test
    void aClickWritesTheValueAndShowsTheSamePageAgain() {
        TestMenuViewer viewer = new TestMenuViewer();
        clickZone(viewer, "Europe/Berlin", ClickType.LEFT);

        assertTrue(title(last(viewer)).contains("Параметры библиотеки"));
    }

    @Test
    void anUnknownZoneIsShownFirstCountedWithoutANumberAndAClickGoesToTheFirstZone() {
        List<String> all = ChoiceSource.ZONES.getAll();
        List<String> lines = zoneTooltip(settingsScreen(new TestMenuViewer(), rows(with("time-zone", "US/Pacific"))));
        String text = String.join("\n", lines);

        assertTrue(text.contains("[ Вариант ] — из " + all.size()), text);
        assertTrue(lines.stream().anyMatch(line -> line.contains("US/Pacific") && line.contains("➤")), text);

        clickZone(new TestMenuViewer(), "US/Pacific", ClickType.LEFT);
        clickZone(new TestMenuViewer(), "US/Pacific", ClickType.RIGHT);
        List<String> written = new ArrayList<>();
        for (List<String> change : changes) {
            written.add(change.get(1));
        }
        assertEquals(Arrays.asList(all.get(0), all.get(all.size() - 1)), written);
    }

    @Test
    void shiftClickOpensTheFullListOfAllZonesAndWritesNothing() {
        Menu list = zoneList(new TestMenuViewer(), "Europe/Samara");

        assertTrue(changes.isEmpty());
        assertTrue(title(list).endsWith("1/" + pages()), title(list));
    }

    @Test
    void theWheelResetsAChangedZoneAndShowsTheDefaultOnlyThen() {
        TestMenuViewer viewer = new TestMenuViewer();
        Menu screen = settingsScreen(viewer, rows(with("time-zone", "Asia/Tokyo")));
        String text = String.join("\n", zoneTooltip(screen));
        assertTrue(text.contains("[ По умолчанию ]") && text.contains("Колёсико  вернуть значение по умолчанию"), text);

        click(viewer, screen, slotNamed(screen, "Часовой пояс"), ClickType.MIDDLE);

        assertEquals(Collections.singletonList(Arrays.asList("time-zone", null)), changes);
    }
}
