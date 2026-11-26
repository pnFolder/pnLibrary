package org.pnfolder.pnlibrary.core.events.inspect.history;

import org.pnfolder.pnlibrary.api.events.inspect.history.EventDay;
import org.pnfolder.pnlibrary.api.events.inspect.history.EventHour;

import java.util.List;

/** Renders the recorded days and hours as the text of the CSV and JSON exports. */
final class EventHistoryExport {
    private static final String NL = "\n";

    private EventHistoryExport() {
    }

    static String csv(List<EventDay> days, List<EventHour> hours) {
        StringBuilder text = new StringBuilder("period,time,event,calls,denied").append(NL);
        for (EventDay day : days) {
            text.append("day,").append(day.getDate()).append(',').append(cell(day.getEventType())).append(',')
                    .append(day.getCalls()).append(',').append(day.getDenied()).append(NL);
        }
        for (EventHour hour : hours) {
            text.append("hour,").append(hour.getHour()).append(',').append(cell(hour.getEventType())).append(',')
                    .append(hour.getCalls()).append(',').append(hour.getDenied()).append(NL);
        }
        return text.toString();
    }

    static String json(List<EventDay> days, List<EventHour> hours) {
        StringBuilder text = new StringBuilder("{\"days\":[");
        for (int index = 0; index < days.size(); index++) {
            EventDay day = days.get(index);
            text.append(index == 0 ? "" : ",");
            entry(text, "date", day.getDate().toString(), day.getEventType(), day.getCalls(), day.getDenied());
        }
        text.append("],\"hours\":[");
        for (int index = 0; index < hours.size(); index++) {
            EventHour hour = hours.get(index);
            text.append(index == 0 ? "" : ",");
            entry(text, "hour", hour.getHour().toString(), hour.getEventType(), hour.getCalls(), hour.getDenied());
        }
        return text.append("]}").append(NL).toString();
    }

    private static void entry(StringBuilder text, String periodKey, String period, String event, long calls, long denied) {
        text.append("{\"").append(periodKey).append("\":\"").append(period).append("\",\"event\":").append(quote(event))
                .append(",\"calls\":").append(calls).append(",\"denied\":").append(denied).append('}');
    }

    private static String cell(String value) {
        boolean needsQuotes = value.indexOf(',') >= 0 || value.indexOf('"') >= 0;
        return needsQuotes ? "\"" + value.replace("\"", "\"\"") + "\"" : value;
    }

    private static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
