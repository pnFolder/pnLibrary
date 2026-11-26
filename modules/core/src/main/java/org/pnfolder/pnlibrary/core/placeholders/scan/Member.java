package org.pnfolder.pnlibrary.core.placeholders.scan;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import org.pnfolder.pnlibrary.api.placeholders.annotation.Placeholder;
import org.pnfolder.pnlibrary.api.placeholders.annotation.UseFormat;

/** One method, Kotlin property or field of a holder class that is marked with {@code @Placeholder}. */
final class Member {
    private final String name;
    private final Placeholder placeholder;
    private final Type generic;
    private final Method method;
    private final Field field;
    private final Method getter;
    private final UseFormat format;

    Member(String name, Placeholder placeholder, Type generic, Method method, Field field, Method getter, UseFormat format) {
        this.name = name;
        this.placeholder = placeholder;
        this.generic = generic;
        this.method = method;
        this.field = field;
        this.getter = getter;
        this.format = format;
    }

    String getName() {
        return name;
    }

    Placeholder getPlaceholder() {
        return placeholder;
    }

    Type getGeneric() {
        return generic;
    }

    Method getMethod() {
        return method;
    }

    UseFormat getFormat() {
        return format;
    }

    Parameter[] getParameters() {
        return method == null ? new Parameter[0] : method.getParameters();
    }

    Object read(Object receiver, Object[] args) throws ReflectiveOperationException {
        if (method != null) {
            return method.invoke(receiver, args);
        }
        if (getter != null) {
            return getter.invoke(receiver);
        }
        field.setAccessible(true);
        return field.get(receiver);
    }
}
