package org.pnfolder.pnlibrary.core.updates.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.pnfolder.pnlibrary.core.updates.catalog.ViewFixtures.BUKKIT;
import static org.pnfolder.pnlibrary.core.updates.catalog.ViewFixtures.NOW;
import static org.pnfolder.pnlibrary.core.updates.catalog.ViewFixtures.clicks;
import static org.pnfolder.pnlibrary.core.updates.catalog.ViewFixtures.plain;
import static org.pnfolder.pnlibrary.core.updates.catalog.ViewFixtures.row;
import static org.pnfolder.pnlibrary.core.updates.catalog.ViewFixtures.runs;
import static org.pnfolder.pnlibrary.core.updates.catalog.ViewFixtures.snapshot;
import static org.pnfolder.pnlibrary.core.updates.catalog.ViewFixtures.text;

import java.time.Instant;
import java.time.ZoneOffset;
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

class UpdateProductViewTest {
    private static String legacy(Component component) {
        return LegacyComponentSerializer.legacyAmpersand().serialize(component);
    }

    private static List<Component> settings(UpdateSnapshot snapshot, UpdateCatalogRoutes routes, Instant heldUntil) {
        return UpdateCatalogView.settings(snapshot, routes, heldUntil, NOW, ZoneOffset.UTC);
    }

    @Test
    void settingsDrawEveryChoiceAsAButtonAndMarkTheCurrentOne() {
        List<Component> lines = UpdateCatalogView.settings(
            snapshot("pnlibrary", UpdateState.UP_TO_DATE, null, UpdateChannel.BETA, "1.1.0"), BUKKIT, null);
        String text = text(lines);

        assertEquals("pnlibrary  настройки", plain(lines.get(1)));
        assertEquals("  v1.0.0 · beta", plain(lines.get(2)));
        assertTrue(text.contains(" Нажмите на кнопку, чтобы выбрать. ✔ — выбрано сейчас."));
        Component channel = row(lines, "Канал:");
        assertEquals("  Канал: [stable] [rc] [✔ beta] [alpha] [dev]", plain(channel));
        assertTrue(legacy(channel).contains("&e[✔ beta]"), legacy(channel));
        assertTrue(legacy(channel).contains("[&cdev&7]"));
        assertFalse(text.contains("Автозагрузка"));
        assertEquals("  Пауза: [✔ нет] [1 ч] [6 ч] [1 д] [3 д] [5 д]", plain(row(lines, "Пауза:")));
        assertTrue(text.contains("Пауза — не дольше 5 дней."));
        assertTrue(legacy(channel).startsWith("&f  Канал: "));

        List<String> actions = clicks(lines);
        assertTrue(actions.containsAll(Arrays.asList("/pn updates set pnlibrary channel stable", "/pn updates set pnlibrary channel alpha",
            "/pn updates set pnlibrary hold 3d", "/pn updates set pnlibrary hold 5d")));
        assertFalse(actions.contains("/pn updates set pnlibrary channel beta"));
        assertFalse(actions.contains("/pn updates set pnlibrary auto off"));
        assertFalse(actions.contains("/pn updates set pnlibrary hold off"));
    }

    @Test
    void aReaderWithoutEditRightsSeesTheChoicesButCannotClickThem() {
        List<Component> lines = settings(snapshot("pnlibrary", UpdateState.FROZEN), BUKKIT.forReader(true, false, false), NOW.plusSeconds(3600));
        String text = text(lines);

        for (String run : runs(lines)) {
            assertFalse(run.contains(" set "));
        }
        assertTrue(text.contains("Только просмотр"));
        assertFalse(text.contains("Нажмите на кнопку"));
        assertTrue(text.contains("Канал: [✔ stable] rc beta alpha dev"), text);
        assertTrue(text.contains("Пауза: до 17 окт. 13:00 · ещё 1 ч") || text.contains("Пауза: до 17 окт 13:00 · ещё 1 ч"), text);
        assertFalse(text.contains("[Снять]"));
    }

    @Test
    void aPausedComponentShowsWhenThePauseEndsAndOffersToLiftOrReplaceIt() {
        Instant until = NOW.plusSeconds(3L * 86_400);
        List<Component> lines = settings(snapshot("pnlibrary", UpdateState.FROZEN), BUKKIT, until);
        String text = text(lines);

        assertTrue(text.contains("Пауза: [✔ до 20 окт. 12:00 · ещё 3 д] [Снять]") || text.contains("Пауза: [✔ до 20 окт 12:00 · ещё 3 д] [Снять]"), text);
        assertTrue(text.contains("Продлить: [1 ч] [6 ч] [1 д] [3 д] [5 д]"));
        assertFalse(clicks(lines).contains("/pn updates set pnlibrary hold 30d"));
        assertTrue(clicks(lines).containsAll(Arrays.asList("/pn updates set pnlibrary hold off", "/pn updates set pnlibrary hold 5d")));

        String info = text(UpdateCatalogView.info(snapshot("pnlibrary", UpdateState.FROZEN), BUKKIT, until, NOW, ZoneOffset.UTC));
        assertTrue(info.contains("pnlibrary  приостановлено"));
        assertTrue(info.contains("· ещё 3 д"));
    }

