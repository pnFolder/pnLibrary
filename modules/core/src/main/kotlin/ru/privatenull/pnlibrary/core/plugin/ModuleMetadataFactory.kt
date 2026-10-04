package ru.privatenull.pnlibrary.core.plugin

import ru.privatenull.pnlibrary.api.plugin.ModuleId
import ru.privatenull.pnlibrary.api.plugin.PluginMetadata
import ru.privatenull.pnlibrary.spi.platform.PlatformAdapter

internal class ModuleMetadataFactory(private val platform: PlatformAdapter) {
    fun create(owner: Any, moduleId: ModuleId, definition: ModuleDefinitionBuilder): PluginMetadata {
        val nativeMetadata = platform.ownerDetails(owner)
        return PluginMetadata(
            id = moduleId,
            name = definition.metadataName.orNative(nativeMetadata, "name", moduleId.value),
            version = definition.metadataVersion.orNative(nativeMetadata, "version", "unknown"),
            authors = definition.metadataAuthors.orNative(nativeMetadata, "authors", "unknown"),
            platform = platform.type,
            platformImplementation = platform.implementationName,
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
