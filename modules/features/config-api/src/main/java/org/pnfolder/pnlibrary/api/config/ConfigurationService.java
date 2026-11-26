package org.pnfolder.pnlibrary.api.config;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Creates lifecycle-bound typed configuration scopes and lets you look up every registered file.
 *
 * <p>The same owner object resolves to the same scope. Owner identity is used rather than equality. A file is counted
 * from the owner's data folder by default and cannot escape its {@link ConfigRoot}. Every registered file has an
 * identifier {@code plugin/module:path}, for example {@code quests/main:quests.yml}.
 */
public interface ConfigurationService {
    /** Returns the existing scope for {@code owner}, or creates one atomically. */
    @NotNull
    ConfigScope scope(@NotNull Object owner);

    /** Every registered file of every plugin, in registration order. */
    @NotNull
    List<ConfigInfo> all();

    /** The files inside {@code address}: a whole plugin, one module or one file. */
    @NotNull
    List<ConfigInfo> find(@NotNull ConfigAddress address);

    /** The files inside the address written as text: {@code quests}, {@code quests/main} or {@code quests/main:quests.yml}. */
    @NotNull
    default List<ConfigInfo> find(@NotNull String address) {
        return find(ConfigAddress.parse(address));
    }

    /** The file with identifier {@code id}, or {@code null} when it is not registered. */
    @Nullable
    ManagedConfig<?> get(@NotNull ConfigId id);

    /** The file with the identifier written as text, or {@code null} when it is not registered. */
    @Nullable
    default ManagedConfig<?> get(@NotNull String id) {
        return get(ConfigId.parse(id));
    }

    /** The file with identifier {@code id}, or an error naming the registered files. */
    @NotNull
    default ManagedConfig<?> require(@NotNull ConfigId id) {
        ManagedConfig<?> config = get(id);
        if (config != null) {
            return config;
        }
        StringBuilder registered = new StringBuilder();
        for (ConfigInfo info : all()) {
            if (registered.length() > 0) {
                registered.append(", ");
            }
            registered.append(info.getId());
        }
        throw new IllegalStateException("Configuration " + id + " is not registered; registered: " + registered);
    }

    /** The file with the identifier written as text, or an error naming the registered files. */
    @NotNull
    default ManagedConfig<?> require(@NotNull String id) {
        return require(ConfigId.parse(id));
    }

    /** The registered files that read into {@code model}. */
    @NotNull
    default List<ConfigInfo> byModel(@NotNull Class<?> model) {
        List<ConfigInfo> found = new ArrayList<ConfigInfo>();
        for (ConfigInfo info : all()) {
            if (info.getModelType().equals(model)) {
                found.add(info);
            }
        }
        return found;
    }

    /** Every word {@code baseType} understands: the ones its annotations declare and the ones plugins added. */
    @NotNull
    List<ConfigTypeInfo> types(@NotNull Class<?> baseType);

    /** The serializers that plugins added, with where each one works. Built-in ones are not listed. */
    @NotNull
    List<ConfigSerializerInfo> serializers();
}
