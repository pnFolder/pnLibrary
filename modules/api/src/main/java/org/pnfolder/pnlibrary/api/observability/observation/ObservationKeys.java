package org.pnfolder.pnlibrary.api.observability.observation;

/** Reserved {@link Observation#getData()} keys used by the library for retention. */
public final class ObservationKeys {
    /** Request: how long to keep the observation, in milliseconds; replaced by {@link #KEEP_UNTIL_MS} when stored. */
    public static final String KEEP_FOR_MS = "pn.keepForMs";
    /** Stored: epoch milliseconds after which the observation is dropped. */
    public static final String KEEP_UNTIL_MS = "pn.keepUntilMs";

    private ObservationKeys() {
    }
}
