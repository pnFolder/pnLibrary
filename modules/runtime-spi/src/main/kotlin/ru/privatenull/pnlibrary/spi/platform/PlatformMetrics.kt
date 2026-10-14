package ru.privatenull.pnlibrary.spi.platform

import ru.privatenull.pnlibrary.spi.metrics.NoopMetricsFactory
import ru.privatenull.pnlibrary.spi.metrics.PlatformMetricsFactory

interface PlatformMetrics {
    val metricsFactory: PlatformMetricsFactory get() = NoopMetricsFactory
}
