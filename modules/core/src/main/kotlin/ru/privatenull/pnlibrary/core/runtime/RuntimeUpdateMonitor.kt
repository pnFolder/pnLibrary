package ru.privatenull.pnlibrary.core.runtime

import ru.privatenull.pnlibrary.api.runtime.PnLibrary
import ru.privatenull.pnlibrary.api.updates.PluginUpdateRequest
import ru.privatenull.pnlibrary.api.updates.UpdateRegistration
import ru.privatenull.pnlibrary.spi.platform.PlatformIdentity
import ru.privatenull.pnlibrary.spi.platform.PlatformOwnership

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
            ru.privatenull.pnlibrary.api.updates.ProductDescriptor.library(currentVersion),
            PluginUpdateRequest.builder()
                .repository("pnFolder", "pnLibrary")
                .automaticDownload(false)
                .artifact("(?i)^pnLibrary-[0-9].*-$artifactId-java[0-9]+\\.jar$", 8)
                .build(),
        )
        return Registration(currentVersion, monitor)
    }
}
