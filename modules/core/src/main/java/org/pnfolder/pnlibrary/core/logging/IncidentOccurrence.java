package org.pnfolder.pnlibrary.core.logging;

/** One time an incident happened: the exact UTC time and the thread that logged it. */
final class IncidentOccurrence {
    final String timeUtc;
    final String thread;

    IncidentOccurrence(String timeUtc, String thread) {
        this.timeUtc = timeUtc;
        this.thread = thread;
    }
}
