package org.pnfolder.pnlibrary.core.placeholders.address;

import org.pnfolder.pnlibrary.core.placeholders.LimitedConverter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.placeholders.annotation.Argument;
import org.pnfolder.pnlibrary.api.placeholders.argument.ArgumentAdapters;
import org.pnfolder.pnlibrary.api.placeholders.argument.ArgumentConverter;

/** Finds the converter of an argument type and applies the options of {@code @Argument} to it. */
public final class ArgumentTypes {
    private static final String BAD_RANGE = "range must look like 1..100";

    private ArgumentTypes() {
    }

    public static Class<?> boxed(Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        if (type == Integer.TYPE) {
            return Integer.class;
        }
        if (type == Long.TYPE) {
            return Long.class;
        }
        if (type == Double.TYPE) {
            return Double.class;
        }
        if (type == Float.TYPE) {
            return Float.class;
        }
        if (type == Boolean.TYPE) {
            return Boolean.class;
        }
        if (type == Short.TYPE) {
            return Short.class;
        }
        if (type == Byte.TYPE) {
            return Byte.class;
        }
        return type;
    }

    /** The {@code T} of a parameter declared as {@code ArgumentValue<T>}. */
    public static Class<?> argumentTypeOf(Type generic, String where) {
        Type argument = null;
        if (generic instanceof ParameterizedType) {
            Type[] arguments = ((ParameterizedType) generic).getActualTypeArguments();
            argument = arguments.length == 0 ? null : arguments[0];
        }
        if (argument == null) {
            throw new IllegalStateException(where + ": write the type of the argument, like ArgumentValue<Int>");
        }
        Class<?> resolved = null;
        if (argument instanceof Class) {
            resolved = (Class<?>) argument;
        } else if (argument instanceof ParameterizedType) {
            resolved = (Class<?>) ((ParameterizedType) argument).getRawType();
        } else if (argument instanceof WildcardType) {
            Type[] bounds = ((WildcardType) argument).getUpperBounds();
            resolved = bounds.length > 0 && bounds[0] instanceof Class ? (Class<?>) bounds[0] : null;
        }
        if (resolved == null) {
            throw new IllegalStateException(where + ": the argument type cannot be read");
        }
        return boxed(resolved);
    }

    public static ArgumentSpec specFor(Class<?> type, @Nullable Argument options, String where) {
        return specFor(type, options, where, found -> null);
    }

    @SuppressWarnings("unchecked")
    public static ArgumentSpec specFor(
            Class<?> type, @Nullable Argument options, String where, Function<Class<?>, ArgumentConverter<Object>> lookup) {
        Class<? extends ArgumentConverter> custom = options == null ? null : options.converter();
        ArgumentConverter<Object> base;
        if (custom != null && custom != ArgumentConverter.class) {
            base = create(custom, where);
        } else {
            base = lookup.apply(type);
            if (base == null) {
                base = (ArgumentConverter<Object>) ArgumentAdapters.find(type);
            }
            if (base == null) {
                throw new IllegalStateException(where + ": no converter for " + type.getSimpleName()
                        + ". Add one with ArgumentAdapters.register or @Argument(converter = ...)");
            }
        }
        return new ArgumentSpec(options == null ? base : withOptions(base, type, options, where));
    }

    @SuppressWarnings("unchecked")
    private static ArgumentConverter<Object> create(Class<? extends ArgumentConverter> type, String where) {
        Object instance = null;
        try {
            instance = type.getField("INSTANCE").get(null);
        } catch (Exception ignored) {
            // A class without INSTANCE is made by its constructor below.
        }
        if (instance == null) {
            try {
                instance = type.getDeclaredConstructor().newInstance();
            } catch (Exception failure) {
                throw new IllegalStateException(where + ": " + type.getSimpleName() + " needs a constructor without parameters", failure);
            }
        }
        return (ArgumentConverter<Object>) instance;
    }

    private static ArgumentConverter<Object> withOptions(ArgumentConverter<Object> base, Class<?> type, Argument options, String where) {
        ArgumentConverter<Object> result = base;
        if (!options.range().isEmpty()) {
            if (!Number.class.isAssignableFrom(type)) {
                throw new IllegalArgumentException(where + ": range works only with numbers");
            }
            result = ranged(result, options.range(), where);
        }
        if (options.choices().length > 0) {
            if (type != String.class) {
                throw new IllegalArgumentException(where + ": choices work only with text");
            }
            result = chosen(result, Arrays.asList(options.choices()));
        }
        return result;
    }

    private static ArgumentConverter<Object> ranged(ArgumentConverter<Object> inner, String text, String where) {
        String[] parts = text.split(Pattern.quote(".."), -1);
        final Long first = parts.length > 0 ? toLong(parts[0].trim()) : null;
        final Long last = parts.length > 1 ? toLong(parts[1].trim()) : null;
        if (first == null || last == null) {
            throw new IllegalStateException(where + ": " + BAD_RANGE);
        }
        return new LimitedConverter(inner, inner.getLabel() + " " + first + ".." + last, Collections.emptyList(), input -> {
            Object value = inner.read(input);
            if (value == null) {
                return null;
            }
            long number = ((Number) value).longValue();
            return number >= first && number <= last ? value : null;
        });
    }

    private static ArgumentConverter<Object> chosen(ArgumentConverter<Object> inner, final List<String> words) {
        return new LimitedConverter(inner, "one of " + String.join(", ", words), words, input -> {
            for (String word : words) {
                if (word.equalsIgnoreCase(input.getText())) {
                    return word;
                }
            }
            return null;
        });
    }

    private static @Nullable Long toLong(@NotNull String text) {
        try {
            return Long.valueOf(text);
        } catch (NumberFormatException failure) {
            return null;
        }
    }
}
