package ru.privatenull.pnlibrary.spi.platform

import ru.privatenull.pnlibrary.spi.audiences.PlatformAudienceAdapter
import ru.privatenull.pnlibrary.spi.audiences.UnsupportedPlatformAudienceAdapter

interface PlatformAudience {
    val audienceAdapter: PlatformAudienceAdapter get() = UnsupportedPlatformAudienceAdapter
}
