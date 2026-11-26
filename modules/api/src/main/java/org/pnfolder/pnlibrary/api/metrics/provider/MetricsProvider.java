package org.pnfolder.pnlibrary.api.metrics.provider;

/** Identifies a metrics transport without exposing its SDK classes to consumers. */
public enum MetricsProvider {
    /** The bStats service. */
    BSTATS,
    /** The FastStats service. */
    FASTSTATS
}
