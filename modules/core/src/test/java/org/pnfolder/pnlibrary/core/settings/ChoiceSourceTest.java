package org.pnfolder.pnlibrary.core.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import org.pnfolder.pnlibrary.common.lang.Pair;
import org.junit.jupiter.api.Test;

class ChoiceSourceTest {
    private final ChoiceSource zones = ChoiceSource.ZONES;
    private final Instant winter = Instant.parse("2026-01-15T12:00:00Z");
    private final Instant summer = Instant.parse("2026-07-15T12:00:00Z");

    private static List<String> sorted(List<String> values) {
        List<String> copy = new ArrayList<String>(values);
        Collections.sort(copy);
        return copy;
    }

    @Test
    void zonesAreSortedUniqueAndContainTheOnesPeopleLookFor() {
        assertEquals(sorted(zones.getAll()), zones.getAll());
        assertEquals(zones.getAll().size(), new HashSet<String>(zones.getAll()).size());
        for (String zone : Arrays.asList("Europe/Moscow", "America/New_York", "Asia/Tokyo", "UTC", "Etc/UTC")) {
            assertTrue(zones.getAll().contains(zone), zone);
        }
    }

    @Test
    void legacyAliasesAndTheGmtSeriesAreLeftOut() {
        for (String zone : zones.getAll()) {
            assertFalse(zone.startsWith("SystemV/") || zone.startsWith("Etc/GMT") || zone.startsWith("US/"), zone);
            ZoneId.of(zone);
        }
    }

    @Test
    void theDefaultOrderCoversEveryZoneByOffsetAndThenByName() {
        List<String> shown = zones.view(0, false, "", winter);

        assertEquals(new HashSet<String>(zones.getAll()), new HashSet<String>(shown));
        assertEquals(zones.getAll().size(), shown.size());
        List<String> expected = new ArrayList<String>(shown);
        Collections.sort(expected, new Comparator<String>() {
            @Override
            public int compare(String left, String right) {
                int byOffset = Integer.compare(ChoiceSource.offsetSeconds(left, winter),
                    ChoiceSource.offsetSeconds(right, winter));
                return byOffset != 0 ? byOffset : left.compareTo(right);
            }
        });
        assertEquals(expected, shown);
        List<String> plusThree = new ArrayList<String>();
        for (String zone : shown) {
            if (ChoiceSource.offsetSeconds(zone, winter) == 3 * 3600) {
                plusThree.add(zone);
            }
        }
        assertTrue(plusThree.contains("Europe/Moscow") && plusThree.contains("Africa/Nairobi")
            && plusThree.equals(sorted(plusThree)));
    }

    @Test
    void theOffsetOrderFollowsSummerTimeAndTheAlphabetOrderDoesNot() {
        assertEquals(3600, ChoiceSource.offsetSeconds("Europe/Berlin", winter));
        assertEquals(7200, ChoiceSource.offsetSeconds("Europe/Berlin", summer));
        assertEquals(zones.getAll(), zones.view(1, false, "", winter));
        List<String> labels = new ArrayList<String>();
        for (ChoiceOrder order : zones.getOrders()) {
            labels.add(order.getLabel());
        }
        assertEquals(Arrays.asList("по смещению UTC", "по алфавиту"), labels);
    }

    @Test
    void popularZonesAreValidIdsInTheGivenOrder() {
        List<String> wanted = Arrays.asList(
            "UTC", "Europe/London", "Europe/Berlin", "Europe/Kyiv", "Europe/Minsk", "Europe/Moscow",
            "Europe/Istanbul", "Asia/Dubai", "Asia/Tashkent", "Asia/Almaty", "Asia/Kolkata", "Asia/Bangkok",
            "Asia/Shanghai", "Asia/Tokyo", "Australia/Sydney", "Pacific/Auckland", "America/Sao_Paulo",
            "America/New_York", "America/Chicago", "America/Denver", "America/Los_Angeles");
        List<String> present = new ArrayList<String>();
        for (String zone : wanted) {
            if (zones.getAll().contains(zone)) {
                present.add(zone);
            }
        }

        assertEquals(present, zones.getPopular());
        assertEquals(zones.getPopular(), zones.view(0, true, "", winter));
        assertTrue(zones.getAll().containsAll(zones.getPopular()));
        assertEquals("UTC", zones.getPopular().get(0));
        ChoiceSource custom = new ChoiceSource("x", Arrays.asList("a", "b"), Arrays.asList("b", "missing", "a"));
        assertEquals(Arrays.asList("b", "a"), custom.getPopular());
    }

