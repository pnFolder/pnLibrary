package ru.privatenull.pnlibrary.spi.platform

import ru.privatenull.pnlibrary.api.runtime.PnLibrary

/** Native registration boundary used once the shared runtime has been created. */
interface PlatformBinding {
    fun bind(library: PnLibrary) = Unit
}
