package org.pnfolder.pnlibrary.core.commands.configs;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.config.ConfigInfo;
import org.pnfolder.pnlibrary.api.config.ConfigPolymorphic;

/** Finds the polymorphic types that registered configuration files use, by looking through the fields of their classes. */
final class PolymorphicTypes {
    private final Set<Class<?>> seen = new HashSet<Class<?>>();
    private final Set<Class<?>> found = new LinkedHashSet<Class<?>>();

    private PolymorphicTypes() {
    }

    @NotNull
    static List<Class<?>> in(@NotNull List<ConfigInfo> configs) {
        PolymorphicTypes search = new PolymorphicTypes();
        for (ConfigInfo info : configs) {
            search.visit(info.getModelType());
        }
        return new ArrayList<Class<?>>(search.found);
    }

    private void visit(Type type) {
        Class<?> raw = rawOf(type);
        if (raw == null || raw.isPrimitive() || raw.getName().startsWith("java.") || !seen.add(raw)) {
            return;
        }
        if (raw.isAnnotationPresent(ConfigPolymorphic.class)) {
            found.add(raw);
        }
        for (Field field : raw.getDeclaredFields()) {
            visit(field.getGenericType());
        }
    }

    private Class<?> rawOf(Type type) {
        if (type instanceof Class) {
            return (Class<?>) type;
        }
        if (type instanceof ParameterizedType) {
            ParameterizedType parameterized = (ParameterizedType) type;
            for (Type argument : parameterized.getActualTypeArguments()) {
                visit(argument);
            }
            return parameterized.getRawType() instanceof Class ? (Class<?>) parameterized.getRawType() : null;
        }
        // Kotlin writes List<Prize> as List<? extends Prize>.
        if (type instanceof WildcardType) {
            for (Type bound : ((WildcardType) type).getUpperBounds()) {
                visit(bound);
            }
        }
        return null;
    }
}
