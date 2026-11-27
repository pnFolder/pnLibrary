package org.pnfolder.pnlibrary.core.updates.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.pnfolder.pnlibrary.core.updates.catalog.ViewFixtures.BUKKIT;
import static org.pnfolder.pnlibrary.core.updates.catalog.ViewFixtures.clicks;
import static org.pnfolder.pnlibrary.core.updates.catalog.ViewFixtures.event;
import static org.pnfolder.pnlibrary.core.updates.catalog.ViewFixtures.history;
import static org.pnfolder.pnlibrary.core.updates.catalog.ViewFixtures.hover;
import static org.pnfolder.pnlibrary.core.updates.catalog.ViewFixtures.plain;
import static org.pnfolder.pnlibrary.core.updates.catalog.ViewFixtures.row;
import static org.pnfolder.pnlibrary.core.updates.catalog.ViewFixtures.text;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.updates.history.UpdateHistoryEntry;
import org.pnfolder.pnlibrary.api.updates.product.ProductId;
import org.pnfolder.pnlibrary.api.updates.release.ProductChange;
import org.pnfolder.pnlibrary.api.updates.state.UpdateActor;
import org.pnfolder.pnlibrary.api.updates.state.UpdateState;
import org.pnfolder.pnlibrary.api.version.SemanticVersion;
import org.pnfolder.pnlibrary.updates.install.ledger.ComponentOperation;

class UpdateHistoryViewTest {
    @Test
    void historyGroupsEventsByDayUnderAccentHeadingsAndKeepsTimeAndActorInTheHover() {
        UpdateHistoryEntry today = event("2026-10-17T09:05:00Z", UpdateState.UPDATE_STAGED, "STAGE", "Admin", "pnlibrary", "1.0.0", "1.1.0");
        UpdateHistoryEntry yesterday = event("2026-10-16T18:00:00Z", UpdateState.UP_TO_DATE, "HEALTH", "System", "currency", "2.0.0", "2.1.0");
        UpdateHistoryEntry older = event("2026-09-12T10:00:00Z", UpdateState.FAILED, "STAGE", "Admin", "pnlibrary", "0.9.0", "1.0.0");
        List<Component> lines = history(Arrays.asList(today, yesterday, older));
        String text = text(lines);

        assertEquals("История", plain(lines.get(1)));
        assertTrue(text.indexOf("  «Сегодня»") < text.indexOf("  «Вчера»"));
        assertTrue(text.indexOf("  «Вчера»") < text.indexOf("  «12 сентября»"));
        assertTrue(text.contains("\n\n  «Вчера»\n"), text);
        assertTrue(text.contains("    pnlibrary  v1.0.0 → v1.1.0  подготовлено"), text);
        assertTrue(text.contains("    currency  v2.0.0 → v2.1.0  применено"));
        assertTrue(text.contains("    pnlibrary  v0.9.0 → v1.0.0  ошибка"));
        assertFalse(text.contains("Admin"));
        assertFalse(text.contains("страница 1 из 1"));
        String card = hover(row(lines, "v1.0.0 → v1.1.0"));
        assertTrue(card.contains("Итог: подготовлено, применится после перезапуска"));
        assertTrue(card.contains("Кто: Admin"));
    }

    @Test
    void historyLinesOfferAPerPluginFilterAndTheFilteredViewDoesNot() {
        UpdateHistoryEntry entry = event("2026-10-17T09:05:00Z", UpdateState.UPDATE_STAGED, "STAGE", "Admin", "pnlibrary", "1.0.0", "1.1.0");
        List<Component> all = history(Collections.singletonList(entry));
        assertTrue(clicks(all).contains("/pn updates history plugin pnlibrary"));
        assertTrue(hover(row(all, "pnlibrary  v1.0.0")).contains("Показать только pnlibrary\n/pn updates history plugin pnlibrary"));

        List<Component> filtered = history(Collections.singletonList(entry), 1, BUKKIT, 1, "pnlibrary");
        assertEquals("История  pnlibrary", plain(filtered.get(1)));
        assertNull(row(filtered, "v1.0.0 → v1.1.0").clickEvent());
        assertTrue(clicks(filtered).containsAll(Arrays.asList("/pn updates history", "/pn updates info pnlibrary")));
    }

