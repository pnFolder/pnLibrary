package org.pnfolder.pnlibrary.core.metrics;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.metrics.provider.MetricsCredentials;
import org.pnfolder.pnlibrary.core.metrics.session.AbstractMetricsSession;
import org.pnfolder.pnlibrary.core.metrics.session.Chart;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AbstractMetricsSessionTest {

    @Test
    void chartsAreNormalizedAndSuppliersRemainLazy() {
        RecordingSession session = new RecordingSession();
        AtomicInteger reads = new AtomicInteger();
        assertSame(session, session.simplePie(" mode ", () -> {
            reads.incrementAndGet();
            return "paper";
        }));
        assertEquals(0, reads.get());
        assertEquals(1, session.charts.size());
        Chart.SimplePie chart = (Chart.SimplePie) session.charts.get(0);
        assertEquals("mode", chart.getId());
        assertEquals("paper", chart.getValue().get());
        assertEquals(1, reads.get());
    }

    @Test
    void allChartKindsRetainTheirDistinctTypes() {
        RecordingSession session = new RecordingSession();
        session.simplePie("pie", () -> "a");
        session.advancedPie("advanced", () -> Collections.singletonMap("a", 1));
        session.drilldownPie("drill", () -> Collections.singletonMap("a", Collections.singletonMap("b", 2)));
        session.singleLineChart("line", () -> 3);
        session.multiLineChart("lines", () -> Collections.singletonMap("c", 4));
        session.simpleBarChart("bar", () -> Collections.singletonMap("d", 5));
        session.advancedBarChart("bars", () -> Collections.singletonMap("e", new int[]{6}));
        List<String> names = new ArrayList<>();
        for (Chart chart : session.charts) {
            names.add(chart.getClass().getSimpleName());
        }
        assertEquals(Arrays.asList("SimplePie", "AdvancedPie", "DrilldownPie", "SingleLine", "MultiLine", "SimpleBar",
                "AdvancedBar"), names);
    }

    @Test
    void invalidIdentifiersNeverReachAProvider() {
        RecordingSession session = new RecordingSession();
        StringBuilder tooLong = new StringBuilder();
        for (int index = 0; index < 65; index++) {
            tooLong.append('x');
        }
        for (String id : Arrays.asList("", "bad id", tooLong.toString())) {
            assertThrows(IllegalArgumentException.class, () -> session.singleLineChart(id, () -> 1));
        }
        assertEquals(0, session.charts.size());
    }

    @Test
    void closeShutsDownOnceAndPreventsRegistration() {
        RecordingSession session = new RecordingSession();
        session.close();
        session.close();
        assertEquals(1, session.shutdowns);
        assertThrows(IllegalStateException.class, () -> session.singleLineChart("line", () -> 1));
    }

    @Test
    void shutdownFailureStillClosesOwnedCredentials() {
        MetricsCredentials.FastStats credentials = new MetricsCredentials.FastStats("private".toCharArray());
        AbstractMetricsSession session = new AbstractMetricsSession(credentials) {
            @Override
            protected void register(@NotNull Chart chart) {
            }

            @Override
            protected void shutdown() {
                throw new IllegalStateException("native shutdown failed");
            }
        };
        assertThrows(IllegalStateException.class, session::close);
        assertThrows(IllegalStateException.class, () -> credentials.useToken(token -> token));
    }

    private static final class RecordingSession extends AbstractMetricsSession {
        final List<Chart> charts = new ArrayList<>();
        int shutdowns;

        RecordingSession() {
            super(new MetricsCredentials.BStats(42));
        }

        @Override
        protected void register(@NotNull Chart chart) {
            charts.add(chart);
        }

        @Override
        protected void shutdown() {
            shutdowns++;
        }
    }
}
