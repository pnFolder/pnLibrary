package org.pnfolder.pnlibrary.core.runtime;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.remote.DenyAction;
import org.pnfolder.pnlibrary.api.remote.PolicyErrorAction;
import org.pnfolder.pnlibrary.api.remote.RemotePolicySettings;
import org.pnfolder.pnlibrary.api.runtime.PnLibrary;
import org.pnfolder.pnlibrary.api.runtime.PnLibraryIdentity;
import org.pnfolder.pnlibrary.common.lang.Failures;
import org.pnfolder.pnlibrary.core.runtime.details.RuntimeRemotePolicy;
import org.pnfolder.pnlibrary.core.runtime.lifecycle.RuntimeBootstrapCoordinator;
import org.pnfolder.pnlibrary.core.runtime.platform.PlatformRegistration;
import org.pnfolder.pnlibrary.core.runtime.platform.PlatformRuntimeContext;
import org.pnfolder.pnlibrary.spi.platform.PlatformAdapter;

/**
 * High-level lifecycle owner for an installed pnLibrary runtime.
 *
 * <p>{@link #start} loads configuration, creates the runtime, binds the platform adapter, starts the self-updater, and
 * prints a consistent startup summary. {@link #close} performs the matching shutdown sequence. Platform entry points
 * therefore do not depend on low-level {@code core} implementation classes.
 */
public final class PnLibraryRuntimeHost implements AutoCloseable {
    /**
     * The policy of pnLibrary itself on Bukkit, Velocity and BungeeCord, published in the {@code pnlibrary-policy}
     * repository. It executes Java code with the server's permissions, so only that trusted source is used.
     *
     * <p>Checks begin after native readiness, asynchronously, and repeat every two hours. Only an explicit denial stops
     * pnLibrary (a library has no module to stop, so DISABLE_PLUGIN is the only valid action). A policy that cannot be
     * downloaded or compiled at start, for example offline or on a JRE without {@code javac}, stops pnLibrary too; after one
     * successful check such a failure only logs a warning.
     */
    private static final RemotePolicySettings LIBRARY_POLICY = RemotePolicySettings.builder()
            .source("https://raw.githubusercontent.com/pnFolder/pnlibrary-policy/main/.pnlibrary/policies/PnLibraryPolicy.java")
            .onDeny(DenyAction.DISABLE_PLUGIN)
            .checkEvery(Duration.ofHours(2))
            .onError(PolicyErrorAction.DENY_UNTIL_CHECKED)
            .build();

    private final PnLibrary library;
    private final AutoCloseable updateMonitor;
    private final String platformName;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    private PnLibraryRuntimeHost(PnLibrary library, AutoCloseable updateMonitor, String platformName) {
        this.library = library;
        this.updateMonitor = updateMonitor;
        this.platformName = platformName;
    }

    /** Public runtime that may be registered in the platform service registry. */
    public @NotNull PnLibrary getLibrary() {
        return library;
    }

    /**
     * Registers a platform-specific service in the runtime registry.
     *
     * <p>The registration is removed when the library closes. The registry does not call {@code AutoCloseable.close()} on the
     * service, so the platform entry point must separately close a service that owns native resources.
     */
    public <T> void registerService(@NotNull Class<T> type, @NotNull T service, int priority) {
        library.getServices().register(type, service, priority);
    }

    public <T> void registerService(@NotNull Class<T> type, @NotNull T service) {
        registerService(type, service, 0);
    }

    public <T> @NotNull AutoCloseable registerPlatform(@NotNull Class<T> type, @NotNull T implementation) {
        return ((PlatformRegistration) library).registerPlatform(type, implementation);
    }

