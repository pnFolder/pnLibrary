package ru.privatenull.pnlibrary.spi.platform

import ru.privatenull.pnlibrary.spi.audiences.PlatformAudienceAdapter
import ru.privatenull.pnlibrary.spi.audiences.UnsupportedPlatformAudienceAdapter
import ru.privatenull.pnlibrary.spi.commands.PlatformCommandAdapter
import ru.privatenull.pnlibrary.spi.commands.UnsupportedPlatformCommandAdapter
import ru.privatenull.pnlibrary.spi.metrics.NoopMetricsFactory
import ru.privatenull.pnlibrary.spi.metrics.PlatformMetricsFactory
import ru.privatenull.pnlibrary.spi.tasks.PlatformTaskAdapter
import ru.privatenull.pnlibrary.spi.tasks.UnsupportedPlatformTaskAdapter

/** Optional native bridges consumed by shared runtime services. */
interface PlatformServices {
    val metricsFactory: PlatformMetricsFactory get() = NoopMetricsFactory
    val commandAdapter: PlatformCommandAdapter get() = UnsupportedPlatformCommandAdapter
    val audienceAdapter: PlatformAudienceAdapter get() = UnsupportedPlatformAudienceAdapter
    val taskAdapter: PlatformTaskAdapter get() = UnsupportedPlatformTaskAdapter
}
