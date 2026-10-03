package ru.privatenull.pnlibrary.api.observability

interface ObservabilityService : AutoCloseable {
    fun record(request: ObservationRequest): Observation

    fun capture(block: ObservationScope.() -> Unit): Observation {
        val scope = ObservationScope().apply(block)
        return record(scope.build())
    }

    fun failure(error: Throwable, block: ObservationScope.() -> Unit = {}): Observation {
        val scope = ObservationScope().apply(block)
        return record(scope.build(error))
    }

    fun recent(query: ObservationQuery = ObservationQuery()): List<Observation>

    fun status(status: ComponentStatus): ComponentStatus

    fun createReport(request: ObservabilityReportRequest = ObservabilityReportRequest()): ObservabilityReport
}
