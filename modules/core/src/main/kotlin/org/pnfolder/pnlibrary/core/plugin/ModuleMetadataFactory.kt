package org.pnfolder.pnlibrary.core.plugin

import org.pnfolder.pnlibrary.api.plugin.ModuleId
import org.pnfolder.pnlibrary.api.plugin.PluginMetadata
import org.pnfolder.pnlibrary.common.jvm.JavaRuntime
import org.pnfolder.pnlibrary.spi.platform.PlatformIdentity
import org.pnfolder.pnlibrary.spi.platform.PlatformOwnership

/** Builds module metadata from the definition, falling back to the native plugin description. */
internal class ModuleMetadataFactory(
    private val ownership: PlatformOwnership,
    private val identity: PlatformIdentity,
) {
    fun create(owner: Any, moduleId: ModuleId, definition: ModuleDefinitionBuilder): PluginMetadata {
        val nativeMetadata = ownership.ownerDetails(owner)
        return PluginMetadata(
            id = moduleId,
            name = definition.metadataName.orNative(nativeMetadata, "name", moduleId.value),
            version = definition.metadataVersion.orNative(nativeMetadata, "version", "unknown"),
            authors = definition.metadataAuthors.orNative(nativeMetadata, "authors", "unknown"),
            platform = identity.type,
            platformImplementation = identity.implementationName,
            javaVersion = System.getProperty("java.version", "unknown"),
            javaFeature = JavaRuntime.feature,
        )
    }

    private fun String?.orNative(
        nativeMetadata: Map<String, String>,
        key: String,
        fallback: String,
    ): String = this ?: nativeMetadata[key]?.takeIf(String::isNotBlank) ?: fallback
}
