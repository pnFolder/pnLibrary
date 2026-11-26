package org.pnfolder.pnlibrary.core.runtime.platform;

import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.platform.PlatformProvider;

/** Small runtime registry for the single native platform implementation. */
public final class NativePlatform implements PlatformProvider, AutoCloseable {
    private final ConcurrentHashMap<Class<?>, Object> implementations = new ConcurrentHashMap<>();

    @Override
    public <T> @Nullable T get(@NotNull Class<T> type) {
        Object implementation = implementations.get(type);
        return implementation == null ? null : type.cast(implementation);
    }

    @Override
    public <T> @NotNull T require(@NotNull Class<T> type) {
        T implementation = get(type);
        if (implementation == null) {
            StringBuilder registered = new StringBuilder();
            for (Class<?> known : implementations.keySet()) {
                registered.append(registered.length() == 0 ? "" : ", ").append(known.getName());
            }
            throw new IllegalStateException("Platform API " + type.getName() + " is not available in this runtime; registered APIs: "
                    + (registered.length() == 0 ? "none" : registered));
        }
        return implementation;
    }

    public <T> @NotNull AutoCloseable register(@NotNull final Class<T> type, @NotNull final T implementation) {
        if (implementations.putIfAbsent(type, implementation) != null) {
            throw new IllegalArgumentException("Platform API " + type.getName() + " is already registered");
        }
        return () -> implementations.remove(type, implementation);
    }

    @Override
    public void close() {
        implementations.clear();
    }
}
