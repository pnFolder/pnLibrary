package org.pnfolder.pnlibrary.core.updates.catalog;

import org.pnfolder.pnlibrary.core.updates.catalog.backup.BackupQuery;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.pnfolder.pnlibrary.core.updates.catalog.ViewFixtures.BUKKIT;
import static org.pnfolder.pnlibrary.core.updates.catalog.ViewFixtures.NOW;
import static org.pnfolder.pnlibrary.core.updates.catalog.ViewFixtures.PROXY;
import static org.pnfolder.pnlibrary.core.updates.catalog.ViewFixtures.catalog;
import static org.pnfolder.pnlibrary.core.updates.catalog.ViewFixtures.clicks;
import static org.pnfolder.pnlibrary.core.updates.catalog.ViewFixtures.hover;
import static org.pnfolder.pnlibrary.core.updates.catalog.ViewFixtures.plain;
import static org.pnfolder.pnlibrary.core.updates.catalog.ViewFixtures.row;
import static org.pnfolder.pnlibrary.core.updates.catalog.ViewFixtures.snapshot;
import static org.pnfolder.pnlibrary.core.updates.catalog.ViewFixtures.text;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.updates.product.UpdateChannel;
import org.pnfolder.pnlibrary.api.updates.release.ReleaseSummary;
import org.pnfolder.pnlibrary.api.updates.state.UpdateSnapshot;
import org.pnfolder.pnlibrary.api.updates.state.UpdateState;

class UpdateCatalogViewTest {
    private static String legacy(Component component) {
        return LegacyComponentSerializer.legacyAmpersand().serialize(component);
    }

    @Test
    void catalogTitleCountsPluginsAndUpdatesAndOffersAnUpdateButtonPerAvailablePlugin() {
        List<Component> lines = catalog(Arrays.asList(
            snapshot("pnlibrary", UpdateState.UPDATE_AVAILABLE, "https://github.com/example/repo/blob/main/notes.md"),
            snapshot("currency", UpdateState.UP_TO_DATE)), BUKKIT);
        String text = text(lines);

        assertEquals("Каталог  2 плагина · 1 к обновлению", plain(lines.get(1)));
        assertEquals(" Нажмите на плагин, чтобы открыть его карточку.", plain(lines.get(2)));
        assertTrue(text.contains("\n  currency v1.0.0\n"), text);
        assertTrue(text.contains("\n  pnlibrary v1.0.0 → v1.1.0 [Обновить]\n"), text);
        List<String> actions = clicks(lines);
        assertTrue(actions.containsAll(Arrays.asList("/pn update pnlibrary", "/pn updates info pnlibrary",
            "https://github.com/example/repo/blob/main/notes.md")));
        assertTrue(actions.containsAll(Arrays.asList("/pn check", "/pn updates available", "/pn updates history", "/pn updates backups")));
        assertFalse(plain(row(lines, "currency")).contains("[Обновить]"));
    }

    @Test
    void aCatalogRowOpensThePluginCardAndExplainsItselfInAMultiLineHover() {
        List<ReleaseSummary> releases = Collections.singletonList(
            new ReleaseSummary("1.1.0", UpdateChannel.STABLE, Instant.parse("2026-10-01T00:00:00Z")));
        UpdateSnapshot snapshot = new UpdateSnapshot("pnlibrary", "1.0.0", "1.1.0", UpdateChannel.STABLE, UpdateState.UPDATE_AVAILABLE,
            17, 8, true, null, "Каталог ответил", null, null, releases, Collections.emptyList(), null, null);

        Component row = row(catalog(Collections.singletonList(snapshot), BUKKIT), "pnlibrary");

        assertEquals("/pn updates info pnlibrary", row.clickEvent().value());
        String card = hover(row);
        assertTrue(card.startsWith("pnlibrary\n"), card);
        assertTrue(card.contains("Статус: доступно обновление"));
        assertTrue(card.contains("Версия: v1.0.0 → v1.1.0"));
        assertTrue(card.contains("Выпущена: 1 октября · 16 д назад"));
        assertFalse(card.contains("Автозагрузка"));
        assertTrue(card.contains("Каталог ответил"));
        assertTrue(card.endsWith("\n\nНажмите, чтобы открыть карточку"), card);
    }

    @Test
    void theTitleReportsPreparedUpdatesAndFailuresAndReadsAsAllClearOtherwise() {
        List<Component> busy = catalog(Arrays.asList(snapshot("a", UpdateState.UPDATE_STAGED), snapshot("b", UpdateState.FAILED),
            snapshot("c", UpdateState.INCOMPATIBLE)), PROXY);
        assertEquals("Каталог  3 плагина · 1 к перезапуску · 2 ошибки", plain(busy.get(1)));
        assertEquals("  a v1.0.0 → v1.1.0  ждёт перезапуска", plain(row(busy, "a")));

        assertEquals("Каталог  1 плагин · всё актуально",
            plain(catalog(Collections.singletonList(snapshot("a", UpdateState.UP_TO_DATE)), PROXY).get(1)));
        List<Component> none = catalog(Collections.singletonList(snapshot("a", UpdateState.UP_TO_DATE)), 1, true, PROXY);
        assertEquals("Обновления  1 плагин · всё актуально", plain(none.get(1)));
        boolean clear = false;
        for (Component line : none) {
            clear |= plain(line).equals("  Все плагины актуальны.");
        }
        assertTrue(clear);
    }

