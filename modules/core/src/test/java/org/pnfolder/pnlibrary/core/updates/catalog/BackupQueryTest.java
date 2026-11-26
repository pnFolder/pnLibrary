package org.pnfolder.pnlibrary.core.updates.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.updates.backup.UpdateBackup;
import org.pnfolder.pnlibrary.api.updates.backup.UpdateBackupComponent;
import org.pnfolder.pnlibrary.api.updates.product.ProductId;

class BackupQueryTest {
    private final Instant now = Instant.parse("2026-10-31T12:00:00Z");

    private static UpdateBackup backup(String at, String... products) {
        List<UpdateBackupComponent> components = new ArrayList<>();
        for (String product : products) {
            components.add(new UpdateBackupComponent(ProductId.of(product), "2.0.0", "1.0.0"));
        }
        return new UpdateBackup(UUID.randomUUID(), at == null ? null : Instant.parse(at), null, components);
    }

    private final UpdateBackup old = backup("2026-10-01T10:00:00Z", "shop");
    private final UpdateBackup middle = backup("2026-10-20T10:00:00Z", "pnlibrary", "clans");
    private final UpdateBackup recent = backup("2026-10-30T10:00:00Z", "clans");
    private final UpdateBackup undated = backup(null, "shop");
    private final List<UpdateBackup> all = Arrays.asList(old, middle, recent, undated);

    @Test
    void theDefaultListIsNewestFirstAndKeepsEverySet() {
        assertEquals(Arrays.asList(recent, middle, old, undated), new BackupQuery().apply(all));
        assertTrue(new BackupQuery().isDefault());
    }

    @Test
    void oldestFirstPutsTheSetWithoutADateLast() {
        assertEquals(Arrays.asList(old, middle, recent, undated),
            new BackupQuery(null, null, null, BackupOrder.OLDEST).apply(all));
    }

    @Test
    void aPluginKeepsOnlyTheSetsThatContainIt() {
        assertEquals(Arrays.asList(recent, middle), new BackupQuery("CLANS", null, null, BackupOrder.NEWEST).apply(all));
    }

    @Test
    void aStartDateDropsOlderSetsAndThoseWithNoDate() {
        Instant since = BackupQuery.parseSince("7d", now, ZoneOffset.UTC);

        assertEquals(Collections.singletonList(recent), new BackupQuery(null, since, "7d", BackupOrder.NEWEST).apply(all));
    }

    @Test
    void sortingByPluginGroupsTheSetsByTheFirstPluginName() {
        assertEquals(Arrays.asList(recent, middle, old, undated), new BackupQuery(null, null, null, BackupOrder.PLUGIN).apply(all));
    }

    @Test
    void aDateIsReadAsDaysBackOrAsACalendarDay() {
        assertEquals(Instant.parse("2026-10-24T12:00:00Z"), BackupQuery.parseSince("7d", now, ZoneOffset.UTC));
        assertEquals(Instant.parse("2026-10-15T00:00:00Z"), BackupQuery.parseSince("2026-10-15", now, ZoneOffset.UTC));
        assertNull(BackupQuery.parseSince("yesterday", now, ZoneOffset.UTC));
        assertNull(BackupQuery.parseSince("2026-13-45", now, ZoneOffset.UTC));
    }

    @Test
    void theCommandKeepsTheFiltersSoAPageButtonDoesNotLoseThem() {
        BackupQuery query = new BackupQuery("clans", now, "7d", BackupOrder.OLDEST);

        assertEquals("/pn updates backups plugin clans since 7d sort old", query.command("/pn updates backups"));
        assertEquals("/pn updates backups", new BackupQuery().command("/pn updates backups"));
    }
}
