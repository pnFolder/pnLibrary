package org.pnfolder.pnlibrary.core.plugin.module

import org.pnfolder.pnlibrary.api.plugin.ModuleId
import org.pnfolder.pnlibrary.api.updates.ProductDescriptor
import org.pnfolder.pnlibrary.api.version.PnLibraryApi
import org.pnfolder.pnlibrary.spi.platform.PlatformOwnership

/** Resolves and binds the update identity represented by one registered module. */
internal class ModuleProductResolver(
    private val platform: PlatformOwnership,
) {
    fun resolve(
        owner: Any,
        moduleId: ModuleId,
        definition: ModuleDefinitionBuilder,
    ): ProductDescriptor? {
        val explicit = definition.productDescriptor?.bindIfNecessary(moduleId)
        val inferredFromUpdates = definition.updateRequest?.let { request ->
            ProductDescriptor.builder()
                .version(resolveVersion(owner, moduleId, definition))
                .pnLibraryApi(request.supportedApi.minimum, request.supportedApi.maximum)
                .build()
        }
        val inferredFromDependencies = if (
            explicit == null &&
            inferredFromUpdates == null &&
            definition.dependencies.isNotEmpty()
        ) {
            ProductDescriptor.builder()
                .version(resolveVersion(owner, moduleId, definition))
                .pnLibraryApi(PnLibraryApi.VERSION, PnLibraryApi.VERSION)
                .build()
        } else {
            null
        }

        return (explicit ?: inferredFromUpdates ?: inferredFromDependencies)
            ?.bindIfNecessary(moduleId)
            ?.also { descriptor ->
                require(descriptor.id.value == moduleId.value) {
                    "Component ID ${descriptor.id} does not match module ID $moduleId"
                }
            }
    }

    private fun resolveVersion(
        owner: Any,
        moduleId: ModuleId,
        definition: ModuleDefinitionBuilder,
    ): String = platform.ownerDetails(owner)["version"]
        ?: definition.metadataVersion
        ?: error("Cannot infer component version for $moduleId")

    private fun ProductDescriptor.bindIfNecessary(moduleId: ModuleId): ProductDescriptor =
        if (isBound) this else bindTo(moduleId.value)
}