    @Test
    void rowsKeepAWhiteNameGrayVersionsAndOneColoredWord() {
        UpdateSnapshot paused = snapshot("shop", UpdateState.FROZEN, null, UpdateChannel.BETA, "1.1.0");
        Component row = row(catalog(Collections.singletonList(paused), BUKKIT), "shop");

        assertEquals("  shop v1.0.0  beta  пауза", plain(row));
        String legacy = legacy(row);
        assertTrue(legacy.contains("&fshop"), legacy);
        assertTrue(legacy.contains("&7v1.0.0"), legacy);
        assertTrue(legacy.contains("&eпауза"), legacy);
        assertFalse(plain(row(catalog(Collections.singletonList(snapshot("plain", UpdateState.UP_TO_DATE)), BUKKIT), "plain")).contains("stable"));
    }

    @Test
    void withoutAnUpdateRouteTheAvailableVersionIsShownAsPlainText() {
        List<Component> lines = catalog(Collections.singletonList(snapshot("pnlibrary", UpdateState.UPDATE_AVAILABLE)), PROXY);

        assertEquals("  pnlibrary v1.0.0 → v1.1.0", plain(row(lines, "pnlibrary")));
        assertFalse(clicks(lines).contains("/pn update pnlibrary"));
    }

    @Test
    void catalogPaginatesAndTheAvailableFilterExcludesCurrentComponents() {
        List<UpdateSnapshot> snapshots = new ArrayList<>();
        for (int index = 1; index <= 12; index++) {
            snapshots.add(snapshot("module-" + index, UpdateState.UPDATE_AVAILABLE));
        }
        snapshots.add(snapshot("current", UpdateState.UP_TO_DATE));
        List<Component> first = catalog(snapshots, 1, true, PROXY);
        List<Component> second = catalog(snapshots, 2, true, PROXY);

        assertEquals(10, modules(first));
        assertEquals(2, modules(second));
        for (Component line : first.subList(2, first.size())) {
            assertFalse(plain(line).contains("current"));
        }
        assertTrue(text(first).contains("страница 1 из 2 [Следующая]"));
        assertTrue(text(second).contains("страница 2 из 2 [Предыдущая]"));
        assertTrue(clicks(first).contains("/pn update available 2"));
        assertTrue(clicks(second).contains("/pn update available 1"));
        assertTrue(text(catalog(snapshots, Integer.MAX_VALUE, false, PROXY)).contains("На этой странице пусто"));
    }

    private static int modules(List<Component> lines) {
        int count = 0;
        for (Component line : lines) {
            count += plain(line).startsWith("  module-") ? 1 : 0;
        }
        return count;
    }

    @Test
    void onlyValidHttpsChangelogUrlsBecomeClickable() {
        List<Component> safe = catalog(Collections.singletonList(
            snapshot("safe", UpdateState.UPDATE_AVAILABLE, "https://github.com/example/repo/blob/main/notes.md")), PROXY);
        boolean github = false;
        for (String click : clicks(safe)) {
            github |= click.startsWith("https://github.com/");
        }
        assertTrue(github);
        for (String url : Arrays.asList("javascript:alert(1)", "file:///secret", "https://user:password@example.com/notes")) {
            List<Component> unsafe = catalog(Collections.singletonList(snapshot("unsafe", UpdateState.UPDATE_AVAILABLE, url)), PROXY);
            assertFalse(clicks(unsafe).contains(url));
            assertFalse(text(unsafe).contains(url));
        }
    }

    @Test
    void aReaderThatCannotClickGetsTextAndAListOfCommandsInsteadOfButtons() {
        UpdateCatalogRoutes text = BUKKIT.forReader(false, false, true);
        List<List<Component>> pages = Arrays.asList(
            catalog(Collections.singletonList(snapshot("pnlibrary", UpdateState.UPDATE_AVAILABLE, "https://github.com/a/b")), text),
            UpdateCatalogView.info(snapshot("pnlibrary", UpdateState.UPDATE_AVAILABLE), text, null),
            UpdateCatalogView.settings(snapshot("pnlibrary", UpdateState.UP_TO_DATE), text, null),
            ViewFixtures.history(Collections.singletonList(ViewFixtures.event("2026-10-17T09:05:00Z", UpdateState.UPDATE_STAGED, "STAGE",
                "Admin", "pnlibrary", "1.0.0", "1.1.0")), 1, text, 2, null));
        for (List<Component> page : pages) {
            assertTrue(clicks(page).isEmpty(), text(page));
        }
        String catalog = text(pages.get(0));
        assertTrue(catalog.contains("  «Команды»\n    /pn check — проверить обновления"), catalog);
        assertTrue(catalog.contains("pnlibrary v1.0.0 → v1.1.0\n      изменения: https://github.com/a/b"), catalog);
        assertFalse(catalog.contains("Нажмите"));
        assertTrue(text(pages.get(2)).contains("/pn updates set pnlibrary channel <stable|rc|beta|alpha|dev|default>"));
        assertTrue(text(pages.get(3)).contains("следующая: /pn updates history 2"));
    }