    @Test
    void aFilterIgnoresCaseAndTheDifferenceBetweenUnderscoreSlashAndSpace() {
        assertEquals(Arrays.asList("America/New_York"), zones.view(0, false, "new york", winter));
        assertEquals(Arrays.asList("America/New_York"), zones.view(0, false, "NEW_YORK", winter));
        assertEquals(Arrays.asList("America/New_York"), zones.view(0, false, "america/new_york", winter));
        assertEquals(Arrays.asList("Europe/Moscow"), zones.view(0, false, "moscow", winter));
        for (String zone : zones.view(0, false, "europe/", winter)) {
            assertTrue(zone.startsWith("Europe/"));
        }
        assertTrue(zones.view(0, false, "no such zone anywhere", winter).isEmpty());
        assertEquals(zones.view(0, false, "", winter), zones.view(0, false, "   ", winter));
        assertEquals(zones.view(0, false, "york", winter), zones.view(0, true, "york", winter),
            "a filter searches everything");
    }

    @Test
    void offsetsAreWrittenAsUtcUtcPlus0300AndUtcMinus0500() {
        assertEquals("UTC", ChoiceSource.offsetText(0));
        assertEquals("UTC+03:00", ChoiceSource.offsetText(3 * 3600));
        assertEquals("UTC-05:00", ChoiceSource.offsetText(-5 * 3600));
        assertEquals("UTC+05:30", ChoiceSource.offsetText(5 * 3600 + 1800));
        assertEquals("UTC-09:30", ChoiceSource.offsetText(-(9 * 3600 + 1800)));
    }

    private static List<Pair<String, String>> lore(String offset, String time) {
        return Arrays.asList(new Pair<String, String>("Смещение", offset), new Pair<String, String>("Сейчас там", time));
    }

    @Test
    void aZoneTellsTheTimeThereAndItsCurrentOffset() {
        assertEquals(lore("UTC+03:00", "15:00"), zones.lore("Europe/Moscow", winter));
        assertEquals(lore("UTC-05:00", "07:00"), zones.lore("America/New_York", winter));
        assertEquals(lore("UTC-04:00", "08:00"), zones.lore("America/New_York", summer));
        assertEquals(lore("UTC", "12:00"), zones.lore("UTC", winter));
        assertTrue(new ChoiceSource("x", Arrays.asList("a")).lore("a").isEmpty());
    }

    @Test
    void theSourceIsFoundAgainByTheNameThatTravelsOverTheLink() {
        SettingSpec spec = SettingsRegistry.get("time-zone");

        assertEquals(zones, ChoiceSource.named(zones.getId()));
        assertNull(ChoiceSource.named("nope"));
        assertEquals(zones.getId(), spec.getChoices().getId());
        assertEquals(SettingKind.CHOICE, spec.getKind());
    }

    @Test
    void theCycleOfZonesIsEveryZoneAndStepsWrapAround() {
        List<String> all = zones.getAll();

        assertEquals(all, zones.getCycle());
        assertEquals(all.get(1), zones.next(all.get(0)));
        assertEquals(all.get(all.size() - 1), zones.previous(all.get(0)));
        assertEquals(all.get(0), zones.next(all.get(all.size() - 1)));
    }

    @Test
    void aZoneOutsideTheCycleComesFirstInTheRollingListAndStepsToTheEnds() {
        List<String> expected = new ArrayList<String>();
        expected.add("US/Pacific");
        expected.addAll(zones.getCycle());
        assertEquals(expected, zones.rolling("US/Pacific"));
        assertEquals(zones.getCycle(), zones.rolling("Europe/Moscow"));
        assertEquals(zones.getCycle().get(0), zones.next("US/Pacific"));
        assertEquals(zones.getCycle().get(zones.getCycle().size() - 1), zones.previous("US/Pacific"));
    }

    @Test
    void aSourceWithoutACycleRollsThroughAllItsValues() {
        ChoiceSource source = new ChoiceSource("letters", Arrays.asList("a", "b", "c"));

        assertEquals(Arrays.asList("a", "b", "c"), source.getCycle());
        assertEquals("a", source.next("c"));
        assertEquals("c", source.previous("a"));
    }
}
