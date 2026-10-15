package ru.privatenull.pnlibrary.core.plugin

import ru.privatenull.pnlibrary.api.plugin.ModuleId
import ru.privatenull.pnlibrary.api.plugin.PluginMetadata
import ru.privatenull.pnlibrary.spi.platform.PlatformIdentity
import ru.privatenull.pnlibrary.spi.platform.PlatformOwnership

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
            javaFeature = Runtime.version().feature(),
        )
    }

    private fun String?.orNative(
        nativeMetadata: Map<String, String>,
        key: String,
        fallback: String,
    ): String = this ?: nativeMetadata[key]?.takeIf(String::isNotBlank) ?: fallback
}