    @Test
    void theConsoleVariantUsesOnlyAsciiAndCyrillic() {
        UpdateCatalogRoutes ascii = PROXY.forReader(false, true, true);
        org.pnfolder.pnlibrary.api.updates.backup.UpdateBackup backup = new org.pnfolder.pnlibrary.api.updates.backup.UpdateBackup(
            java.util.UUID.randomUUID(), NOW.minusSeconds(3600), java.util.UUID.randomUUID(), Collections.singletonList(
            new org.pnfolder.pnlibrary.api.updates.backup.UpdateBackupComponent(
                org.pnfolder.pnlibrary.api.updates.product.ProductId.of("pnlibrary"), "2.0.0", "1.0.0")), 1);
        List<Component> all = new ArrayList<>();
        all.addAll(catalog(Arrays.asList(snapshot("pnlibrary", UpdateState.UPDATE_AVAILABLE), snapshot("shop", UpdateState.UP_TO_DATE)), ascii));
        all.addAll(UpdateCatalogView.info(snapshot("pnlibrary", UpdateState.FROZEN), ascii, NOW.plusSeconds(7200), NOW, ZoneOffset.UTC));
        all.addAll(UpdateCatalogView.settings(snapshot("pnlibrary", UpdateState.UP_TO_DATE), ascii, null, NOW, ZoneOffset.UTC));
        all.addAll(ViewFixtures.history(Collections.singletonList(ViewFixtures.event("2026-10-17T09:05:00Z", UpdateState.UPDATE_STAGED,
            "STAGE", "Admin", "pnlibrary", "1.0.0", "1.1.0")), 1, ascii, 1, null));
        all.addAll(UpdateCatalogView.backups(Collections.singletonList(backup), 1, ascii, NOW, ZoneOffset.UTC, new BackupQuery()));
        String text = text(all);

        assertTrue(text.contains("Каталог  2 плагина | 1 к обновлению"), text);
        assertTrue(text.contains("  pnlibrary v1.0.0 -> v1.1.0"), text);
        for (char symbol : text.toCharArray()) {
            assertTrue(symbol < 128 || symbol >= 'А' && symbol <= 'я' || symbol == 'ё' || symbol == 'Ё', text);
        }
    }

    @Test
    void theStatusCardListsServerFactsAndEveryComponentLikeTheCatalog() {
        List<org.pnfolder.pnlibrary.common.lang.Pair<String, String>> facts = Arrays.asList(new org.pnfolder.pnlibrary.common.lang.Pair<>("Ядро", "Paper 1.8.8"), new org.pnfolder.pnlibrary.common.lang.Pair<>("Java", "OpenJDK 8"));
        List<Component> lines = UpdateCatalogView.status(facts, Arrays.asList(snapshot("shop", UpdateState.UP_TO_DATE),
            snapshot("pnlibrary", UpdateState.UPDATE_AVAILABLE)), BUKKIT, "https://discord.gg/example", NOW, ZoneOffset.UTC);
        String text = text(lines);

        assertEquals("Состояние  2 плагина · 1 к обновлению", plain(lines.get(1)));
        assertTrue(text.contains("  Ядро: Paper 1.8.8"));
        assertTrue(text.indexOf("pnlibrary v1.0.0 → v1.1.0") < text.indexOf("shop v1.0.0"));
        assertTrue(clicks(lines).containsAll(Arrays.asList("/pn updates list", "/pn check", "https://discord.gg/example")));
    }

    @Test
    void theJoinNoticeListsOnlyPendingUpdatesAndIsEmptyWhenEverythingIsCurrent() {
        List<UpdateSnapshot> current = new ArrayList<>(Collections.singletonList(snapshot("shop", UpdateState.UP_TO_DATE)));
        assertTrue(UpdateCatalogView.announcement(current, BUKKIT, null, NOW, ZoneOffset.UTC).isEmpty());

        current.add(snapshot("pnlibrary", UpdateState.UPDATE_AVAILABLE));
        List<Component> lines = UpdateCatalogView.announcement(current, BUKKIT, null, NOW, ZoneOffset.UTC);
        String text = text(lines);

        assertEquals("Обновления pnFolder  1 плагин · 1 к обновлению", plain(lines.get(1)));
        assertTrue(text.contains("pnlibrary v1.0.0 → v1.1.0"));
        assertFalse(text.contains("shop"));
        assertTrue(clicks(lines).containsAll(Arrays.asList("/pn update pnlibrary", "/pn updates available")));
    }
}
