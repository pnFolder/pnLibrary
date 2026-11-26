package org.pnfolder.pnlibrary.core.commands.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.pointer.Pointers;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pnfolder.pnlibrary.api.audience.AudienceExtensions;
import org.pnfolder.pnlibrary.api.commands.CommandContext;
import org.pnfolder.pnlibrary.api.commands.CommandSender;
import org.pnfolder.pnlibrary.api.commands.definition.CommandDefinition;
import org.pnfolder.pnlibrary.core.commands.engine.CommandTreeRouter;
import org.pnfolder.pnlibrary.core.commands.engine.ExecutableRoute;
import org.pnfolder.pnlibrary.core.settings.LibrarySettings;
import org.pnfolder.pnlibrary.core.settings.SettingPickerHook;
import org.pnfolder.pnlibrary.core.settings.SettingsStore;

class SettingsCommandTest {
    @TempDir
    Path directory;

    private SettingsStore store;
    private final SettingPickerHook hook = new SettingPickerHook();
    private CommandDefinition definition;
    private boolean console = true;
    private final List<String> sent = new ArrayList<String>();
    private final CommandSender sender = new CommandSender() {
        @NotNull
        @Override
        public Pointers pointers() {
            return AudienceExtensions.senderPointers("Console", console ? null : UUID.randomUUID(), permission -> true);
        }

        @Override
        public void sendMessage(@NotNull Component message) {
            sent.add(PlainTextComponentSerializer.plainText().serialize(message));
        }
    };

    private SettingsStore store() {
        if (store == null) {
            store = new SettingsStore(directory.resolve("data/settings.json"));
            definition = new SettingsCommand(store, hook).definition();
        }
        return store;
    }

    private String run(String... words) {
        store();
        sent.clear();
        ExecutableRoute route = (ExecutableRoute) new CommandTreeRouter().route(definition,
                new CommandContext(sender, Arrays.asList(words), "settings", String.join(" ", words)));
        route.getNode().getExecution().execute(route.getContext()).toCompletableFuture().join();
        return String.join("\n", sent);
    }

    private List<String> suggest(String... words) {
        store();
        return new CommandTreeRouter().suggest(definition,
                new CommandContext(sender, Arrays.asList(words), "settings", String.join(" ", words))).toCompletableFuture().join();
    }

    private static List<String> sorted(List<String> values) {
        List<String> copy = new ArrayList<String>(values);
        Collections.sort(copy);
        return copy;
    }

    @Test
    @DisplayName("the list shows every setting with its value and marks the changed ones")
    void theListShowsEverySettingWithItsValueAndMarksTheChangedOnes() {
        store().set("privacy", "true");

        String first = run("list");
        String second = run("list", "2");

        assertTrue(first.contains("privacy = включено  (по умолчанию выключено)"), first);
        assertTrue(first.contains("history-retention-days = 30"), first);
        assertTrue(second.contains("страница 2/2"), second);
        assertTrue(second.contains("updates.read-timeout = 30с"), second);
        assertEquals(first, run(), "the bare command is the list");
    }

    @Test
    @DisplayName("get shows value default limits and when a change works")
    void getShowsValueDefaultLimitsAndWhenAChangeWorks() {
        String text = run("get", "history-retention-days");

        assertTrue(text.contains("значение: 30 (по умолчанию)"), text);
        assertTrue(text.contains("допустимо: от 30 до 365"), text);
        assertTrue(text.contains("заработает: после перезапуска"), text);
        assertTrue(run("get", "downloads.enabled").contains("заработает: сразу"));
        assertTrue(run("get", "nope").contains("Такой настройки нет"));
    }

    @Test
    @DisplayName("set saves the value and says when it starts to work")
    void setSavesTheValueAndSaysWhenItStartsToWork() {
        String live = run("set", "downloads.automatic", "off");
        String later = run("set", "history-retention-days", "90");

        assertFalse(store.current().getDownloadsAutomatic());
        assertEquals(90, store.current().getHistoryRetentionDays());
        assertTrue(live.contains("downloads.automatic: включено → выключено. Сохранено."), live);
        assertFalse(live.contains("перезапуска"), live);
        assertTrue(later.contains("30 → 90"), later);
        assertTrue(later.contains("после перезапуска"), later);
    }

    @Test
    @DisplayName("a text with spaces is taken whole")
    void aTextWithSpacesIsTakenWhole() {
        run("set", "no-permission-message", "Нет", "права", "на", "это");

        assertEquals("Нет права на это", store.current().getNoPermissionMessage());
    }

