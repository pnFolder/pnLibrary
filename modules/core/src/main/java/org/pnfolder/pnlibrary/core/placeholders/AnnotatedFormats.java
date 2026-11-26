package org.pnfolder.pnlibrary.core.placeholders;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.placeholders.annotation.Argument;
import org.pnfolder.pnlibrary.api.placeholders.annotation.Format;
import org.pnfolder.pnlibrary.api.placeholders.annotation.Viewer;
import org.pnfolder.pnlibrary.api.placeholders.argument.ArgumentConverter;
import org.pnfolder.pnlibrary.api.placeholders.argument.ArgumentInput;
import org.pnfolder.pnlibrary.api.placeholders.argument.ArgumentValue;
import org.pnfolder.pnlibrary.api.placeholders.info.ViewerInfo;
import org.pnfolder.pnlibrary.api.placeholders.route.RouteInfo;
import org.pnfolder.pnlibrary.api.placeholders.route.RoutePart;

/** Turns the methods marked with {@code @Format} into formats. */
public final class AnnotatedFormats {
    private AnnotatedFormats() {
    }

    /** Registers every {@code @Format} method of {@code holders} for {@code owner}. */
    public static void register(
            @NotNull FormatRegistry registry,
            @NotNull String owner,
            @NotNull List<Object> holders,
            @NotNull Function<Class<?>, ArgumentConverter<Object>> lookup) {
        for (Object holder : holders) {
            List<Method> methods = new ArrayList<Method>();
            for (Method method : holder.getClass().getMethods()) {
                if (method.isAnnotationPresent(Format.class)) {
                    methods.add(method);
                }
            }
            if (methods.isEmpty()) {
                throw new IllegalArgumentException(holder.getClass().getName() + " has no method marked with @Format");
            }
            for (Method method : methods) {
                registry.add(definition(owner, holder, method, lookup));
            }
        }
    }

    private static FormatDef definition(String owner, final Object holder, final Method method, Function<Class<?>, ArgumentConverter<Object>> lookup) {
        String where = holder.getClass().getSimpleName() + "." + method.getName();
        if (method.getParameterCount() < 1) {
            throw new IllegalArgumentException(where + ": a format takes the value as its first parameter");
        }
        Class<?> valueType = ArgumentTypes.boxed(method.getParameterTypes()[0]);
        final List<ArgumentSpec> extras = extras(method, where, lookup);
        String name = method.getAnnotation(Format.class).value();
        return new FormatDef(owner, name, valueType, (value, arguments, viewer) -> invoke(holder, method, extras, value, arguments, viewer));
    }

    /** One spec per parameter after the value; {@code null} stands for the viewer. */
    private static List<ArgumentSpec> extras(Method method, String where, Function<Class<?>, ArgumentConverter<Object>> lookup) {
        List<ArgumentSpec> extras = new ArrayList<ArgumentSpec>();
        Parameter[] parameters = method.getParameters();
        for (int i = 1; i < parameters.length; i++) {
            Parameter parameter = parameters[i];
            if (parameter.getType() == ViewerInfo.class) {
                if (!parameter.isAnnotationPresent(Viewer.class)) {
                    throw new IllegalArgumentException(where + ": a ViewerInfo parameter needs @Viewer");
                }
                extras.add(null);
            } else if (parameter.getType() == ArgumentValue.class) {
                if (!parameter.isAnnotationPresent(Argument.class)) {
                    throw new IllegalArgumentException(where + ": an ArgumentValue parameter needs @Argument");
                }
                Class<?> type = ArgumentTypes.argumentTypeOf(parameter.getParameterizedType(), where);
                extras.add(ArgumentTypes.specFor(type, parameter.getAnnotation(Argument.class), where, lookup));
            } else {
                throw new IllegalStateException(where + ": extra parameters of a format must be ArgumentValue<T> or ViewerInfo");
            }
        }
        return extras;
    }

    private static Object invoke(Object holder, Method method, List<ArgumentSpec> extras, Object value, List<String> arguments, ViewerInfo viewer)
            throws Exception {
        Object[] values = new Object[extras.size() + 1];
        values[0] = value;
        int next = 0;
        for (int i = 0; i < extras.size(); i++) {
            ArgumentSpec spec = extras.get(i);
            if (spec == null) {
                values[i + 1] = viewer;
                continue;
            }
            int position = next;
            String text = next < arguments.size() ? arguments.get(next) : "";
            next++;
            ArgumentInput input = new ArgumentInput(text, viewer, new RouteInfo(Collections.<RoutePart>emptyList()), "", position,
                    Collections.<ArgumentValue<?>>emptyList());
            values[i + 1] = spec.makeNow(input);
        }
        try {
            return method.invoke(holder, values);
        } catch (InvocationTargetException failure) {
            Throwable cause = failure.getTargetException();
            if (cause instanceof Exception) {
                throw (Exception) cause;
            }
            throw Failures.unchecked(cause);
        }
    }
}