    @Test
    void historyNavigationReportsPageNumbersAndLinksToOlderAndNewerPages() {
        UpdateHistoryEntry entry = event("2026-10-17T09:05:00Z", UpdateState.UPDATE_STAGED, "STAGE", "Admin", "pnlibrary", "1.0.0", "1.1.0");
        List<Component> middle = history(Collections.singletonList(entry), 2, BUKKIT, 4, null);
        assertTrue(text(middle).contains("  страница 2 из 4 [Новее] [Старее]"));
        assertTrue(clicks(middle).containsAll(Arrays.asList("/pn updates history 1", "/pn updates history 3", "/pn updates list")));

        assertFalse(clicks(history(Collections.singletonList(entry), 4, BUKKIT, 4, null)).contains("/pn updates history 5"));
        assertTrue(clicks(history(Collections.singletonList(entry), 1, BUKKIT, 2, "pnlibrary")).contains("/pn updates history plugin pnlibrary 2"));
        boolean empty = false;
        for (Component line : history(Collections.emptyList())) {
            empty |= plain(line).equals(" Событий пока нет.");
        }
        assertTrue(empty);
    }

    @Test
    void verifiedRollbackIsDistinguishedFromAMerelyPreparedRollback() {
        UpdateHistoryEntry verified = event("2026-10-17T09:05:00Z", UpdateState.ROLLED_BACK, "HEALTH_ROLLBACK", "System", "pnlibrary", "2.0.0", "1.0.0");
        UpdateHistoryEntry prepared = event("2026-10-17T08:05:00Z", UpdateState.ROLLED_BACK, "ROLLBACK", "Admin", "pnlibrary", "2.0.0", "1.0.0");
        String text = text(history(Arrays.asList(verified, prepared)));

        assertTrue(text.contains("pnlibrary  откат к v2.0.0  подтверждён"), text);
        assertTrue(text.contains("pnlibrary  откат к v2.0.0  ждёт перезапуска"));
    }

    @Test
    void anEventWithoutVersionChangesShowsItsMessageAsPlainText() {
        UpdateHistoryEntry entry = new UpdateHistoryEntry(UUID.randomUUID(), Instant.parse("2026-10-17T09:05:00Z"), UUID.randomUUID(),
            UpdateState.FAILED, UpdateActor.SYSTEM, "CHECK", Collections.emptyList(), "<red>HTTP 404</red>");

        assertEquals("    <red>HTTP 404</red>  ошибка", plain(row(history(Collections.singletonList(entry)), "HTTP 404")));
    }

    private static UpdateHistoryEntry component(String operation, String from, String to, String message) {
        return new UpdateHistoryEntry(UUID.randomUUID(), Instant.parse("2026-10-17T09:05:00Z"), UUID.randomUUID(), UpdateState.UP_TO_DATE,
            UpdateActor.SYSTEM, operation, Collections.singletonList(new ProductChange(ProductId.of("shop"),
            from == null ? null : SemanticVersion.parse(from), SemanticVersion.parse(to))), message);
    }

    @Test
    void componentEventsSayWhatWasInstalledRemovedChangedByHandOrReconfigured() {
        String text = text(history(Arrays.asList(
            component(ComponentOperation.ADDED, null, "1.0.0", null),
            component(ComponentOperation.REMOVED, "1.0.0", "1.0.0", null),
            component(ComponentOperation.VERSION_CHANGED, "1.0.0", "1.1.0", null),
            component(ComponentOperation.SETTINGS_CHANGED, "1.0.0", "1.0.0", "канал: stable → beta"))));

        assertTrue(text.contains("shop  v1.0.0  установлен"), text);
        assertTrue(text.contains("shop  v1.0.0  удалён"));
        assertTrue(text.contains("shop  v1.0.0 → v1.1.0  изменена вручную"));
        assertTrue(text.contains("shop  v1.0.0  канал: stable → beta"));
    }
}
