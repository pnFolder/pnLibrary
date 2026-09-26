package ru.privatenull.pnlibrary.acceptance;

import ru.privatenull.pnlibrary.api.events.Event;

public final class AcceptanceEvent extends Event {
    private final long sequence;

    public AcceptanceEvent(long sequence) {
        this.sequence = sequence;
    }

    public long getSequence() {
        return sequence;
    }
}
