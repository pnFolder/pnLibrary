package org.pnfolder.pnlibrary.core.updates.restart;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.core.link.LinkEntry;
import org.pnfolder.pnlibrary.core.link.ReceivedNetwork;
import org.pnfolder.pnlibrary.core.link.report.LinkReport;
import org.pnfolder.pnlibrary.core.link.report.NetworkReport;
import org.pnfolder.pnlibrary.core.link.report.NetworkServer;

class ImportantNoticesTest {
    private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");

    private static LinkReport report(boolean restart, String address) {
        return new LinkReport("Velocity", "0.1.0", 0, Collections.emptyList(), NOW, restart, address);
    }

    private static ReceivedNetwork network(Instant at, LinkReport proxy, NetworkServer... servers) {
        return new ReceivedNetwork(new NetworkReport(proxy, Arrays.asList(servers), at), at);
    }

    @Test
    void aProxyThatNeedsARestartIsAnnouncedWithItsName() {
        List<ImportantNotice> notices = ImportantNotices.from(network(NOW, report(true, null)), NOW);

        assertEquals(1, notices.size());
        assertTrue(notices.get(0).line().startsWith("Прокси: "), notices.get(0).line());
        assertTrue(notices.get(0).line().contains("перезапуск прокси"));
    }

    @Test
    void aServerNoticeCarriesItsAddressWhenTheProxyKnowsIt() {
        NetworkServer lobby = new NetworkServer("lobby", true, report(true, "10.0.0.5:25565"));

        List<ImportantNotice> notices = ImportantNotices.from(network(NOW, report(false, null), lobby), NOW);

        assertEquals(1, notices.size());
        assertEquals("lobby", notices.get(0).getSource());
        assertEquals("10.0.0.5:25565", notices.get(0).getAddress());
        assertTrue(notices.get(0).line().startsWith("lobby (10.0.0.5:25565): "));
    }

    @Test
    void noticesGoAwayOnceTheUpdateIsApplied() {
        assertTrue(ImportantNotices.from(network(NOW, report(false, null),
            new NetworkServer("lobby", true, report(false, null))), NOW).isEmpty());
    }

    @Test
    void offlineServersAndTooOldMapsAreNotTrusted() {
        NetworkServer gone = new NetworkServer("games", false, report(true, null));

        assertTrue(ImportantNotices.from(network(NOW, report(false, null), gone), NOW).isEmpty());
        assertTrue(ImportantNotices.from(network(NOW, report(true, null)), NOW.plusSeconds(600)).isEmpty());
        assertTrue(ImportantNotices.from(null, NOW).isEmpty());
    }

    @Test
    void aProxyListsItsOwnStagedUpdateAndTheServersThatReportedOne() {
        LinkEntry waiting = new LinkEntry("lobby", report(true, null), NOW);
        LinkEntry fine = new LinkEntry("games", report(false, null), NOW);
        LinkEntry silent = new LinkEntry("old", report(true, null), NOW.minusSeconds(3600));

        List<ImportantNotice> notices = ImportantNotices.fromReports(true, Arrays.asList(waiting, fine, silent), NOW);

        assertEquals(Arrays.asList("Прокси", "lobby"), Arrays.asList(notices.get(0).getSource(), notices.get(1).getSource()));
        assertEquals(2, notices.size());
        assertTrue(ImportantNotices.fromReports(false, Collections.emptyList(), NOW).isEmpty());
    }
}
