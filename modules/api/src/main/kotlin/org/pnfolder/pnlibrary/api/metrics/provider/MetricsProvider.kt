package org.pnfolder.pnlibrary.api.metrics.provider


/** Identifies a metrics transport without exposing its SDK classes to consumers. */
enum class MetricsProvider {
    BSTATS,
    FASTSTATS,
}
