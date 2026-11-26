package org.pnfolder.pnlibrary.core.placeholders.format;

import java.util.Locale;
import java.time.Duration;
import org.pnfolder.pnlibrary.api.placeholders.info.ViewerInfo;

/** Writes spans of time the way the viewer reads them. */
final class TimeText {
    private TimeText() {
    }

    /** The two biggest non-zero units, such as {@code 1h 30m}. */
    static String left(Duration duration, ViewerInfo viewer) {
        long[] values = {duration.toDays(), duration.toHours() % 24, duration.toMinutes() % 60, duration.getSeconds() % 60};
        String[] units = NumberText.isRussian(viewer)
                ? new String[] {"д", "ч", "м", "с"}
                : new String[] {"d", "h", "m", "s"};
        StringBuilder text = new StringBuilder();
        int taken = 0;
        for (int i = 0; i < values.length && taken < 2; i++) {
            if (values[i] > 0) {
                text.append(taken > 0 ? " " : "").append(values[i]).append(units[i]);
                taken++;
            }
        }
        return taken == 0 ? "0" + units[units.length - 1] : text.toString();
    }

    static String hms(Duration duration) {
        return String.format(Locale.ROOT, "%02d:%02d:%02d", duration.toHours(), duration.toMinutes() % 60, duration.getSeconds() % 60);
    }
}
