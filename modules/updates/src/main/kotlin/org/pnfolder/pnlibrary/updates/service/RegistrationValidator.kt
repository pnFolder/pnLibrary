package org.pnfolder.pnlibrary.updates.service

import org.pnfolder.pnlibrary.api.updates.PluginUpdateArtifact
import org.pnfolder.pnlibrary.api.updates.PluginUpdateRequest
import org.pnfolder.pnlibrary.api.updates.ProductDescriptor
import org.pnfolder.pnlibrary.api.version.SemanticVersion
import org.pnfolder.pnlibrary.common.jvm.JavaRuntime
import org.pnfolder.pnlibrary.spi.platform.PlatformIdentity
import org.pnfolder.pnlibrary.spi.platform.PlatformOwnerMetadata
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/** What a plugin must provide on disk to be updated: its [jar], the [pluginsDirectory] it sits in and its [artifact]. */
internal data class ValidatedRegistration(val jar: Path, val pluginsDirectory: Path, val artifact: PluginUpdateArtifact)

/** Checks that a registering plugin can be updated and finds the files the update pipeline works with. */
internal class RegistrationValidator(
    private val ownership: PlatformOwnerMetadata,
    private val identity: PlatformIdentity,
) {
    /** Returns the files of [owner] or throws when its names, version, location or request are not updatable. */
    fun validate(owner: Any, product: ProductDescriptor, request: PluginUpdateRequest): ValidatedRegistration {
        val info = ownership.ownerDetails(owner)
        val nativeProductName = info["name"] ?: request.repositoryName
        listOf(nativeProductName, request.repositoryName).firstOrNull(UpdateCommandWords::isReserved)?.let { word ->
            throw IllegalArgumentException(
                "A plugin cannot be called '$word' for updates: it is a word of the /pn update command " +
                    "(${UpdateCommandWords.RESERVED.joinToString()}). Rename the plugin or its repository.",
            )
        }
        val version = info["version"] ?: error("Cannot determine the version of the registering plugin")
        require(SemanticVersion.tryParse(version) != null) {
            "Version of $nativeProductName must be semantic: $version"
        }
        val jar = Paths.get(owner.javaClass.protectionDomain.codeSource.location.toURI()).toAbsolutePath().normalize()
        require(Files.isRegularFile(jar)) { "Plugin must be loaded from a JAR file" }
        val pluginsDirectory = pluginsDirectoryOf(jar)
        request.configurationBackup?.let { backup ->
            require(backup.root != pluginsDirectory && backup.root.toAbsolutePath().normalize().startsWith(pluginsDirectory)) {
                "Configuration backup root must be a component data directory inside the plugins directory"
            }
        }
        val artifact = request.artifactFor(JavaRuntime.feature, identity.type)
            ?: error("No artifact of ${request.repositoryName} is registered for Java ${JavaRuntime.feature}")
        require(product.version == SemanticVersion.parse(version)) {
            "Product version ${product.version} does not match native plugin version $version"
        }
        return ValidatedRegistration(jar, pluginsDirectory, artifact)
    }

    private companion object {
        const val PAPER_REMAPPED = ".paper-remapped"

        /** Paper runs remapped copies from `plugins/.paper-remapped`; data folders and `update` still sit in `plugins`. */
        fun pluginsDirectoryOf(jar: Path): Path {
            val folder = jar.parent
            return if (folder.fileName?.toString() == PAPER_REMAPPED) folder.parent else folder
        }
    }
}
