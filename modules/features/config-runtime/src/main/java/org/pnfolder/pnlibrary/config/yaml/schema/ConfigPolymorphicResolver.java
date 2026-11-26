package org.pnfolder.pnlibrary.config.yaml.schema;

import org.pnfolder.pnlibrary.config.yaml.UnknownConfigTypeException;
import java.util.Objects;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Supplier;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.config.ConfigPolymorphic;
import org.pnfolder.pnlibrary.api.config.ConfigType;
import org.pnfolder.pnlibrary.api.config.ConfigTypes;

/**
 * Resolves annotated and runtime polymorphic configuration implementations.
 *
 * <p>The runtime types passed in are already limited to those that work in this file, so every one of them can be
 * written with its plain word; {@code owner::name} also works and tells two additions with one word apart.
 * The consumer's own and built-in declarations win first, then priority;
 * an equal-priority tie is rejected as ambiguous.
 */
public final class ConfigPolymorphicResolver {
    private final Supplier<List<RuntimeConfigType>> runtimeTypes;
    private final String consumer;
    private final ConfigObjectIntrospector introspector;

    public ConfigPolymorphicResolver(@NotNull Supplier<List<RuntimeConfigType>> runtimeTypes, @NotNull String consumer,
                              @NotNull ConfigObjectIntrospector introspector) {
        this.runtimeTypes = Objects.requireNonNull(runtimeTypes, "runtimeTypes");
        this.consumer = Objects.requireNonNull(consumer, "consumer");
        this.introspector = Objects.requireNonNull(introspector, "introspector");
    }

    /** Whether {@code type} has enough metadata to use polymorphic conversion. */
    public boolean supports(@NotNull Type type, @NotNull List<Annotation> annotations) {
        Class<?> rawType = introspector.rawClass(type);
        if (rawType == Object.class || !rawType.isAnnotationPresent(ConfigPolymorphic.class)) {
            return false;
        }
        if (rawType.isAnnotationPresent(ConfigTypes.class)) {
            return true;
        }
        for (Annotation annotation : annotations) {
            if (annotation instanceof ConfigTypes) {
                return true;
            }
        }
        return false;
    }

    /** The declaration used to write an {@code implementation} of {@code baseType}. */
    @NotNull
    public ResolvedConfigType encodingType(@NotNull Class<?> baseType, @NotNull List<Annotation> annotations,
                                    @NotNull Class<?> implementation) {
        ResolvedConfigType selected = null;
        for (ResolvedConfigType declaration : declarations(baseType, annotations)) {
            if (declaration.getImplementation() == implementation && (selected == null || compare(selected, declaration) < 0)) {
                selected = declaration;
            }
        }
        if (selected == null) {
            throw new IllegalArgumentException(
                    "Configuration implementation " + implementation.getName() + " is not declared by " + baseType.getName());
        }
        return selected;
    }

    /** The implementation class selected by discriminator {@code value} found at {@code path}; fails when unknown or ambiguous. */
    @NotNull
    public Class<?> decodingType(@NotNull Class<?> baseType, @NotNull List<Annotation> annotations, @NotNull String value,
                          @NotNull String path) {
        List<ResolvedConfigType> available = declarations(baseType, annotations);
        List<ResolvedConfigType> matches = new ArrayList<>();
        ResolvedConfigType selected = null;
        for (ResolvedConfigType declaration : available) {
            if (declaration.matches(value)) {
                matches.add(declaration);
                if (selected == null || compare(selected, declaration) < 0) {
                    selected = declaration;
                }
            }
        }
        if (selected == null) {
            StringBuilder allowed = new StringBuilder();
            for (ResolvedConfigType declaration : available) {
                if (allowed.length() > 0) {
                    allowed.append(", ");
                }
                allowed.append(declaration.getName());
            }
            throw new UnknownConfigTypeException("Unknown " + baseType.getSimpleName() + " type '" + value + "' at " + path
                    + "; allowed: " + allowed);
        }
        int equallyPreferred = 0;
        for (ResolvedConfigType match : matches) {
            if (match.getPriority() == selected.getPriority() && isConsumerOwned(match) == isConsumerOwned(selected)) {
                equallyPreferred++;
            }
        }
        if (equallyPreferred != 1) {
            throw new IllegalArgumentException(
                    "Ambiguous " + baseType.getSimpleName() + " type '" + value + "' at " + path + "; assign different priorities");
        }
        return selected.getImplementation();
    }

    private List<ResolvedConfigType> declarations(Class<?> baseType, List<Annotation> annotations) {
        List<ResolvedConfigType> result = new ArrayList<>();
        ConfigTypes declared = baseType.getAnnotation(ConfigTypes.class);
        if (declared != null) {
            addDeclared(result, declared);
        }
        for (Annotation annotation : annotations) {
            if (annotation instanceof ConfigTypes) {
                addDeclared(result, (ConfigTypes) annotation);
            }
        }
        for (RuntimeConfigType type : runtimeTypes.get()) {
            if (type.getBaseType() == baseType) {
                result.add(new ResolvedConfigType(type.getOwner(), type.getImplementation(), type.getName(),
                        type.getAliases(), type.getPriority()));
            }
        }
        if (result.isEmpty()) {
            throw new IllegalArgumentException("Polymorphic configuration type " + baseType.getName() + " requires @ConfigTypes");
        }
        for (ResolvedConfigType type : result) {
            if (!baseType.isAssignableFrom(type.getImplementation())) {
                throw new IllegalArgumentException("Every @ConfigTypes entry on " + baseType.getName() + " must implement that type");
            }
        }
        return result;
    }

    private static void addDeclared(List<ResolvedConfigType> result, ConfigTypes declared) {
        for (ConfigType type : declared.value()) {
            result.add(new ResolvedConfigType(null, type.type(), type.name(),
                    new LinkedHashSet<>(Arrays.asList(type.aliases())), type.priority()));
        }
    }

    private int compare(ResolvedConfigType first, ResolvedConfigType second) {
        int owned = Boolean.compare(isConsumerOwned(first), isConsumerOwned(second));
        return owned != 0 ? owned : Integer.compare(first.getPriority(), second.getPriority());
    }

    private boolean isConsumerOwned(ResolvedConfigType type) {
        return type.getOwner() != null && type.getOwner().equalsIgnoreCase(consumer);
    }
}
