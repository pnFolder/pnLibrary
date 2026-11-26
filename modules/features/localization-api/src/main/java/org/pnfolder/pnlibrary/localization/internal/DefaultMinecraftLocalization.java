package org.pnfolder.pnlibrary.localization.internal;

import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.common.minecraft.MinecraftVersion;
import org.pnfolder.pnlibrary.localization.MinecraftLocalization;
import org.pnfolder.pnlibrary.localization.TranslationBundle;
import org.pnfolder.pnlibrary.localization.TranslationException;
import org.pnfolder.pnlibrary.localization.TranslationMetadata;
import org.pnfolder.pnlibrary.localization.TranslationRequest;
import org.pnfolder.pnlibrary.localization.TranslationSource;
import org.pnfolder.pnlibrary.localization.layout.KeyboardLayouts;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Default Minecraft localization service: resolves Mojang language assets, caches verified translations
 * and serves them per locale. Created through {@link MinecraftLocalization#builder()}.
 */
public final class DefaultMinecraftLocalization implements MinecraftLocalization {
    private static final int MAX_LANGUAGE_BYTES = 16 * 1024 * 1024;
    private static final int MAX_MANIFEST_BYTES = 4 * 1024 * 1024;

    private final Duration manifestTtl;
    private final KeyboardLayouts layouts;
    private final boolean ownsExecutor;
    private final ExecutorService executor;
    private final VerifiedFileStore store;
    private final MojangAssetResolver resolver;
    private final Semaphore permits;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final Object lifecycle = new Object();
    private final Set<CompletableFuture<?>> pending = new HashSet<CompletableFuture<?>>();
    private final Map<CacheKey, CompletableFuture<LoadedLocale>> inFlight =
            new ConcurrentHashMap<CacheKey, CompletableFuture<LoadedLocale>>();
    private final Map<CacheKey, LoadedLocale> memory;

    /** Creates a service; plugin code uses {@link MinecraftLocalization#builder()} instead. */
    public DefaultMinecraftLocalization(@NotNull Path cacheDirectory, @NotNull Duration manifestTtl,
                                        @NotNull Duration connectTimeout, @NotNull Duration readTimeout,
                                        int memoryEntries, int downloadConcurrency,
                                        @Nullable ExecutorService suppliedExecutor, @NotNull KeyboardLayouts layouts) {
        this(cacheDirectory, manifestTtl, connectTimeout, readTimeout, memoryEntries, downloadConcurrency,
                suppliedExecutor, null, layouts);
    }

    DefaultMinecraftLocalization(Path cacheDirectory, Duration manifestTtl, Duration connectTimeout,
                                 Duration readTimeout, final int memoryEntries, int downloadConcurrency,
                                 ExecutorService suppliedExecutor, LocalizationHttpClient httpClient,
                                 KeyboardLayouts layouts) {
        this.manifestTtl = Objects.requireNonNull(manifestTtl, "manifestTtl");
        this.layouts = Objects.requireNonNull(layouts, "layouts");
        this.ownsExecutor = suppliedExecutor == null;
        this.executor = suppliedExecutor != null ? suppliedExecutor
                : Executors.newFixedThreadPool(downloadConcurrency, daemonThreads());
        this.store = new VerifiedFileStore(cacheDirectory.resolve("minecraft"));
        this.resolver = new MojangAssetResolver(
                httpClient != null ? httpClient : new LocalizationHttpClient(connectTimeout, readTimeout), store);
        this.permits = new Semaphore(downloadConcurrency);
        this.memory = new LinkedHashMap<CacheKey, LoadedLocale>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<CacheKey, LoadedLocale> eldest) {
                return size() > memoryEntries;
            }
        };
    }

    @NotNull
    @Override
    public KeyboardLayouts getLayouts() {
        return layouts;
    }

    @NotNull
    @Override
    public CompletionStage<List<MinecraftVersion>> availableVersions() {
        return async(new Operation<List<MinecraftVersion>>() {
            @Override
            public List<MinecraftVersion> run() throws Exception {
                byte[] manifest = manifest(true);
                return manifest == null ? MinecraftVersion.supported() : resolver.versions(manifest);
            }
        });
    }

    @NotNull
    @Override
    public CompletionStage<List<String>> availableLocales(@NotNull final MinecraftVersion version) {
        return async(new Operation<List<String>>() {
            @Override
            public List<String> run() throws Exception {
                requireKnown(version);
                byte[] manifest = manifest(false);
                if (manifest == null) {
                    throw offline();
                }
                return resolver.locales(version, manifest);
            }
        });
    }

    @NotNull
    @Override
    public CompletionStage<TranslationBundle> load(@NotNull TranslationRequest request) {
        return load(request, false);
    }

    @NotNull
    @Override
    public CompletionStage<TranslationBundle> refresh(@NotNull TranslationRequest request) {
        return load(request, true);
    }

    /** Loads every requested locale plus the fallback in parallel; {@code refresh} skips the memory and disk caches. */
    private CompletionStage<TranslationBundle> load(final TranslationRequest request, boolean refresh) {
        ensureOpen();
        Set<String> requested = new LinkedHashSet<String>(request.getLocales());
        if (request.getFallbackLocale() != null) {
            requested.add(request.getFallbackLocale());
        }
        final Map<String, CompletableFuture<LoadedLocale>> stages =
                new LinkedHashMap<String, CompletableFuture<LoadedLocale>>();
        for (String locale : requested) {
            stages.put(locale, loadLocale(request.getVersion(), locale, refresh));
        }
        CompletableFuture<?>[] all = stages.values().toArray(new CompletableFuture<?>[0]);
        return CompletableFuture.allOf(all).<TranslationBundle>thenApply(ignored -> {
            Map<String, LoadedLocale> loaded = new LinkedHashMap<String, LoadedLocale>();
            for (Map.Entry<String, CompletableFuture<LoadedLocale>> entry : stages.entrySet()) {
                loaded.put(entry.getKey(), entry.getValue().join());
            }
            return new DefaultTranslationBundle(
                    request.getVersion(), request.getLocales(), request.getFallbackLocale(), loaded);
        });
    }

    /** Returns the locale from memory or a shared in-flight download, starting one when needed. */
    private CompletableFuture<LoadedLocale> loadLocale(MinecraftVersion version, String locale, final boolean refresh) {
        requireKnown(version);
        String normalized;
        try {
            normalized = TranslationRequest.normalizeLocale(locale);
        } catch (IllegalArgumentException error) {
            return failed(new TranslationException(TranslationException.Reason.INVALID_LOCALE,
                    error.getMessage() != null ? error.getMessage() : "Invalid locale", error));
        }
        final CacheKey key = new CacheKey(version, normalized);
        if (!refresh) {
            LoadedLocale cached;
            synchronized (memory) {
                cached = memory.get(key);
            }
            if (cached != null) {
                return CompletableFuture.completedFuture(cached.withSource(TranslationSource.MEMORY));
            }
        }
        CompletableFuture<LoadedLocale> running = inFlight.get(key);
        if (running != null) {
            return running;
        }
        final CompletableFuture<LoadedLocale> promise = new CompletableFuture<LoadedLocale>();
        CompletableFuture<LoadedLocale> existing = inFlight.putIfAbsent(key, promise);
        if (existing != null) {
            return existing;
        }
        return submit(promise, () -> inFlight.remove(key, promise), new Operation<LoadedLocale>() {
            @Override
            public LoadedLocale run() throws Exception {
                permits.acquire();
                try {
                    return loadLocaleNow(key, refresh);
                } finally {
                    permits.release();
                }
            }
        });
    }

    /** Reads the locale from disk, else downloads it; on a failed refresh falls back to the stale disk copy. */
    private LoadedLocale loadLocaleNow(CacheKey key, boolean refresh) throws IOException {
        Path path = store.translation(key.version.getText(), key.locale);
        if (!refresh) {
            byte[] bytes = store.read(path, MAX_LANGUAGE_BYTES);
            if (bytes != null) {
                try {
                    return cache(key, bytes, new TranslationMetadata(TranslationSource.DISK, store.sha1(bytes), false));
                } catch (TranslationException error) {
                    store.quarantine(path);
                }
            }
        }
        try {
            byte[] manifest = manifest(false);
            if (manifest == null) {
                throw offline();
            }
            ResolvedLanguage resolved = resolver.language(key.version, key.locale, manifest);
            LoadedLocale loaded = cache(key, resolved.getBytes(),
                    new TranslationMetadata(TranslationSource.NETWORK, resolved.getHash(), false));
            store.write(path, resolved.getBytes());
            return loaded;
        } catch (TranslationException error) {
            if (refresh) {
                byte[] bytes = store.read(path, MAX_LANGUAGE_BYTES);
                if (bytes != null) {
                    return cache(key, bytes,
                            new TranslationMetadata(TranslationSource.STALE_DISK, store.sha1(bytes), true));
                }
            }
            throw error;
        }
    }

    /** Parses {@code bytes} and keeps the result in the memory cache unless the service is closed. */
    private LoadedLocale cache(CacheKey key, byte[] bytes, TranslationMetadata metadata) {
        LoadedLocale loaded = new LoadedLocale(key.locale, TranslationJsonCodec.parse(bytes), metadata, layouts);
        synchronized (memory) {
            if (!closed.get()) {
                memory.put(key, loaded);
            }
        }
        return loaded;
    }

    /** Returns the version manifest, re-downloading it after the TTL; {@code allowStale} tolerates a failed refresh. */
    private synchronized byte[] manifest(boolean allowStale) throws IOException {
        Path path = store.manifest();
        byte[] cached = store.read(path, MAX_MANIFEST_BYTES);
        if (cached != null) {
            try {
                resolver.validateManifest(cached);
            } catch (TranslationException error) {
                store.quarantine(path);
                cached = null;
            }
        }
        boolean fresh = cached != null
                && Duration.ofMillis(System.currentTimeMillis() - Files.getLastModifiedTime(path).toMillis())
                        .compareTo(manifestTtl) <= 0;
        if (fresh) {
            return cached;
        }
        try {
            byte[] bytes = resolver.manifestBytes();
            store.write(path, bytes);
            return bytes;
        } catch (TranslationException error) {
            if (allowStale) {
                return cached;
            }
            throw error;
        }
    }

    /** Runs {@code action} on the executor and returns its future. */
    private <T> CompletableFuture<T> async(Operation<T> action) {
        ensureOpen();
        return submit(new CompletableFuture<T>(), () -> {
            }, action);
    }

    /** Executes {@code action} on the executor and completes {@code promise}; {@code beforeCompletion} always runs first. */
    private <T> CompletableFuture<T> submit(final CompletableFuture<T> promise, final Runnable beforeCompletion,
                                            final Operation<T> action) {
        boolean accepted;
        synchronized (lifecycle) {
            accepted = !closed.get();
            if (accepted) {
                pending.add(promise);
            }
        }
        if (!accepted) {
            beforeCompletion.run();
            promise.completeExceptionally(closedError());
            return promise;
        }
        promise.whenComplete((result, error) -> {
            synchronized (lifecycle) {
                pending.remove(promise);
            }
        });
        try {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    if (promise.isDone()) {
                        return;
                    }
                    try {
                        ensureOpen();
                        T result = action.run();
                        // Release download ownership before waking callers, including refresh requests.
                        beforeCompletion.run();
                        promise.complete(result);
                    } catch (Throwable error) {
                        beforeCompletion.run();
                        promise.completeExceptionally(error);
                    }
                }
            });
        } catch (RejectedExecutionException error) {
            beforeCompletion.run();
            promise.completeExceptionally(error);
        }
        return promise;
    }

    /** Throws a CLOSED {@link TranslationException} after {@link #close()}. */
    private void ensureOpen() {
        if (closed.get()) {
            throw closedError();
        }
    }

    /** Rejects the UNKNOWN version, which has no assets. */
    private static void requireKnown(MinecraftVersion version) {
        if (!version.isKnown()) {
            throw new TranslationException(
                    TranslationException.Reason.UNSUPPORTED_VERSION, "UNKNOWN Minecraft version is not supported");
        }
    }

    @Override
    public void close() {
        List<CompletableFuture<?>> abandoned;
        synchronized (lifecycle) {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            abandoned = new ArrayList<CompletableFuture<?>>(pending);
            pending.clear();
        }
        inFlight.clear();
        for (CompletableFuture<?> future : abandoned) {
            future.completeExceptionally(closedError());
        }
        synchronized (memory) {
            memory.clear();
        }
        if (ownsExecutor) {
            executor.shutdownNow();
        }
    }

    /** Error given to callers after the service is closed. */
    private static TranslationException closedError() {
        return new TranslationException(TranslationException.Reason.CLOSED, "Minecraft localization service is closed");
    }

    /** Error given when a download is needed but no manifest was ever cached. */
    private static TranslationException offline() {
        return new TranslationException(TranslationException.Reason.OFFLINE, "No cached Minecraft manifest is available");
    }

    /** Already failed future carrying {@code error}. */
    private static <T> CompletableFuture<T> failed(Throwable error) {
        CompletableFuture<T> future = new CompletableFuture<T>();
        future.completeExceptionally(error);
        return future;
    }

    private static ThreadFactory daemonThreads() {
        return task -> {
            Thread thread = new Thread(task, "pnlibrary-localization");
            thread.setDaemon(true);
            return thread;
        };
    }

    /** Work that runs on the executor and may fail with any exception. */
    private interface Operation<T> {
        T run() throws Exception;
    }

    /** Memory and in-flight cache key: one language file of one version. */
    private static final class CacheKey {
        private final MinecraftVersion version;
        private final String locale;

        CacheKey(MinecraftVersion version, String locale) {
            this.version = version;
            this.locale = locale;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof CacheKey
                    && version == ((CacheKey) other).version && locale.equals(((CacheKey) other).locale);
        }

        @Override
        public int hashCode() {
            return 31 * version.hashCode() + locale.hashCode();
        }
    }
}
