package org.pnfolder.pnlibrary.updates.engine.transaction;

import java.util.UUID;

/** A preparation must not be health-checked as if a restart already happened in this process. */
public final class UpdateProcessSession {
    private static final String ID = UUID.randomUUID().toString();

    private UpdateProcessSession() {
    }

    /** Identifies this JVM run. */
    public static String getId() {
        return ID;
    }
}
