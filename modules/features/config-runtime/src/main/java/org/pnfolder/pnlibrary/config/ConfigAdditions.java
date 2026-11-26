package org.pnfolder.pnlibrary.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;
import java.util.regex.Pattern;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.config.ConfigAddress;
import org.pnfolder.pnlibrary.api.config.ConfigId;
import org.pnfolder.pnlibrary.api.config.ConfigSerializer;
import org.pnfolder.pnlibrary.api.config.ConfigSerializerInfo;
import org.pnfolder.pnlibrary.api.config.ConfigSerializerRegistration;
import org.pnfolder.pnlibrary.api.config.ConfigType;
import org.pnfolder.pnlibrary.api.config.ConfigTypeConflict;
import org.pnfolder.pnlibrary.api.config.ConfigTypeInfo;
import org.pnfolder.pnlibrary.api.config.ConfigTypeRegistration;
import org.pnfolder.pnlibrary.api.config.ConfigTypes;
import org.pnfolder.pnlibrary.api.plugin.ModuleId;
import org.pnfolder.pnlibrary.api.plugin.PluginId;
import org.pnfolder.pnlibrary.config.yaml.RuntimeConfigType;

/**
 * The polymorphic types and serializers that plugins publish for the configuration files of others. Each addition
 * belongs to the scope that published it, works in the files its address covers, and goes away with its scope.
 */
final class ConfigAdditions {
    private static final List<String> FORBIDDEN_PACKAGES = Arrays.asList("java.", "javax.", "kotlin.", "jdk.");
    private static final Pattern TYPE_NAME = Pattern.compile("[a-z0-9][a-z0-9_.-]{0,63}");

    private final Set<PublishedConfigType> runtimeTypes = new LinkedHashSet<PublishedConfigType>();
    private final Set<PublishedConfigSerializer> runtimeSerializers = new LinkedHashSet<PublishedConfigSerializer>();
    private final Consumer<ConfigAddress> reloadWaiting;

    /** @param reloadWaiting called with the address of a new type word, so files that waited for it are read again */
    ConfigAdditions(@NotNull Consumer<ConfigAddress> reloadWaiting) {
        this.reloadWaiting = reloadWaiting;
    }

    @NotNull
    List<ConfigTypeInfo> types(@NotNull Class<?> baseType) {
        List<ConfigTypeInfo> result = new ArrayList<ConfigTypeInfo>();
        ConfigTypes declared = baseType.getAnnotation(ConfigTypes.class);
        if (declared != null) {
            for (ConfigType type : declared.value()) {
                result.add(new ConfigTypeInfo(baseType, type.type(), type.name(),
                        new LinkedHashSet<String>(Arrays.asList(type.aliases())), null, type.priority(), null));
            }
        }
        synchronized (runtimeTypes) {
            for (PublishedConfigType added : runtimeTypes) {
                if (added.isActive() && added.getBaseType().equals(baseType)) {
                    result.add(new ConfigTypeInfo(baseType, added.getImplementation(), added.getName(), added.aliases(),
                            added.getOwner(), added.priority(), added.getTarget()));
                }
            }
        }
        return result;
    }

    @NotNull
    List<ConfigSerializerInfo> serializers() {
        List<ConfigSerializerInfo> result = new ArrayList<ConfigSerializerInfo>();
        synchronized (runtimeSerializers) {
            for (PublishedConfigSerializer added : runtimeSerializers) {
                if (added.isActive()) {
                    result.add(new ConfigSerializerInfo(added.getType(), added.getOwner(), added.getPriority(), added.getTarget()));
                }
            }
        }
        return result;
    }

    /** The serializers that file {@code consumer} uses: those whose address covers it, one per type, the highest priority first. */
    @NotNull
    Map<Class<?>, ConfigSerializer<?>> visibleSerializers(@NotNull ConfigId consumer) {
        Map<Class<?>, ConfigSerializer<?>> found = new LinkedHashMap<Class<?>, ConfigSerializer<?>>();
        synchronized (runtimeSerializers) {
            List<PublishedConfigSerializer> covering = new ArrayList<PublishedConfigSerializer>();
            for (PublishedConfigSerializer added : runtimeSerializers) {
                if (added.isActive() && added.getTarget().matches(consumer)) {
                    covering.add(added);
                }
            }
            Collections.sort(covering, new Comparator<PublishedConfigSerializer>() {
                @Override
                public int compare(PublishedConfigSerializer a, PublishedConfigSerializer b) {
                    return Integer.compare(b.getPriority(), a.getPriority());
                }
            });
            for (PublishedConfigSerializer added : covering) {
                if (!found.containsKey(added.getType())) {
                    found.put(added.getType(), added.serializer());
                }
            }
        }
        return found;
    }

