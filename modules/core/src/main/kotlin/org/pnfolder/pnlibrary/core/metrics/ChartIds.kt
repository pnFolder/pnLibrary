package org.pnfolder.pnlibrary.core.metrics

private val CHART_ID = Regex("[A-Za-z0-9_-]{1,64}")

/** Trims this chart id and rejects values that a metrics provider could not represent; every supported provider accepts the rest. */
internal fun String.validChartId(): String = trim().also {
    require(CHART_ID.matches(it)) { "Invalid metrics chart id: $this" }
}