    @Test
    void infoCardListsTheDetailsAndOffersAnUpdateOnlyWhenOneIsAvailable() {
        List<Component> available = UpdateCatalogView.info(
            snapshot("pnlibrary", UpdateState.UPDATE_AVAILABLE, "https://github.com/example/repo/blob/main/notes.md"), BUKKIT, null);
        String text = text(available);

        assertEquals("pnlibrary  доступно обновление", plain(available.get(1)));
        assertTrue(text.contains("  Версия: v1.0.0 → v1.1.0 [Изменения]"));
        assertTrue(text.contains("  Канал: stable [изменить]"));
        assertFalse(text.contains("Автозагрузка"));
        assertTrue(text.contains("  Требует: Java 8+"));
        assertTrue(text.contains("  [Обновить] [Настройки] [История] [Назад]"));
        assertTrue(clicks(available).containsAll(Arrays.asList("/pn update pnlibrary", "/pn updates history plugin pnlibrary", "/pn updates list")));

        List<Component> current = UpdateCatalogView.info(snapshot("pnlibrary", UpdateState.UP_TO_DATE, null, UpdateChannel.STABLE, "1.0.0"), BUKKIT, null);
        assertFalse(clicks(current).contains("/pn update pnlibrary"));
        assertTrue(text(current).contains("Версия: v1.0.0\n"));

        UpdateSnapshot oldJava = new UpdateSnapshot("pnlibrary", "1.0.0", null, UpdateChannel.STABLE, UpdateState.INCOMPATIBLE, 8, 17, false, null, null);
        assertTrue(text(UpdateCatalogView.info(oldJava, BUKKIT, null)).contains("Требует: Java 17+ (на сервере 8)"));
    }

    @Test
    void theInfoCardLinksEachSettingToTheSettingsCard() {
        List<Component> lines = UpdateCatalogView.info(snapshot("shop", UpdateState.UP_TO_DATE), BUKKIT, null);
        for (String label : Arrays.asList("Канал:", "Пауза:")) {
            Component row = row(lines, label);
            assertTrue(plain(row).endsWith("[изменить]"), plain(row));
            assertTrue(ViewFixtures.clicks(row).contains("/pn updates settings shop"));
        }
        for (Component line : UpdateCatalogView.info(snapshot("shop", UpdateState.UP_TO_DATE), BUKKIT.forReader(true, false, false), null)) {
            assertFalse(plain(line).contains("[изменить]"));
        }
    }

    @Test
    void infoCardShowsTheReleaseDateAndTheNewestReleaseOfEveryOtherChannelSwitchingChannelOnClick() {
        List<ReleaseSummary> releases = Arrays.asList(
            new ReleaseSummary("1.1.0", UpdateChannel.STABLE, Instant.parse("2026-10-01T00:00:00Z")),
            new ReleaseSummary("1.0.5", UpdateChannel.STABLE, Instant.parse("2026-09-01T00:00:00Z")),
            new ReleaseSummary("1.2.0-beta.2", UpdateChannel.BETA, Instant.parse("2026-10-10T00:00:00Z")));
        UpdateSnapshot snapshot = new UpdateSnapshot("pnlibrary", "1.0.0", "1.1.0", UpdateChannel.STABLE, UpdateState.UPDATE_AVAILABLE,
            17, 8, false, null, null, null, null, releases, Collections.emptyList(), null, null);

        List<Component> lines = UpdateCatalogView.info(snapshot, BUKKIT, null, NOW, ZoneOffset.UTC);
        String text = text(lines);

        assertTrue(text.contains("Выпущена: 1 октября · 16 д назад"), text);
        assertTrue(text.contains("Другие каналы: beta v1.2.0-beta.2"), text);
        List<String> actions = clicks(lines);
        assertTrue(actions.contains("/pn updates set pnlibrary channel beta"));
        assertFalse(actions.contains("/pn updates set pnlibrary channel stable"));
        for (String click : clicks(UpdateCatalogView.info(snapshot, BUKKIT.forReader(true, false, false), null))) {
            assertFalse(click.contains(" set "));
        }
    }
}
