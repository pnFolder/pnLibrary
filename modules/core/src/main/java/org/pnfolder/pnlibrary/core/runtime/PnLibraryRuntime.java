package org.pnfolder.pnlibrary.core.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.common.lang.Failures;
import org.pnfolder.pnlibrary.spi.platform.PlatformAdapter;

/**
 * Entry point for starting a pnLibrary runtime.
 *
 * <pre>{@code
 * PnLibraryRuntimeHost host = PnLibraryRuntime.builder()
 *     .owner(plugin)
 *     .adapter(platformAdapter)
 *     .platform(VelocityPlatform.class, velocityPlatformImpl)
 *     .launch(platformAdapter::markReady);
 * }</pre>
 */
public final class PnLibraryRuntime {
    private PnLibraryRuntime() {
    }

    public static @NotNull Builder builder() {
        return new Builder();
    }

    /** Collects the owner, the platform adapter and the native platform APIs, then starts the runtime. */
    public static final class Builder {
        private Object owner;
        private PlatformAdapter adapter;
        private final List<Consumer<PnLibraryRuntimeHost>> registrations = new ArrayList<>();

        private Builder() {
        }

        public @NotNull Builder owner(@NotNull Object owner) {
            this.owner = owner;
            return this;
        }

        public @NotNull Builder adapter(@NotNull PlatformAdapter adapter) {
            this.adapter = adapter;
            return this;
        }

        public <T> @NotNull Builder platform(@NotNull final Class<T> type, @NotNull final T implementation) {
            registrations.add(host -> host.registerPlatform(type, implementation));
            return this;
        }

        public @NotNull PnLibraryRuntimeHost launch() {
            return launch(() -> { });
        }

        public @NotNull PnLibraryRuntimeHost launch(@NotNull Runnable onReady) {
            if (owner == null) {
                throw new IllegalArgumentException("owner is not set");
            }
            if (adapter == null) {
                throw new IllegalArgumentException("adapter is not set");
            }
            final PnLibraryRuntimeHost host = startOrCloseAdapter();
            try {
                for (Consumer<PnLibraryRuntimeHost> registration : registrations) {
                    registration.accept(host);
                }
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

        private PnLibraryRuntimeHost startOrCloseAdapter() {
            final PlatformAdapter platform = adapter;
            try {
                return PnLibraryRuntimeHost.start(owner, platform);
            } catch (Throwable error) {
                Failures.runCatchingNonFatal(() -> {
                    platform.close();
                    return null;
                }).onFailure(error::addSuppressed);
                throw error;
            }
        }
    }
}
