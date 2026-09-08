package ru.privatenull.pnlibrary.core.runtime

import ru.privatenull.pnlibrary.spi.platform.PlatformAdapter
import ru.privatenull.pnlibrary.api.runtime.PnLibrary
import ru.privatenull.pnlibrary.api.platform.PlatformType
import ru.privatenull.pnlibrary.core.updates.MandatoryUpdateService
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.atomic.AtomicBoolean

/**
 * High-level lifecycle owner for an installed pnLibrary runtime.
 *
 * [start] loads configuration, creates the runtime, binds the platform adapter,
 * starts the self-updater, and prints a consistent startup summary. [close]
 * performs the matching shutdown sequence. Platform entry points therefore do
 * not depend on low-level `core` implementation classes.
 */
class PnLibraryRuntimeHost private constructor(
    /** Public runtime that may be registered in the platform service registry. */
    val library: PnLibrary,
    private val updateMonitor: AutoCloseable,
    private val platformName: String,
) : AutoCloseable {

    private val closed = AtomicBoolean(false)

    /** Stops the self-updater and closes the runtime. Safe to call repeatedly. */
    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        runCatching { updateMonitor.close() }
        runCatching {
            library.logging.shutdownBox(library.owner, "pnLibrary", library.version)
                .ok("Платформа", platformName)
                .ok("Ресурсы", "задачи и регистрации освобождаются")
                .show()
        }
        library.close()
    }

    companion object {
        /**
         * Starts a complete pnLibrary runtime for one platform.
         *
         * @param owner native pnLibrary plugin instance;
         * @param platform adapter for the current server or proxy;
         * @param updateDirectory native directory for staged updates.
         */
        @JvmStatic
        fun start(
            owner: Any,
            platform: PlatformAdapter,
            updateDirectory: Path,
        ): PnLibraryRuntimeHost {
            val dataFolder = requireNotNull(platform.dataFolder) {
                "Platform adapter must provide the pnLibrary data folder"
            }
            val library = PnLibraryBootstrap.bootstrap(
                owner,
                platform,
                PnLibraryConfigLoader.load(dataFolder),
            )

            return try {
                val currentVersion = platform.ownerDetails(owner)["version"]
                    ?.takeIf { it.isNotBlank() }
                    ?: error("The platform did not expose the pnLibrary version")
                val artifactId = platform.type.distributionArtifact()
                val currentJar = Paths.get(owner.javaClass.protectionDomain.codeSource.location.toURI())
                val monitor = MandatoryUpdateService.start(
                    owner,
                    platform,
                    currentVersion,
                    artifactId,
                    currentJar,
                    updateDirectory,
                )
                PnLibraryRuntimeHost(library, monitor, platform.summaryName()).also {
                    runCatching {
                        val box = library.logging.box(owner, "pnLibrary", currentVersion)
                            .ok("Runtime", "общие сервисы запущены")
                            .ok("Платформа", platform.summaryName())
                            .ok("Обновления", "проверка релизов запущена")
                        box.show()
                    }
                }
            } catch (error: Throwable) {
                library.close()
                throw error
            }
        }
    }
}

private fun PlatformAdapter.summaryName(): String =
    if (implementationName.equals(type.displayName, ignoreCase = true)) type.displayName
    else "${type.displayName} · $implementationName"

private fun PlatformType.distributionArtifact(): String = when (this) {
    PlatformType.BUKKIT -> "bukkit"
    PlatformType.BUNGEECORD -> "bungee"
    PlatformType.VELOCITY -> "velocity"
}
