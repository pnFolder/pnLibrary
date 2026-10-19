package org.pnfolder.pnlibrary.acceptance;

import org.pnfolder.pnlibrary.api.events.Event;

public final class AcceptanceEvent extends Event {
    private final long sequence;

    public AcceptanceEvent(long sequence) {
        this.sequence = sequence;
    }

    public long getSequence() {
        return sequence;
    }
}
