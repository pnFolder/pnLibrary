package ru.privatenull.pnlibrary.core.runtime

import ru.privatenull.pnlibrary.api.runtime.PnLibrary
import ru.privatenull.pnlibrary.api.plugin.RemotePolicy
import ru.privatenull.pnlibrary.spi.platform.PlatformAdapter
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
         * One code-owned policy declaration for pnLibrary on Bukkit, Velocity and Bungee.
         * Leave null to preserve normal startup. Only trusted policy source may be used:
         * it executes Java/Kotlin code with the server process's permissions.
         *
         * Replace null with, for example:
         * ```kotlin
         * RemotePolicy.builder()
         *     .source("https://example.org/pnLibrary/LibraryPolicy.kt")
         *     .checkEvery(java.time.Duration.ofHours(6))
         *     .value("product", "pnlibrary")
         *     .build()
         * ```
         * A library has no independently stoppable module: only DISABLE_PLUGIN is valid.
         * Checks begin after native readiness, asynchronously, just like consumer policies.
         * A denied result or a loading/compilation error stops pnLibrary using the same monitor.
         */
        private val LIBRARY_POLICY: RemotePolicy? = null

        /**
         * Starts a complete pnLibrary runtime for one platform.
         *
         * Initialization loads runtime configuration, bootstraps and binds the shared runtime,
         * starts mandatory update monitoring, and finally emits the startup summary. Any failure
         * after runtime creation closes that runtime before propagating the original error.
         *
         * @param owner native pnLibrary plugin instance
         * @param platform adapter for the current server or proxy
         * @param remotePolicy library-wide remote business policy; defaults to [LIBRARY_POLICY]
         */
        @JvmStatic
        @JvmOverloads
        fun start(
            owner: Any,
            platform: PlatformAdapter,
            remotePolicy: RemotePolicy? = LIBRARY_POLICY,
        ): PnLibraryRuntimeHost = startInternal(owner, platform, remotePolicy)

        private fun startInternal(
            owner: Any,
            platform: PlatformAdapter,
            remotePolicy: RemotePolicy?,
        ): PnLibraryRuntimeHost {
            // Reject invalid declarations before allocating or publishing a runtime.
            val policy = remotePolicy?.let(RuntimeRemotePolicy::validated)
            val started = RuntimeBootstrapCoordinator.start(owner, platform.toRuntimeContext())
            return try {
                PnLibraryRuntimeHost(started.library, started.updateMonitor, started.platformName).also { host ->
                    policy?.let { configured ->
                        RuntimeRemotePolicy.start(
                            library = started.library,
                            platform = platform,
                            policy = configured,
                            isClosed = { host.closed.get() || started.library.isClosed },
                            closeRuntime = host::close,
                        )
                    }
                    runCatching {
                        val box = started.library.logging.box(owner, "pnLibrary", started.currentVersion)
                            .ok("Runtime", "общие сервисы запущены")
                            .ok("Платформа", started.platformName)
                            .ok("Обновления", "проверка релизов запущена")
                        box.show()
                    }
                }
            } catch (error: Throwable) {
                runCatching { started.updateMonitor.close() }.onFailure(error::addSuppressed)
                runCatching { started.library.close() }.onFailure(error::addSuppressed)
                throw error
            }
        }

        /** Starts the runtime and registers the native platform API transactionally. */
        @JvmStatic
        @JvmOverloads
        fun <T : Any> startAndRegisterPlatform(
            owner: Any,
            platform: PlatformAdapter,
            type: Class<T>,
            implementation: T,
            remotePolicy: RemotePolicy? = LIBRARY_POLICY,
        ): PnLibraryRuntimeHost {
            val host = start(owner, platform, remotePolicy)
            return try {
                host.registerPlatform(type, implementation)
                host
            } catch (error: Throwable) {
                runCatching { host.close() }.onFailure(error::addSuppressed)
                throw error
            }
        }

        /**
         * Full platform entry-point bootstrap: starts the runtime, registers the native platform API,
         * then runs [onReady] to release startup-gated callbacks.
         *
         * Any failure closes everything created in this attempt and rethrows the original error, so
         * a plugin's lifecycle method only needs to store the returned host.
         */
        @JvmStatic
        @JvmOverloads
        fun <T : Any> launchPlatform(
            owner: Any,
            platform: PlatformAdapter,
            type: Class<T>,
            implementation: T,
            onReady: () -> Unit = {},
        ): PnLibraryRuntimeHost {
            val host = try {
                startAndRegisterPlatform(owner, platform, type, implementation)
            } catch (error: Throwable) {
                runCatching { platform.close() }.onFailure(error::addSuppressed)
                throw error
            }
            return try {
                onReady()
                host
            } catch (error: Throwable) {
                runCatching { host.close() }.onFailure(error::addSuppressed)
                throw error
            }
        }

    }
}