    /** Stops the self-updater and closes the runtime. Safe to call repeatedly. */
    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        final List<Throwable> failures = new ArrayList<Throwable>();
        Failures.runCatchingNonFatal(() -> {
            updateMonitor.close();
            return null;
        }).onFailure(failures::add);
        Failures.runCatchingNonFatal(() -> {
            library.getLogging().shutdownBox(library.getOwner(), PnLibraryIdentity.NAME, library.getVersion())
                    .ok("Платформа", platformName)
                    .ok("Ресурсы", "задачи и регистрации освобождаются")
                    .show();
            return null;
        }).onFailure(failures::add);
        Failures.runCatchingNonFatal(() -> {
            library.close();
            return null;
        }).onFailure(failures::add);
        if (failures.isEmpty()) {
            return;
        }
        Throwable first = failures.get(0);
        for (Throwable other : failures.subList(1, failures.size())) {
            first.addSuppressed(other);
        }
        throwUnchecked(first);
    }

    @SuppressWarnings("unchecked")
    private static <E extends Throwable> void throwUnchecked(Throwable error) throws E {
        throw (E) error;
    }

    public static @NotNull PnLibraryRuntimeHost start(@NotNull Object owner, @NotNull PlatformAdapter platform) {
        return start(owner, platform, LIBRARY_POLICY);
    }

    /**
     * Starts a complete pnLibrary runtime for one platform.
     *
     * <p>Initialization loads runtime configuration, bootstraps and binds the shared runtime, starts mandatory update
     * monitoring, and finally emits the startup summary. Any failure after runtime creation closes that runtime before
     * propagating the original error.
     *
     * @param owner native pnLibrary plugin instance
     * @param platform adapter for the current server or proxy
     * @param remotePolicy library-wide remote business policy, or {@code null} for none
     */
    public static @NotNull PnLibraryRuntimeHost start(@NotNull Object owner, @NotNull PlatformAdapter platform,
                                                      @Nullable RemotePolicySettings remotePolicy) {
        // Reject invalid declarations before allocating or publishing a runtime.
        RemotePolicySettings policy = remotePolicy == null ? null : RuntimeRemotePolicy.validated(remotePolicy);
        final RuntimeBootstrapCoordinator.Started started =
                RuntimeBootstrapCoordinator.start(owner, PlatformRuntimeContext.from(platform));
        try {
            final PnLibraryRuntimeHost host = new PnLibraryRuntimeHost(started.getLibrary(), started.getUpdateMonitor(),
                    started.getPlatformName());
            if (policy != null) {
                RuntimeRemotePolicy.start(started.getLibrary(), platform, policy,
                        () -> host.closed.get() || started.getLibrary().isClosed(), host::close);
            }
            announce(owner, started);
            return host;
        } catch (Throwable error) {
            Failures.runCatchingNonFatal(() -> {
                started.getUpdateMonitor().close();
                return null;
            }).onFailure(error::addSuppressed);
            Failures.runCatchingNonFatal(() -> {
                started.getLibrary().close();
                return null;
            }).onFailure(error::addSuppressed);
            throw error;
        }
    }

    private static void announce(Object owner, RuntimeBootstrapCoordinator.Started started) {
        Failures.runCatchingNonFatal(() -> {
            started.getLibrary().getLogging().box(owner, PnLibraryIdentity.NAME, started.getCurrentVersion())
                    .ok("Runtime", "общие сервисы запущены")
                    .ok("Платформа", started.getPlatformName())
                    .ok("Обновления", "проверка релизов запущена")
                    .show();
            return null;
        });
    }

    public static <T> @NotNull PnLibraryRuntimeHost startAndRegisterPlatform(@NotNull Object owner, @NotNull PlatformAdapter platform,
                                                                              @NotNull Class<T> type, @NotNull T implementation) {
        return startAndRegisterPlatform(owner, platform, type, implementation, LIBRARY_POLICY);
    }

    public static <T> @NotNull PnLibraryRuntimeHost startAndRegisterPlatform(@NotNull Object owner, @NotNull PlatformAdapter platform,
                                                                              @NotNull Class<T> type, @NotNull T implementation,
                                                                              @Nullable RemotePolicySettings remotePolicy) {
        final PnLibraryRuntimeHost host = start(owner, platform, remotePolicy);
        try {
            host.registerPlatform(type, implementation);
            return host;
        } catch (Throwable error) {
            Failures.runCatchingNonFatal(() -> {
                host.close();
                return null;
            }).onFailure(error::addSuppressed);
            throw error;
        }
    }

    public static <T> @NotNull PnLibraryRuntimeHost launchPlatform(@NotNull Object owner, @NotNull PlatformAdapter platform,
                                                                    @NotNull Class<T> type, @NotNull T implementation) {
        return launchPlatform(owner, platform, type, implementation, () -> { });
    }

    /**
     * Full platform entry-point bootstrap: starts the runtime, registers the native platform API, then runs {@code onReady}
     * to release startup-gated callbacks.
     *
     * <p>Any failure closes everything created in this attempt and rethrows the original error, so a plugin's lifecycle method
     * only needs to store the returned host.
     */
    public static <T> @NotNull PnLibraryRuntimeHost launchPlatform(@NotNull Object owner, @NotNull final PlatformAdapter platform,
                                                                    @NotNull Class<T> type, @NotNull T implementation,
                                                                    @NotNull Runnable onReady) {
        final PnLibraryRuntimeHost host;
        try {
            host = startAndRegisterPlatform(owner, platform, type, implementation);
        } catch (Throwable error) {
            Failures.runCatchingNonFatal(() -> {
                platform.close();
                return null;
            }).onFailure(error::addSuppressed);
            throw error;
        }
        try {
            onReady.run();
            return host;
        } catch (Throwable error) {
            Failures.runCatchingNonFatal(() -> {
                host.close();
                return null;
            }).onFailure(error::addSuppressed);
            throw error;
        }
    }
}
