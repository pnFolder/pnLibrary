package org.pnfolder.pnlibrary.core.link.wire;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.core.link.report.LinkPlugin;
import org.pnfolder.pnlibrary.core.link.report.LinkReport;

class ReportWireTest {
    private static LinkReport roundTrip(LinkReport report, int limit) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ReportWire.write(new DataOutputStream(bytes), report, limit);
        return ReportWire.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
    }

    @Test
    void aReportSurvivesTheWireAndAMissingLatestVersionStaysNull() throws IOException {
        LinkReport report = new LinkReport("paper", "1.2", 7,
            Collections.singletonList(new LinkPlugin("shop", "1.0", null, "ok")), Instant.ofEpochMilli(1234));

        LinkReport back = roundTrip(report, 10);

        assertEquals(report, back);
        assertNull(back.getPlugins().get(0).getLatest());
    }

    @Test
    void onlyTheFirstPluginsUpToTheLimitAreSent() throws IOException {
        List<LinkPlugin> plugins = new ArrayList<LinkPlugin>();
        for (int i = 1; i <= 5; i++) {
            plugins.add(new LinkPlugin("p" + i, "1", "2", "ok"));
        }

        assertEquals(2, roundTrip(new LinkReport("paper", "1", 0, plugins, Instant.EPOCH), 2).getPlugins().size());
    }
}
