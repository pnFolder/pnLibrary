package org.pnfolder.pnlibrary.spi.platform

import org.pnfolder.pnlibrary.api.runtime.PnLibrary

/** Native registration boundary used once the shared runtime has been created. */
interface PlatformBinding {
    fun bind(library: PnLibrary) = Unit
}
