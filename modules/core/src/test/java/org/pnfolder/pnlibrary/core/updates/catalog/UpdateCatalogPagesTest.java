package org.pnfolder.pnlibrary.core.updates.catalog;

import org.pnfolder.pnlibrary.core.updates.catalog.backup.BackupQuery;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.pnfolder.pnlibrary.core.updates.catalog.ViewFixtures.BUKKIT;
import static org.pnfolder.pnlibrary.core.updates.catalog.ViewFixtures.NOW;
import static org.pnfolder.pnlibrary.core.updates.catalog.ViewFixtures.PROXY;
import static org.pnfolder.pnlibrary.core.updates.catalog.ViewFixtures.catalog;
import static org.pnfolder.pnlibrary.core.updates.catalog.ViewFixtures.clicks;
import static org.pnfolder.pnlibrary.core.updates.catalog.ViewFixtures.hover;
import static org.pnfolder.pnlibrary.core.updates.catalog.ViewFixtures.plain;
import static org.pnfolder.pnlibrary.core.updates.catalog.ViewFixtures.plains;
import static org.pnfolder.pnlibrary.core.updates.catalog.ViewFixtures.row;
import static org.pnfolder.pnlibrary.core.updates.catalog.ViewFixtures.snapshot;
import static org.pnfolder.pnlibrary.core.updates.catalog.ViewFixtures.titleOf;

import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.chat.style.ChatPalette;
import org.pnfolder.pnlibrary.api.chat.ChatNotice;
import org.pnfolder.pnlibrary.api.chat.style.ChatTone;
import org.pnfolder.pnlibrary.api.updates.backup.UpdateBackup;
import org.pnfolder.pnlibrary.api.updates.backup.UpdateBackupComponent;
import org.pnfolder.pnlibrary.api.updates.history.UpdateHistoryEntry;
import org.pnfolder.pnlibrary.api.updates.product.ProductId;
import org.pnfolder.pnlibrary.api.updates.state.UpdateState;

class UpdateCatalogPagesTest {
    @Test
    void backupsShowAgeAndOpenSeparateConfirmationFlowsRatherThanExecutingARestore() {
        UUID id = UUID.randomUUID();
        UpdateBackup backup = new UpdateBackup(id, NOW.minusSeconds(4L * 86_400), UUID.randomUUID(),
            Collections.singletonList(new UpdateBackupComponent(ProductId.of("pnlibrary"), "2.0.0", "1.0.0")), 2);

        List<Component> lines = UpdateCatalogView.backups(Collections.singletonList(backup), 1, BUKKIT, NOW, ZoneOffset.UTC, new BackupQuery());
        List<String> actions = clicks(lines);

        assertEquals("Резервы  1 набор", plain(lines.get(1)));
        Component row = row(lines, "pnlibrary");
        assertEquals("  pnlibrary v1.0.0  4 д назад [Откат] [+конфиги]", plain(row));
        assertTrue(hover(row).contains("Вернёт: pnlibrary v1.0.0 (сейчас v2.0.0)"));
        assertTrue(actions.contains("/pn updates rollback " + id));
        assertTrue(actions.contains("/pn updates rollback " + id + " configs"));
        for (String action : actions) {
            assertTrue(!action.endsWith(" confirm"));
        }
        boolean none = false;
        for (Component line : UpdateCatalogView.backups(Collections.emptyList(), 1, BUKKIT)) {
            none |= plain(line).contains("Резервов пока нет");
        }
        assertTrue(none);
    }

    @Test
    void everyPageStartsWithAnEmptyLineAndABoldAccentTitle() {
        List<List<Component>> pages = Arrays.asList(
            catalog(Collections.singletonList(snapshot("shop", UpdateState.UP_TO_DATE)), BUKKIT),
            ViewFixtures.history(Collections.emptyList()),
            UpdateCatalogView.backups(Collections.emptyList(), 1, BUKKIT),
            UpdateCatalogView.info(snapshot("shop", UpdateState.UP_TO_DATE), BUKKIT, null),
            UpdateCatalogView.settings(snapshot("shop", UpdateState.UP_TO_DATE), BUKKIT, null),
            UpdateCatalogView.confirmation("Откат", Collections.singletonList("Вернутся JAR."), "[Да]", "/pn x", "Выполнить", BUKKIT));
        for (List<Component> page : pages) {
            assertEquals("", plain(page.get(0)));
            Component title = titleOf(page.get(1));
            assertTrue(title.hasDecoration(TextDecoration.BOLD), plain(page.get(1)));
            assertEquals(ChatPalette.ACCENT, title.color());
        }
        for (Component line : UpdateCatalogView.notice(new ChatNotice(ChatTone.SUCCESS, "Готово"), BUKKIT)) {
            assertTrue(!plain(line).isEmpty());
        }
    }

