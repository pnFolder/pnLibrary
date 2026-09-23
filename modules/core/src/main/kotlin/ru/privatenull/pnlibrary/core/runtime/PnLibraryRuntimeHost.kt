package ru.privatenull.pnlibrary.core.runtime

import ru.privatenull.pnlibrary.api.platform.PlatformType
import ru.privatenull.pnlibrary.api.runtime.PnLibrary
import ru.privatenull.pnlibrary.api.updates.PluginUpdateRequest
import ru.privatenull.pnlibrary.spi.platform.PlatformAdapter
import java.nio.file.Path
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

    /**
     * Registers a platform-specific service in the runtime registry.
     *
     * The registration is removed when [library] closes. The registry does not call
     * `AutoCloseable.close()` on [service], so the platform entry point must separately close a
     * service that owns native resources.
     */
    fun <T : Any> registerService(
        type: Class<T>,
        service: T,
        priority: Int = 0,
    ) =
        library.services.register(type, service, priority)

    /** Registers the type-safe public API for the active native platform. */
    fun <T : Any> registerPlatform(type: Class<T>, implementation: T): AutoCloseable =
        (library as PnLibraryImpl).registerPlatform(type, implementation)

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

    /** Complete runtime bootstrap entry point used by platform plugins. */
    companion object {
        /**
         * Starts a complete pnLibrary runtime for one platform.
         *
         * Initialization loads runtime configuration, bootstraps and binds the shared runtime,
         * starts mandatory update monitoring, and finally emits the startup summary. Any failure
         * after runtime creation closes that runtime before propagating the original error.
         *
         * @param owner native pnLibrary plugin instance
         * @param platform adapter for the current server or proxy
         * @param updateDirectory native directory for staged updates
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
                val monitor = library.updates.register(
                    owner,
                    ru.privatenull.pnlibrary.api.updates.ProductDescriptor.library(currentVersion),
                    PluginUpdateRequest.builder()
                        .repository("pnFolder", "pnLibrary")
                        .supportedApi(1, 1)
                        .automaticDownload(false)
                        .artifact("(?i)^pnLibrary-$artifactId-.*\\.jar$", 8)
                        .build(),
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
    else "${type.displayName} / $implementationName"

private fun PlatformType.distributionArtifact(): String = when (this) {
    PlatformType.BUKKIT -> "bukkit"
    PlatformType.BUNGEECORD -> "bungee"
    PlatformType.VELOCITY -> "velocity"
}