    @Test
    @DisplayName("a wrong value or key is answered with the reason and nothing changes")
    void aWrongValueOrKeyIsAnsweredWithTheReasonAndNothingChanges() {
        String range = run("set", "history-retention-days", "7");
        String word = run("set", "privacy", "maybe");
        String time = run("set", "updates.check-interval", "5m");
        String key = run("set", "nope", "1");

        assertTrue(range.contains("Не изменено") && range.contains("от 30 до 365"), range);
        assertTrue(word.contains("Не изменено") && word.contains("вкл или выкл"), word);
        assertTrue(time.contains("Не изменено") && time.contains("вне допустимых границ"), time);
        assertTrue(key.contains("Такой настройки нет"), key);
        assertEquals(new LibrarySettings(), store.current());
    }

    @Test
    @DisplayName("reset returns a setting to its default")
    void resetReturnsASettingToItsDefault() {
        run("set", "privacy", "true");

        String text = run("reset", "privacy");

        assertTrue(text.contains("включено → выключено"), text);
        assertFalse(store.current().getPrivacy());
        assertTrue(run("reset", "nope").contains("Такой настройки нет"));
    }

    @Test
    @DisplayName("keys and values are suggested")
    void keysAndValuesAreSuggested() {
        List<String> keys = suggest("set", "");
        assertTrue(keys.contains("downloads.enabled") && keys.contains("privacy") && keys.contains("updates.check-interval"), keys.toString());
        assertEquals(Arrays.asList("downloads.destinations.cache", "downloads.destinations.data-folder"),
                sorted(suggest("get", "downloads.dest")));
        assertEquals(Arrays.asList("включено", "выключено"), sorted(suggest("set", "privacy", "")));
        assertTrue(suggest("set", "updates.check-interval", "").contains("6h"));
        run("set", "privacy", "true");
        assertEquals(Arrays.asList("privacy"), suggest("reset", ""));
    }

    @Test
    @DisplayName("a switch is shown and understood in Russian everywhere in the command")
    void aSwitchIsShownAndUnderstoodInRussianEverywhereInTheCommand() {
        assertTrue(run("list").contains("privacy = выключено"), "the list");
        String get = run("get", "privacy");
        assertTrue(get.contains("значение: выключено (по умолчанию)"), get);
        assertFalse(get.contains("false") || get.contains("true"), get);
        for (String word : Arrays.asList("вкл", "включено", "да", "on", "true")) {
            run("reset", "privacy");
            run("set", "privacy", word);
            assertTrue(store.current().getPrivacy(), word);
        }
        for (String word : Arrays.asList("выкл", "выключено", "нет", "off", "false")) {
            run("set", "privacy", "включено");
            run("set", "privacy", word);
            assertFalse(store.current().getPrivacy(), word);
        }
        run("set", "privacy", "включено");
        assertTrue(run("get", "privacy").contains("по умолчанию: выключено"));
    }

    @Test
    @DisplayName("a time zone is completed from the list of zones")
    void aTimeZoneIsCompletedFromTheListOfZones() {
        List<String> zones = suggest("set", "time-zone", "");

        assertTrue(zones.contains("Europe/Moscow") && zones.contains("UTC") && zones.contains("America/New_York"), String.valueOf(zones.size()));
        assertEquals(zones.size(), new HashSet<String>(zones).size());
    }

    @Test
    @DisplayName("pick opens the list of a choice setting for a player with the words after the key as the filter")
    void pickOpensTheListOfAChoiceSettingForAPlayerWithTheWordsAfterTheKeyAsTheFilter() {
        console = false;
        final List<String> opened = new ArrayList<String>();
        hook.setOpener((context, key, filter) -> opened.add(key + "|" + filter));

        run("pick", "time-zone");
        run("pick", "time-zone", "new", "york");

        assertEquals(Arrays.asList("time-zone|", "time-zone|new york"), opened);
    }

    @Test
    @DisplayName("pick refuses the console, a setting without a list and a platform without menus")
    void pickRefusesTheConsoleASettingWithoutAListAndAPlatformWithoutMenus() {
        final List<String> opened = new ArrayList<String>();
        hook.setOpener((context, key, filter) -> opened.add(key));

        assertTrue(run("pick", "time-zone").contains("меню игрока"));
        console = false;
        assertTrue(run("pick", "privacy").contains("Нет настройки с выбором из списка"));
        hook.setOpener(null);
        assertTrue(run("pick", "time-zone").contains("Меню здесь недоступно"));
        assertTrue(opened.isEmpty());
    }

    @Test
    @DisplayName("pick completes only the settings that have a list")
    void pickCompletesOnlyTheSettingsThatHaveAList() {
        assertEquals(Arrays.asList("time-zone"), suggest("pick", ""));
    }
}
