package org.pnfolder.pnlibrary.core.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class SettingsRegistryTest {
    private static List<String> fields() {
        List<String> names = new ArrayList<>();
        for (Field field : LibrarySettings.class.getDeclaredFields()) {
            if (field.getType() == RestartSettings.class) {
                names.addAll(restartFields());
            } else if (!Modifier.isStatic(field.getModifiers())) {
                names.add(field.getName());
            }
        }
        return names;
    }

    /** The restart group is one field of {@link LibrarySettings}; each of its values has its own setting. */
    private static List<String> restartFields() {
        return Arrays.asList("restart.auto", "restart.max-players", "restart.quiet-time");
    }

    private static List<String> keys() {
        List<String> keys = new ArrayList<>();
        for (SettingSpec spec : SettingsRegistry.getAll()) {
            keys.add(spec.getKey());
        }
        return keys;
    }

    private static SettingSpec spec(String key) {
        SettingSpec spec = SettingsRegistry.get(key);
        if (spec == null) {
            throw new IllegalStateException(key);
        }
        return spec;
    }

    @Test
    void everySettingOfTheLibraryHasExactlyOneEntryInTheRegistry() {
        List<String> fields = fields();
        assertTrue(!fields.isEmpty());
        List<String> covered = new ArrayList<>();
        for (SettingSpec spec : SettingsRegistry.getAll()) {
            covered.add(spec.getField());
        }
        Collections.sort(fields);
        List<String> sortedCovered = new ArrayList<>(covered);
        Collections.sort(sortedCovered);
        assertEquals(fields, sortedCovered, "a field without an entry (or an entry without a field)");
        assertEquals(covered.size(), new HashSet<>(covered).size(), "two entries for one field");
        assertEquals(SettingsRegistry.getAll().size(), new HashSet<>(keys()).size(), "a key is used twice");
    }

    @Test
    void theDefaultOfEverySettingIsAcceptedByThatSetting() {
        for (SettingSpec spec : SettingsRegistry.getAll()) {
            assertEquals(spec.getDefault(), spec.normalize(spec.getDefault()), spec.getKey());
            assertEquals(new LibrarySettings(), spec.apply(new LibrarySettings(), spec.getDefault()), spec.getKey());
            assertTrue(!spec.getDescription().trim().isEmpty() && !spec.getTitle().trim().isEmpty(), spec.getKey());
        }
        assertEquals(new LibrarySettings().toConfig().getHistoryRetentionDays(), 30);
    }

    @Test
    void theDocsDescribeEverySettingAndEveryPermissionOfPluginYml() throws IOException {
        String docs = new String(Files.readAllBytes(Paths.get("../../docs/settings.md")), StandardCharsets.UTF_8);
        List<String> missing = new ArrayList<>();
        for (String key : keys()) {
            if (!docs.contains("`" + key + "`")) {
                missing.add(key);
            }
        }
        assertTrue(missing.isEmpty(), "docs/settings.md does not describe: " + missing);
        List<String> declared = new ArrayList<>();
        Pattern node = Pattern.compile("^  (pnlibrary\\.[a-z.]+):\\s*$");
        for (String line : Files.readAllLines(Paths.get("../../platforms/bukkit/runtime/src/main/resources/plugin.yml"))) {
            Matcher matcher = node.matcher(line);
            if (matcher.find()) {
                declared.add(matcher.group(1));
            }
        }
        assertTrue(declared.size() >= 10, "plugin.yml permissions were not found");
        List<String> unlisted = new ArrayList<>();
        for (String permission : declared) {
            if (!docs.contains(permission)) {
                unlisted.add(permission);
            }
        }
        assertTrue(unlisted.isEmpty(), "docs/settings.md does not mention: " + unlisted);
    }

    @Test
    void switchesReadTheUsualWordsAndRefuseTheRest() {
        SettingSpec spec = spec("privacy");
        assertEquals("true", spec.normalize("ON"));
        assertEquals("false", spec.normalize(" нет "));
        assertThrows(SettingException.class, () -> spec.normalize("maybe"));
    }

    @Test
    void numbersStayInsideTheirLimits() {
        SettingSpec spec = spec("history-retention-days");
        assertEquals("365", spec.normalize("365"));
        SettingException tooBig = assertThrows(SettingException.class, () -> spec.normalize("366"));
        assertTrue(String.valueOf(tooBig.getMessage()).contains("от 30 до 365"));
        assertThrows(SettingException.class, () -> spec.normalize("30.5"));
        assertThrows(SettingException.class, () -> spec.normalize(""));
    }

    @Test
    void timesAreTypedWithUnitsInEitherLanguageAndWrittenInTheShortestForm() {
        SettingSpec spec = spec("updates.check-interval");
        assertEquals("1ч30м", spec.normalize("90m"));
        assertEquals("1ч30м", spec.normalize("1h 30 мин"));
        assertEquals("6ч", spec.normalize("6h"));
        assertEquals(Duration.ofHours(5), spec.apply(new LibrarySettings(), "5h").getUpdateCheckInterval());
        assertThrows(SettingException.class, () -> spec.normalize("5m"));
        assertThrows(SettingException.class, () -> spec.normalize("7h"));
        assertThrows(SettingException.class, () -> spec.normalize("8d"));
        assertThrows(SettingException.class, () -> spec.normalize("6"));
    }

    @Test
    void restartSettingsDefaultAndNeverRestartingAlone() {
        RestartSettings restart = new LibrarySettings().getRestart();
        assertTrue(!restart.getAuto());
        assertEquals(0, restart.getMaxPlayers());
        assertEquals(Duration.ofMinutes(2), restart.getQuietTime());
    }

    @Test
    void restartSettingsStayInsideTheirLimitsAndChangeOnlyTheirOwnValue() {
        SettingSpec players = spec("updates.restart.max-players");
        assertEquals("200", players.normalize("200"));
        assertThrows(SettingException.class, () -> players.normalize("201"));
        assertThrows(SettingException.class, () -> players.normalize("-1"));
        SettingSpec quiet = spec("updates.restart.quiet-time");
        assertEquals("10с", quiet.normalize("10s"));
        assertEquals("1ч", quiet.normalize("1h"));
        assertThrows(SettingException.class, () -> quiet.normalize("9s"));
        assertThrows(SettingException.class, () -> quiet.normalize("2h"));
        LibrarySettings changed = spec("updates.restart.auto").apply(new LibrarySettings(), "true");
        assertTrue(changed.getRestart().getAuto());
        assertEquals(Duration.ofMinutes(2), changed.getRestart().getQuietTime());
    }

    @Test
    void aTimeZoneIsChecked() {
        SettingSpec zone = spec("time-zone");
        assertEquals("Europe/Moscow", zone.normalize(" Europe/Moscow "));
        assertThrows(SettingException.class, () -> zone.normalize("Mars/Base"));
    }

    @Test
    void aSwitchIsReadInRussianAndShownInRussianWhileItIsStoredAsTrueOrFalse() {
        SettingSpec spec = spec("privacy");
        for (String word : Arrays.asList("вкл", "включено", "Да", "true", "on")) {
            assertEquals("true", spec.normalize(word), word);
        }
        for (String word : Arrays.asList("выкл", "выключено", "нет", "false", "off")) {
            assertEquals("false", spec.normalize(word), word);
        }
        assertEquals("включено", spec.display("true"));
        assertEquals("выключено", spec.display(spec.getDefault()));
        assertEquals("30", spec("history-retention-days").display("30"));
    }

    @Test
    void theTimeZoneIsAChoiceFromTheListOfZonesAndStillAcceptsAnyKnownZoneId() {
        SettingSpec zone = spec("time-zone");

        assertEquals(SettingKind.CHOICE, zone.getKind());
        assertTrue(zone.getChoices().getAll().contains("Europe/Moscow"));
        assertEquals("Asia/Tokyo", zone.normalize("Asia/Tokyo"));
    }
}
