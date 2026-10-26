package org.pnfolder.pnlibrary.spi.platform

import org.pnfolder.pnlibrary.api.runtime.PnLibrary

/** Native registration boundary used once the shared runtime has been created. */
interface PlatformBinding {
    /** Called once with the created [library] so the platform can register itself. */
    fun bind(library: PnLibrary) = Unit
}
