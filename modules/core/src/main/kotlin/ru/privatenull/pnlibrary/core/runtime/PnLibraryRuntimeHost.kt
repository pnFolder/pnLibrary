package ru.privatenull.pnlibrary.core.runtime

import ru.privatenull.pnlibrary.api.runtime.PnLibrary
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
        (library as PlatformRegistration).registerPlatform(type, implementation)

    /** Stops the self-updater and closes the runtime. Safe to call repeatedly. */
    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        val failures = mutableListOf<Throwable>()
        runCatching { updateMonitor.close() }.onFailure(failures::add)
        runCatching {
            library.logging.shutdownBox(library.owner, "pnLibrary", library.version)
                .ok("Платформа", platformName)
                .ok("Ресурсы", "задачи и регистрации освобождаются")
                .show()
        }.onFailure(failures::add)
        runCatching { library.close() }.onFailure(failures::add)

        val first = failures.firstOrNull() ?: return
        failures.drop(1).forEach(first::addSuppressed)
        throw first
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
         * @param updateDirectory retained for binary/source compatibility. The update service
         * derives its staging directory from the installed component JAR.
         */
        @JvmStatic
        fun start(
            owner: Any,
            platform: PlatformAdapter,
        ): PnLibraryRuntimeHost = startInternal(owner, platform)

        /**
         * Legacy overload retained for integrations compiled against the old startup contract.
         * The update service now derives its staging directory from the installed component JAR.
         */
        @Deprecated(
            message = "The updateDirectory argument is ignored; use start(owner, platform)",
            replaceWith = ReplaceWith("start(owner, platform)"),
        )
        @JvmStatic
        @Suppress("UNUSED_PARAMETER")
        fun start(
            owner: Any,
            platform: PlatformAdapter,
            updateDirectory: Path,
        ): PnLibraryRuntimeHost = startInternal(owner, platform)

        private fun startInternal(
            owner: Any,
            platform: PlatformAdapter,
        ): PnLibraryRuntimeHost {
            val started = RuntimeBootstrapCoordinator.start(owner, platform.toRuntimeContext())
            return try {
                PnLibraryRuntimeHost(started.library, started.updateMonitor, started.platformName).also {
                    runCatching {
                        val box = started.library.logging.box(owner, "pnLibrary", started.currentVersion)
                            .ok("Runtime", "общие сервисы запущены")
                            .ok("Платформа", started.platformName)
                            .ok("Обновления", "проверка релизов запущена")
                        box.show()
                    }
                }
            } catch (error: Throwable) {
                runCatching { started.library.close() }.onFailure(error::addSuppressed)
                throw error
            }
        }

        /** Starts the runtime and registers the native platform API transactionally. */
        @JvmStatic
        fun <T : Any> startAndRegisterPlatform(
            owner: Any,
            platform: PlatformAdapter,
            type: Class<T>,
            implementation: T,
        ): PnLibraryRuntimeHost {
            val host = start(owner, platform)
            return try {
                host.registerPlatform(type, implementation)
                host
            } catch (error: Throwable) {
                runCatching { host.close() }.onFailure(error::addSuppressed)
                throw error
            }
        }

        /** Legacy registration overload retained for existing platform integrations. */
        @Deprecated(
            message = "The updateDirectory argument is ignored; use startAndRegisterPlatform(owner, platform, type, implementation)",
            replaceWith = ReplaceWith("startAndRegisterPlatform(owner, platform, type, implementation)"),
        )
        @JvmStatic
        @Suppress("UNUSED_PARAMETER")
        fun <T : Any> startAndRegisterPlatform(
            owner: Any,
            platform: PlatformAdapter,
            updateDirectory: Path,
            type: Class<T>,
            implementation: T,
        ): PnLibraryRuntimeHost = startAndRegisterPlatform(owner, platform, type, implementation)
    }
}
