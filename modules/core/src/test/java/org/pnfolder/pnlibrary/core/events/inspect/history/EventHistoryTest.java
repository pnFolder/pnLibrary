package org.pnfolder.pnlibrary.core.events.inspect.history;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pnfolder.pnlibrary.api.events.inspect.history.EventDay;
import org.pnfolder.pnlibrary.api.events.inspect.history.EventHour;

class EventHistoryTest {
    @Test
    void countsCallsPerDayAndSurvivesARestart(@TempDir Path dir) {
        Path file = dir.resolve("history.tsv");
        EventHistory history = new EventHistory(file, 30);
        history.record("a.Buy", false);
        history.record("a.Buy", true);
        history.record("a.Join", false);
        history.close();

        EventHistory reloaded = new EventHistory(file, 30);
        List<EventDay> buys = reloaded.entries("a.Buy");
        assertEquals(1, buys.size());
        assertEquals(2L, buys.get(0).getCalls());
        assertEquals(1L, buys.get(0).getDenied());
        assertEquals(LocalDate.now(), buys.get(0).getDate());
        assertEquals(2, reloaded.entries().size());
    }

    @Test
    void dropsDaysOlderThanTheRetentionAndKeepsNothingAtZero(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("history.tsv");
        LocalDate old = LocalDate.now().minusDays(40);
        LocalDate recent = LocalDate.now().minusDays(2);
        Files.write(file, (old + "\ta.Buy\t5\t0\n" + recent + "\ta.Buy\t3\t1\nbroken line\n").getBytes(StandardCharsets.UTF_8));

        List<LocalDate> dates = new ArrayList<>();
        for (EventDay day : new EventHistory(file, 30).entries()) {
            dates.add(day.getDate());
        }
        assertEquals(Arrays.asList(recent), dates);
        EventHistory off = new EventHistory(file, 0);
        off.record("a.Buy", false);
        assertTrue(off.entries().isEmpty());
    }

    @Test
    void countsCallsPerHourAndSurvivesARestart(@TempDir Path dir) {
        Path file = dir.resolve("history.tsv");
        EventHistory history = new EventHistory(file, 30);
        history.record("a.Buy", false);
        history.record("a.Buy", true);
        history.close();

        List<EventHour> hours = new EventHistory(file, 30).hourly("a.Buy");
        assertEquals(1, hours.size());
        assertEquals(2L, hours.get(0).getCalls());
        assertEquals(1L, hours.get(0).getDenied());
        assertEquals(LocalDateTime.now().truncatedTo(ChronoUnit.HOURS), hours.get(0).getHour());
    }

    @Test
    void dropsHoursOlderThan30Days(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("history.tsv");
        LocalDateTime old = LocalDateTime.now().minusDays(40).truncatedTo(ChronoUnit.HOURS);
        Files.write(file, (old + "\ta.Buy\t5\t0\n").getBytes(StandardCharsets.UTF_8));
        assertTrue(new EventHistory(file, 60).hourly().isEmpty());
    }

    @Test
    void exportsDaysAndHoursAsCsvAndJsonBesideTheHistoryFile(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("history.tsv");
        EventHistory history = new EventHistory(file, 30);
        try {
            history.record("a.Buy", true);
            String csv = new String(Files.readAllBytes(history.exportCsv()), StandardCharsets.UTF_8);
            assertTrue(csv.startsWith("period,time,event,calls,denied"), csv);
            assertTrue(csv.contains("day,") && csv.contains("hour,") && csv.contains(",a.Buy,1,1"), csv);
            String json = new String(Files.readAllBytes(history.exportJson()), StandardCharsets.UTF_8);
            assertTrue(json.contains("\"event\":\"a.Buy\"") && json.contains("\"hours\":["), json);
        } finally {
            history.close();
        }
        assertNull(new EventHistory(null, 30).exportCsv());
    }

    @Test
    void aHistoryThatCannotBeWrittenIsReportedOnce(@TempDir Path dir) throws IOException {
        Path blocker = dir.resolve("blocker");
        Files.write(blocker, "a file where the folder should be".getBytes(StandardCharsets.UTF_8));
        List<Throwable> failures = new ArrayList<>();
        EventHistory history = new EventHistory(blocker.resolve("history.tsv"), 30,
                java.time.ZoneId::systemDefault, failures::add);

        history.record("a.Buy", false);
        history.flush();
        history.record("a.Buy", false);
        history.flush();

        assertEquals(1, failures.size());
    }
}
