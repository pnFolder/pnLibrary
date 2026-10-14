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
interface PlatformServices : PlatformMetrics, PlatformCommands, PlatformAudience, PlatformTasks