    /** The words visible in file {@code consumer}: those whose address covers it and whose plugin its module accepts. */
    @NotNull
    List<RuntimeConfigType> visibleTypes(@NotNull ConfigId consumer) {
        List<RuntimeConfigType> result = new ArrayList<RuntimeConfigType>();
        synchronized (runtimeTypes) {
            List<PublishedConfigType> covering = new ArrayList<PublishedConfigType>();
            for (PublishedConfigType added : runtimeTypes) {
                if (added.isActive() && added.getTarget().matches(consumer)) {
                    covering.add(added);
                }
            }
            for (PublishedConfigType added : covering) {
                // The plugin that owns the file keeps its own words: an addition made for everybody cannot shadow them.
                if (added.getOwner().equals(consumer.getPlugin()) || !shadowedByOwn(covering, added, consumer)) {
                    result.add(new RuntimeConfigType(added.getOwner().getValue(), added.getBaseType(), added.getImplementation(),
                            added.getName(), added.aliases(), added.priority()));
                }
            }
        }
        return result;
    }

    private static boolean shadowedByOwn(List<PublishedConfigType> covering, PublishedConfigType added, ConfigId consumer) {
        for (PublishedConfigType own : covering) {
            if (own.getOwner().equals(consumer.getPlugin()) && own.getBaseType().equals(added.getBaseType())
                    && sharesWord(own, added)) {
                return true;
            }
        }
        return false;
    }

    private static boolean sharesWord(PublishedConfigType first, PublishedConfigType second) {
        Set<String> others = second.words();
        for (String word : first.words()) {
            if (others.contains(word)) {
                return true;
            }
        }
        return false;
    }

    @NotNull
    <T> ConfigSerializerRegistration publishSerializer(@NotNull PluginId scope, @NotNull PluginId plugin, @NotNull ModuleId module,
                                                       @NotNull Class<T> type, @NotNull ConfigSerializer<T> serializer,
                                                       int priority, @Nullable ConfigAddress target,
                                                       @NotNull BooleanSupplier isOpen) {
        requireOpen(isOpen);
        ConfigAddress where = target != null ? target : ConfigAddress.module(plugin, module);
        boolean foreign = where.isAll() || !where.getPlugin().equals(plugin);
        if (foreign && (type.isPrimitive() || isForbidden(type))) {
            throw new IllegalArgumentException("A serializer for " + type.getName()
                    + " cannot be added to other plugins' files: it would change how ordinary values are read");
        }
        PublishedConfigSerializer registration =
                new PublishedConfigSerializer(runtimeSerializers, scope, plugin, type, serializer, priority, where);
        synchronized (runtimeSerializers) {
            requireOpen(isOpen);
            for (PublishedConfigSerializer other : runtimeSerializers) {
                if (other.isActive() && other.getOwner().equals(plugin) && other.getType().equals(type)
                        && other.getTarget().overlaps(where)) {
                    throw new IllegalStateException("Plugin " + plugin + " already published a serializer for "
                            + type.getName() + " that works at " + other.getTarget());
                }
            }
            runtimeSerializers.add(registration);
        }
        return registration;
    }

    @NotNull
    <T> ConfigTypeRegistration publishType(@NotNull PluginId scope, @NotNull PluginId plugin, @NotNull ModuleId module,
                                           @NotNull Class<T> baseType, @NotNull Class<? extends T> implementation,
                                           @NotNull String name, @NotNull Set<String> aliases, int priority,
                                           @Nullable ConfigAddress target, @NotNull ConfigTypeConflict conflict,
                                           @NotNull BooleanSupplier isOpen) {
        requireOpen(isOpen);
        ConfigAddress where = target != null ? target : ConfigAddress.module(plugin, module);
        String normalizedName = normalizeTypeName(name);
        Set<String> normalizedAliases = new LinkedHashSet<String>();
        for (String alias : aliases) {
            normalizedAliases.add(normalizeTypeName(alias));
        }
        if (normalizedAliases.contains(normalizedName)) {
            throw new IllegalArgumentException("Configuration type aliases must not repeat '" + normalizedName + "'");
        }
        if (!baseType.isAssignableFrom(implementation)) {
            throw new IllegalArgumentException(implementation.getName() + " does not implement " + baseType.getName());
        }
        Set<String> words = new LinkedHashSet<String>(normalizedAliases);
        words.add(normalizedName);
        rejectDeclaredWords(baseType, words);
        PublishedConfigType registration = new PublishedConfigType(runtimeTypes, scope, plugin, baseType, implementation,
                normalizedName, normalizedAliases, priority, where);
        synchronized (runtimeTypes) {
            requireOpen(isOpen);
            List<PublishedConfigType> clashing = new ArrayList<PublishedConfigType>();
            for (PublishedConfigType other : runtimeTypes) {
                if (other.isActive() && other.getBaseType().equals(baseType) && other.getTarget().overlaps(where)
                        && firstShared(other, words) != null) {
                    clashing.add(other);
                }
            }
            if (!clashing.isEmpty() && conflict == ConfigTypeConflict.KEEP) {
                registration.deactivate();
                return registration;
            }
            checkClashes(clashing, conflict, plugin, priority, where, baseType, normalizedName, words);
            // Another plugin's word with the same or a higher priority stays; priority decides which one wins.
            if (conflict == ConfigTypeConflict.REPLACE) {
                for (PublishedConfigType other : clashing) {
                    if (other.getOwner().equals(plugin) || other.priority() < priority) {
                        other.close();
                    }
                }
            }
            runtimeTypes.add(registration);
        }
        reloadWaiting.accept(where);
        return registration;
    }

