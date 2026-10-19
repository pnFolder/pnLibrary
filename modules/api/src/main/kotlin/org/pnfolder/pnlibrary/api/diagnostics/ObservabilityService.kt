package org.pnfolder.pnlibrary.api.diagnostics

import org.pnfolder.pnlibrary.api.activity.ActivityService

/**
 * Single observability entry point: diagnostic registrations, runtime events,
 * and the support-file journal are one lifecycle and one report source.
 */
interface ObservabilityService :
    DiagnosticsService,
    ActivityService,
    org.pnfolder.pnlibrary.api.observability.ObservabilityService
