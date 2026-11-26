package org.pnfolder.pnlibrary.updates.policy;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.updates.product.UpdateChannel;
import org.pnfolder.pnlibrary.common.lang.Failures;
import org.pnfolder.pnlibrary.updates.engine.UpdatePreferencesStore;

/**
 * The channel and automatic-download choices an administrator makes with commands or in the menu: one per component,
 * and one for all plugins together. They are kept until changed again and take precedence over the defaults.
 *
 * <p>The choices are the channel, automatic and all-plugins sections of the store; the pause section belongs to
 * another owner.
 */
public final class UpdatePolicyOverrides {
    private final UpdatePreferencesStore store;
    private final Consumer<String> warning;
    private volatile State state;

    /** Creates the overrides over {@code store} and reports unusable stored values to nobody. */
    public UpdatePolicyOverrides(@NotNull UpdatePreferencesStore store) {
        this(store, new Consumer<String>() {
            @Override
            public void accept(String message) {
            }
        });
    }

    /** Creates the overrides over {@code store}; unusable stored values go to {@code warning}. */
    public UpdatePolicyOverrides(@NotNull UpdatePreferencesStore store, @NotNull Consumer<String> warning) {
        this.store = store;
        this.warning = warning;
        this.state = load();
    }

    /** The channel chosen for {@code product} at runtime, or null when the default decides. */
    @Nullable
    public UpdateChannel channel(@NotNull String product) {
        return state.channels.get(product);
    }

    /** The Java build chosen for {@code product}, or null when the recommended one is used. */
    @Nullable
    public Integer javaBuild(@NotNull String product) {
        return state.javaBuilds.get(product);
    }

    /** Remembers the Java build {@code java} for {@code product}, or forgets the choice when it is null. */
    public synchronized void setJavaBuild(@NotNull String product, @Nullable Integer java) {
        State old = state;
        state = new State(old.channels, old.automatic, old.allPlugins, with(old.javaBuilds, product, java));
        save();
    }

    /** The automatic-download choice for {@code product} at runtime, or null when the default decides. */
    @Nullable
    public Boolean automatic(@NotNull String product) {
        return state.automatic.get(product);
    }

    /** The automatic-download choice for all plugins together, or null when none was made. */
    @Nullable
    public Boolean allPlugins() {
        return state.allPlugins;
    }

    /** Whether any component was switched to automatic download at runtime. */
    public boolean anyAutomaticEnabled() {
        State current = state;
        return Boolean.TRUE.equals(current.allPlugins) || current.automatic.containsValue(Boolean.TRUE);
    }

    /** Remembers {@code channel} for {@code product}, or forgets the choice when {@code channel} is null. */
    public synchronized void setChannel(@NotNull String product, @Nullable UpdateChannel channel) {
        State old = state;
        state = new State(with(old.channels, product, channel), old.automatic, old.allPlugins, old.javaBuilds);
        save();
    }

    /** Remembers the automatic-download choice for {@code product}. */
    public synchronized void setAutomatic(@NotNull String product, boolean enabled) {
        State old = state;
        state = new State(old.channels, with(old.automatic, product, enabled), old.allPlugins, old.javaBuilds);
        save();
    }

    /**
     * Sets automatic download for every plugin and forgets the choices made for single ones; {@code libraryProduct}
     * keeps its own.
     */
    public synchronized void setAllPlugins(boolean enabled, @NotNull String libraryProduct) {
        State old = state;
        Map<String, Boolean> kept = new LinkedHashMap<String, Boolean>();
        if (old.automatic.containsKey(libraryProduct)) {
            kept.put(libraryProduct, old.automatic.get(libraryProduct));
        }
        state = new State(old.channels, kept, enabled, old.javaBuilds);
        save();
    }

    private static <V> Map<String, V> with(Map<String, V> map, String key, @Nullable V value) {
        Map<String, V> copy = new LinkedHashMap<String, V>(map);
        if (value == null) {
            copy.remove(key);
        } else {
            copy.put(key, value);
        }
        return copy;
    }

    private State load() {
        UpdatePreferencesStore.Preferences stored = store.current();
        Map<String, UpdateChannel> channels = new LinkedHashMap<String, UpdateChannel>();
        for (Map.Entry<String, String> entry : stored.getChannels().entrySet()) {
            final String product = entry.getKey();
            final String name = entry.getValue();
            UpdateChannel channel = Failures.runCatchingNonFatal(() -> UpdateChannel.valueOf(name))
                .onFailure(error -> warning.accept(
                    "Unknown update channel for " + product + " in " + store.getFileName() + "; the default is used"))
                .getOrNull();
            if (channel != null) {
                channels.put(product, channel);
            }
        }
        return new State(channels, stored.getAutomatic(), stored.getAllPlugins(), stored.getJavaBuilds());
    }

    private void save() {
        final State saved = state;
        Map<String, String> names = new LinkedHashMap<String, String>();
        for (Map.Entry<String, UpdateChannel> entry : saved.channels.entrySet()) {
            names.put(entry.getKey(), entry.getValue().name());
        }
        store.update(current -> current.withChannels(names).withAutomatic(saved.automatic)
            .withAllPlugins(saved.allPlugins).withJavaBuilds(saved.javaBuilds));
    }

    private static final class State {
        private final Map<String, UpdateChannel> channels;
        private final Map<String, Boolean> automatic;
        private final Boolean allPlugins;
        private final Map<String, Integer> javaBuilds;

        State(Map<String, UpdateChannel> channels, Map<String, Boolean> automatic, @Nullable Boolean allPlugins,
              Map<String, Integer> javaBuilds) {
            this.channels = Collections.unmodifiableMap(channels);
            this.automatic = Collections.unmodifiableMap(automatic);
            this.allPlugins = allPlugins;
            this.javaBuilds = Collections.unmodifiableMap(javaBuilds);
        }
    }
}