    private static void checkClashes(List<PublishedConfigType> clashing, ConfigTypeConflict conflict, PluginId plugin,
                                     int priority, ConfigAddress where, Class<?> baseType, String normalizedName,
                                     Set<String> words) {
        for (PublishedConfigType other : clashing) {
            boolean replaceable = conflict == ConfigTypeConflict.REPLACE
                    && (other.getOwner().equals(plugin) || other.priority() < priority);
            if (!replaceable && other.getOwner().equals(plugin)) {
                throw new IllegalArgumentException("Plugin " + plugin + " already published configuration type '"
                        + normalizedName + "' for " + baseType.getName());
            }
            // The plugin that owns the file keeps its own words; an addition can only add new ones.
            if (!where.isAll() && other.getOwner().equals(where.getPlugin()) && !replaceable) {
                throw new IllegalArgumentException("Configuration type word '" + firstShared(other, words) + "' of "
                        + baseType.getSimpleName() + " belongs to plugin " + where.getPlugin());
            }
        }
    }

    private static String firstShared(PublishedConfigType other, Set<String> words) {
        for (String word : other.words()) {
            if (words.contains(word)) {
                return word;
            }
        }
        return null;
    }

    // The base type's own words are not for sale: an addition must not change what an existing file means.
    private static void rejectDeclaredWords(Class<?> baseType, Set<String> words) {
        ConfigTypes declared = baseType.getAnnotation(ConfigTypes.class);
        if (declared == null) {
            return;
        }
        for (ConfigType type : declared.value()) {
            List<String> taken = new ArrayList<String>();
            for (String alias : type.aliases()) {
                taken.add(alias.toLowerCase(Locale.ROOT));
            }
            taken.add(type.name().toLowerCase(Locale.ROOT));
            for (String word : taken) {
                if (words.contains(word)) {
                    throw new IllegalArgumentException("Configuration type word '" + word + "' is declared by "
                            + baseType.getSimpleName() + " itself and cannot be taken");
                }
            }
        }
    }

    private static boolean isForbidden(Class<?> type) {
        for (String prefix : FORBIDDEN_PACKAGES) {
            if (type.getName().startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static void requireOpen(BooleanSupplier isOpen) {
        if (!isOpen.getAsBoolean()) {
            throw new IllegalStateException("Configuration scope is closed");
        }
    }

    private static String normalizeTypeName(String value) {
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (!TYPE_NAME.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Configuration type name '" + value
                    + "' must contain only letters, digits, dots, underscores, or hyphens");
        }
        return normalized;
    }

    /** Takes back everything published through the scope {@code scope}. */
    void release(@NotNull PluginId scope) {
        synchronized (runtimeTypes) {
            List<PublishedConfigType> published = new ArrayList<PublishedConfigType>();
            for (PublishedConfigType type : runtimeTypes) {
                if (type.scopeKey().equals(scope)) {
                    published.add(type);
                }
            }
            for (PublishedConfigType type : published) {
                type.deactivate();
            }
            runtimeTypes.removeAll(published);
        }
        synchronized (runtimeSerializers) {
            List<PublishedConfigSerializer> published = new ArrayList<PublishedConfigSerializer>();
            for (PublishedConfigSerializer serializer : runtimeSerializers) {
                if (serializer.scopeKey().equals(scope)) {
                    published.add(serializer);
                }
            }
            for (PublishedConfigSerializer serializer : published) {
                serializer.deactivate();
            }
            runtimeSerializers.removeAll(published);
        }
    }
}
