package org.pnfolder.pnlibrary.config;

import java.util.Objects;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.config.ConfigApply;
import org.pnfolder.pnlibrary.api.config.ConfigChange;
import org.pnfolder.pnlibrary.api.config.ConfigChangeCause;
import org.pnfolder.pnlibrary.api.config.ConfigEditException;
import org.pnfolder.pnlibrary.api.config.ConfigEditResult;
import org.pnfolder.pnlibrary.api.config.ConfigId;
import org.pnfolder.pnlibrary.api.config.ConfigInfo;
import org.pnfolder.pnlibrary.api.config.ConfigLoadResult;
import org.pnfolder.pnlibrary.api.config.ConfigProblem;
import org.pnfolder.pnlibrary.api.config.ConfigSetting;
import org.pnfolder.pnlibrary.api.config.ConfigValidationException;
import org.pnfolder.pnlibrary.api.config.ManagedConfig;
import org.pnfolder.pnlibrary.api.config.ReloadRequest;
import org.pnfolder.pnlibrary.api.logging.PnLogger;
import org.pnfolder.pnlibrary.common.lang.Failures;
import org.pnfolder.pnlibrary.config.yaml.CodeFirstYaml;
import org.pnfolder.pnlibrary.config.yaml.ConfigSettingsEditor;
import org.pnfolder.pnlibrary.config.yaml.UnknownConfigTypeException;

/**
 * A managed file that remembers its identifier and path. It tells when it was loaded and whether it is waiting for a type,
 * which is the case when its last read failed or skipped list elements because a {@code type} word was unknown.
 */
final class OwnedConfig<T> implements ManagedConfig<T> {
    private final ConfigId id;
    private final Path file;
    private final Class<?> modelType;
    private final boolean readOnly;
    private final CodeFirstYaml<T> delegate;
    private final ConfigSettingsEditor editor;
    private final PnLogger logger;
    private volatile Instant loadedAt;
    private volatile boolean waitingForTypes;
    /** The strongest requirement of edits that do not work yet; a read clears {@link ConfigApply#RELOAD}, a restart clears the rest. */
    private volatile ConfigApply pending;
    private final CopyOnWriteArrayList<Consumer<ConfigChange>> changeListeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Consumer<ReloadRequest>> reloadListeners = new CopyOnWriteArrayList<>();

