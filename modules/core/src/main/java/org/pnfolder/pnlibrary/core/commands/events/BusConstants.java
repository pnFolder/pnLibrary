package org.pnfolder.pnlibrary.core.commands.events;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Page sizes and the period words shared by the pages of {@code /pn bus}. */
public final class BusConstants {
    public static final String ROOT = "/pn bus";
    public static final int PAGE_SIZE = 8;
    public static final int TOP_SIZE = 10;
    public static final int DAYS_PER_PAGE = 7;
    public static final int EVENTS_PER_PAGE = 6;

    /** The words of {@code /pn bus range} and how many hours each one covers. */
    public static final Map<String, Integer> RANGES;

    static {
        Map<String, Integer> ranges = new LinkedHashMap<>();
        ranges.put("1h", 1);
        ranges.put("24h", 24);
        ranges.put("7d", 168);
        ranges.put("30d", 720);
        RANGES = Collections.unmodifiableMap(ranges);
    }

    private BusConstants() {
    }
}
