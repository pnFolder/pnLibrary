package ru.privatenull.pnlibrary.api.diagnostics

import ru.privatenull.pnlibrary.api.activity.ActivityService

/**
 * Single observability entry point: diagnostic registrations, runtime events,
 * and the support-file journal are one lifecycle and one report source.
 */
interface ObservabilityService :
    DiagnosticsService,
    ActivityService,
    ru.privatenull.pnlibrary.api.observability.ObservabilityService
