package ru.privatenull.pnlibrary.api.observability

/**
 * Records operational history, tracks current component state, and creates
 * support reports from one consistent data source.
 *
 * Calls are safe from platform threads. Closing the service releases its
 * persistence resources and rejects subsequent write operations.
 */
interface ObservabilityService : AutoCloseable {
    /** Records one immutable request and persists any declared attachments. */
    fun record(request: ObservationRequest): Observation

    /** Builds and records one observation through the Kotlin DSL. */
    fun capture(block: ObservationScope.() -> Unit): Observation {
        val scope = ObservationScope().apply(block)
        return record(scope.build())
    }

    /**
     * Builds an error observation and captures [error] immediately.
     *
     * The resulting level is always [ObservationLevel.ERROR].
     */
    fun failure(error: Throwable, block: ObservationScope.() -> Unit = {}): Observation {
        val scope = ObservationScope().apply(block)
        return record(scope.build(error))
    }

    /** Returns observations matching [query], ordered from newest to oldest. */
    fun recent(query: ObservationQuery = ObservationQuery()): List<Observation>

    /** Stores and returns the latest state for one plugin component. */
    fun status(status: ComponentStatus): ComponentStatus

    /** Creates a local support report from one consistent runtime snapshot. */
    fun createReport(request: ObservabilityReportRequest = ObservabilityReportRequest()): ObservabilityReport
}
