package org.pnfolder.pnlibrary.core.runtime

import org.pnfolder.pnlibrary.api.runtime.PnLibrary
import org.pnfolder.pnlibrary.api.updates.PluginUpdateRequest
import org.pnfolder.pnlibrary.api.updates.ProductDescriptor
import org.pnfolder.pnlibrary.api.updates.UpdateRegistration
import org.pnfolder.pnlibrary.spi.platform.PlatformIdentity
import org.pnfolder.pnlibrary.spi.platform.PlatformOwnership

/** Registers the pnLibrary self-update monitor for one native platform distribution. */
internal object RuntimeUpdateMonitor {
    data class Registration(
        val currentVersion: String,
        val monitor: UpdateRegistration,
    )

    fun register(
        library: PnLibrary,
        owner: Any,
        identity: PlatformIdentity,
        ownership: PlatformOwnership,
    ): Registration {
        val currentVersion = ownership.ownerDetails(owner)["version"]
            ?.takeIf { it.isNotBlank() }
            ?: error("The platform did not expose the pnLibrary version")
        val artifactId = identity.artifactId
        val monitor = library.updates.register(
            owner,
            ProductDescriptor.library(currentVersion),
            PluginUpdateRequest.builder()
                .repository("pnFolder", "pnLibrary")
                .automaticDownload(false)
                .artifact("(?i)^pnLibrary-[0-9].*-$artifactId-java[0-9]+\\.jar$", 8)
                .build(),
        )
        return Registration(currentVersion, monitor)
    }
}
