package org.pnfolder.pnlibrary.core.events.inspect.history;

import java.util.concurrent.atomic.LongAdder;

/** The calls and denials of one event type in one hour or day; adders keep counting lock-free. */
final class EventCounts {
    private final LongAdder calls = new LongAdder();
    private final LongAdder denied = new LongAdder();

    void add(long callAmount, long deniedAmount) {
        calls.add(callAmount);
        denied.add(deniedAmount);
    }

    long getCalls() {
        return calls.sum();
    }

    long getDenied() {
        return denied.sum();
    }
}