    @Test
    void onlyTheTitleWordIsBoldNeverTheCountersBesideIt() {
        Component header = catalog(Collections.singletonList(snapshot("shop", UpdateState.UP_TO_DATE)), BUKKIT).get(1);
        String legacy = LegacyComponentSerializer.legacyAmpersand().serialize(header);

        assertTrue(legacy.startsWith("&d&lКаталог&r"), legacy);
        Matcher bold = Pattern.compile("&l").matcher(legacy);
        int count = 0;
        while (bold.find()) {
            count++;
        }
        assertEquals(1, count, legacy);
    }

    @Test
    void everyPaletteRoleKeepsItsOwnColorOnServersWithoutRgb() {
        Map<TextColor, NamedTextColor> expected = new LinkedHashMap<>();
        expected.put(ChatPalette.TEXT, NamedTextColor.WHITE);
        expected.put(ChatPalette.SOFT, NamedTextColor.GRAY);
        expected.put(ChatPalette.LABEL, NamedTextColor.LIGHT_PURPLE);
        expected.put(ChatPalette.FAINT, NamedTextColor.DARK_GRAY);
        expected.put(ChatPalette.ACCENT, NamedTextColor.LIGHT_PURPLE);
        expected.put(ChatPalette.OK, NamedTextColor.GREEN);
        expected.put(ChatPalette.INFO, NamedTextColor.AQUA);
        expected.put(ChatPalette.WARN, NamedTextColor.YELLOW);
        expected.put(ChatPalette.ORANGE, NamedTextColor.GOLD);
        expected.put(ChatPalette.DANGER, NamedTextColor.RED);
        for (Map.Entry<TextColor, NamedTextColor> entry : expected.entrySet()) {
            assertEquals(entry.getValue(), NamedTextColor.nearestTo(entry.getKey()), entry.getKey().asHexString());
        }
    }

    @Test
    void buttonsHaveGrayBracketsAroundAColoredLabel() {
        List<Component> lines = catalog(Collections.singletonList(snapshot("shop", UpdateState.UP_TO_DATE)), BUKKIT);
        String footer = LegacyComponentSerializer.legacyAmpersand().serialize(row(lines, "[Проверить]"));

        assertTrue(footer.contains("&7[&aПроверить&7]"), footer);
        assertTrue(footer.contains("&7[&dИстория&7]"), footer);
    }

    @Test
    void noticesMarkTheirToneAndKeepTheDetailOnItsOwnLine() {
        List<Component> lines = UpdateCatalogView.notice(new ChatNotice(ChatTone.FAILURE, "Не удалось подготовить откат.", "HTTP 404"), BUKKIT);
        assertEquals(Arrays.asList("  ! Не удалось подготовить откат.", "    HTTP 404"), plains(lines));
        assertEquals("  √ Готово", plain(UpdateCatalogView.notice(new ChatNotice(ChatTone.SUCCESS, "Готово"), BUKKIT).get(0)));
        List<Component> console = UpdateCatalogView.notice(new ChatNotice(ChatTone.PROGRESS, "Проверяю обновления…"),
            PROXY.forReader(false, true, true));
        assertEquals("  >> Проверяю обновления...", plain(console.get(0)));
    }

    @Test
    void aConfirmationCardOffersOneButtonOrTheCommandToTypeWhenTheReaderCannotClick() {
        List<Component> card = UpdateCatalogView.confirmation("Перезапуск сервера", Collections.singletonList("Игроков на сервере: 3."),
            "[Подтвердить перезапуск]", "/pn update confirm abc", "Выполнить", BUKKIT);
        assertEquals("Перезапуск сервера", plain(card.get(1)));
        assertEquals(Collections.singletonList("/pn update confirm abc"), clicks(card));

        List<Component> text = UpdateCatalogView.confirmation("Перезапуск сервера", Collections.singletonList("Игроков на сервере: 3."),
            "[Подтвердить перезапуск]", "/pn update confirm abc", "Выполнить", BUKKIT.forReader(false, false, true));
        assertTrue(clicks(text).isEmpty());
        assertEquals("  Подтвердить: /pn update confirm abc", plain(text.get(text.size() - 1)));
    }
}