    OwnedConfig(@NotNull ConfigId id, @NotNull Path file, @NotNull Class<?> modelType, boolean readOnly,
                @NotNull CodeFirstYaml<T> delegate, @NotNull ConfigSettingsEditor editor, @NotNull PnLogger logger) {
        this.id = Objects.requireNonNull(id, "id");
        this.file = Objects.requireNonNull(file, "file");
        this.modelType = Objects.requireNonNull(modelType, "modelType");
        this.readOnly = readOnly;
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.editor = Objects.requireNonNull(editor, "editor");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    Path getFile() {
        return file;
    }

    boolean isReadOnly() {
        return readOnly;
    }

    /** Whether reading again could bring back what the last read left out. */
    boolean isWaitingForTypes() {
        return waitingForTypes;
    }

    @Override
    @NotNull
    public ConfigId getId() {
        return id;
    }

    @Override
    @NotNull
    public ConfigInfo getInfo() {
        List<ConfigProblem> problems = Collections.emptyList();
        if (isLoaded()) {
            problems = Failures.runCatchingNonFatal(delegate::validate).getOrDefault(problems);
        }
        return new ConfigInfo(id, modelType, file, Files.exists(file), isLoaded(), loadedAt, readOnly,
                delegate.getSkippedUnknownTypes(), problems, pending);
    }

    @Override
    @NotNull
    public List<ConfigSetting> getSettings() {
        return editor.describe(isLoaded() ? delegate.get() : null);
    }

    @Override
    @NotNull
    public ConfigEditResult edit(@NotNull String path, @NotNull String text) {
        if (readOnly) {
            throw new IllegalStateException("Файл " + id + " открыт только для чтения");
        }
        ConfigSettingsEditor.Applied applied;
        synchronized (delegate) {
            if (!isLoaded()) {
                throw new IllegalStateException("Файл " + id + " не загружен");
            }
            applied = editor.set(delegate.get(), path, text);
            save(applied);
        }
        ConfigEditResult result = new ConfigEditResult(applied.getSetting(), applied.getPrevious());
        if (applied.getSetting().getApply() != ConfigApply.LIVE) {
            pending = strongest(pending, applied.getSetting().getApply());
            fire(reloadListeners, new ReloadRequest(id, applied.getSetting().getApply(),
                    Collections.singletonList(applied.getSetting())));
        }
        fire(changeListeners, new ConfigChange(id, ConfigChangeCause.EDITED, Collections.singletonList(path)));
        return result;
    }

    private void save(ConfigSettingsEditor.Applied applied) {
        try {
            delegate.save();
        } catch (ConfigValidationException error) {
            applied.restore();
            StringBuilder message = new StringBuilder();
            for (ConfigProblem problem : error.getProblems()) {
                if (message.length() > 0) {
                    message.append("; ");
                }
                message.append(problem.getPath()).append(": ").append(problem.getMessage());
            }
            throw new ConfigEditException(message.toString(), error);
        } catch (Exception error) {
            applied.restore();
            throw new ConfigEditException(error.getMessage() != null ? error.getMessage() : "Не удалось записать файл", error);
        }
    }

    @Override
    @NotNull
    @SafeVarargs
    public final AutoCloseable onChanged(@NotNull Consumer<ConfigChange>... listeners) {
        return subscribe(changeListeners, Arrays.asList(listeners));
    }

    @Override
    @NotNull
    @SafeVarargs
    public final AutoCloseable onReloadRequired(@NotNull Consumer<ReloadRequest>... listeners) {
        return subscribe(reloadListeners, Arrays.asList(listeners));
    }

    @Override
    public boolean isLoaded() {
        return delegate.isLoaded();
    }

    @Override
    public T get() {
        return delegate.get();
    }

    @Override
    @NotNull
    public ConfigLoadResult<T> load() {
        return track(ConfigChangeCause.LOADED, delegate::load);
    }

    @Override
    @NotNull
    public ConfigLoadResult<T> reload() {
        return track(ConfigChangeCause.RELOADED, delegate::reload);
    }

    @Override
    public T loadValue() {
        return load().getValue();
    }

    @Override
    public T reloadValue() {
        return reload().getValue();
    }

    @Override
    public void save() {
        delegate.save();
        fire(changeListeners, new ConfigChange(id, ConfigChangeCause.SAVED, Collections.emptyList()));
    }

    @Override
    public void save(T value) {
        delegate.save(value);
        fire(changeListeners, new ConfigChange(id, ConfigChangeCause.SAVED, Collections.emptyList()));
    }

    @Override
    public T update(@NotNull UnaryOperator<T> updater) {
        T updated = delegate.update(updater);
        fire(changeListeners, new ConfigChange(id, ConfigChangeCause.SAVED, Collections.emptyList()));
        return updated;
    }

    @Override
    @NotNull
    public List<ConfigProblem> validate() {
        return delegate.validate();
    }

    @Override
    public T resetToDefaults() {
        T defaults = delegate.resetToDefaults();
        fire(changeListeners, new ConfigChange(id, ConfigChangeCause.RESET, Collections.emptyList()));
        return defaults;
    }

    @Override
    public void unload() {
        delegate.unload();
        loadedAt = null;
    }

    @Override
    public void close() {
        unload();
    }

    private ConfigLoadResult<T> track(ConfigChangeCause cause, Supplier<ConfigLoadResult<T>> action) {
        ConfigLoadResult<T> result;
        try {
            result = action.get();
        } catch (Exception error) {
            waitingForTypes = causedByUnknownType(error);
            throw error;
        }
        loadedAt = Instant.now();
        waitingForTypes = !delegate.getSkippedUnknownTypes().isEmpty();
        if (pending == ConfigApply.RELOAD) {
            pending = null;
        }
        fire(changeListeners, new ConfigChange(id, cause, Collections.emptyList()));
        return result;
    }

    private static boolean causedByUnknownType(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current instanceof UnknownConfigTypeException) {
                return true;
            }
        }
        return false;
    }

    private static ConfigApply strongest(ConfigApply a, ConfigApply b) {
        return a == null || b.ordinal() > a.ordinal() ? b : a;
    }

    private static <E> AutoCloseable subscribe(CopyOnWriteArrayList<Consumer<E>> target, List<Consumer<E>> listeners) {
        target.addAll(listeners);
        return () -> target.removeAll(new HashSet<>(listeners));
    }

    /** A failing listener is logged and never stops the others or the change itself. */
    private <E> void fire(List<Consumer<E>> listeners, E event) {
        for (Consumer<E> listener : new ArrayList<>(listeners)) {
            try {
                listener.accept(event);
            } catch (Exception error) {
                logger.warning("Обработчик изменения " + id + " завершился ошибкой: " + error.getMessage(), error);
            }
        }
    }
}
