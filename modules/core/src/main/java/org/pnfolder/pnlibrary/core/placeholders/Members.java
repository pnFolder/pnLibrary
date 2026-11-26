package org.pnfolder.pnlibrary.core.placeholders;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletionStage;
import org.pnfolder.pnlibrary.api.placeholders.annotation.Hide;
import org.pnfolder.pnlibrary.api.placeholders.annotation.Placeholder;
import org.pnfolder.pnlibrary.api.placeholders.annotation.UseFormat;
import org.pnfolder.pnlibrary.api.placeholders.reply.Reply;

/** Finds the placeholder members of a class and reads their types. */
final class Members {
    private static final String ANNOTATIONS_SUFFIX = "$annotations";

    private Members() {
    }

    static List<Member> collect(Class<?> owner) {
        List<Member> found = new ArrayList<Member>();
        collectMethods(owner, found);
        collectKotlinProperties(owner, found);
        collectFields(owner, found);
        Collections.sort(found, new Comparator<Member>() {
            @Override
            public int compare(Member a, Member b) {
                return a.getName().compareTo(b.getName());
            }
        });
        return found;
    }

    private static void collectMethods(Class<?> owner, List<Member> found) {
        for (Method method : owner.getMethods()) {
            Placeholder placeholder = method.getAnnotation(Placeholder.class);
            if (placeholder == null || method.isAnnotationPresent(Hide.class) || Modifier.isStatic(method.getModifiers())) {
                continue;
            }
            String name = method.getName();
            if (method.getParameterCount() == 0 && name.startsWith("get") && name.length() > 3) {
                name = name.substring(3);
            }
            found.add(new Member(name.toLowerCase(Locale.ROOT), placeholder, method.getGenericReturnType(), method, null, null,
                    method.getAnnotation(UseFormat.class)));
        }
    }

    /** A Kotlin property whose annotation is not on the field keeps it on a synthetic {@code getX$annotations} method. */
    private static void collectKotlinProperties(Class<?> owner, List<Member> found) {
        for (Class<?> holder = owner; holder != null && holder != Object.class; holder = holder.getSuperclass()) {
            for (Method marker : holder.getDeclaredMethods()) {
                Placeholder placeholder = marker.getAnnotation(Placeholder.class);
                if (!marker.getName().endsWith(ANNOTATIONS_SUFFIX) || placeholder == null || marker.isAnnotationPresent(Hide.class)) {
                    continue;
                }
                String getterName = marker.getName().substring(0, marker.getName().length() - ANNOTATIONS_SUFFIX.length());
                Method getter = parameterless(owner, getterName);
                if (getter == null) {
                    continue;
                }
                String name = getterName.startsWith("get") ? getterName.substring(3) : getterName;
                name = name.isEmpty() ? name : name.substring(0, 1).toLowerCase(Locale.ROOT) + name.substring(1);
                found.add(new Member(name.toLowerCase(Locale.ROOT), placeholder, getter.getGenericReturnType(), getter, null, null,
                        marker.getAnnotation(UseFormat.class)));
            }
        }
    }

    private static void collectFields(Class<?> owner, List<Member> found) {
        for (Class<?> type = owner; type != null && type != Object.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                Placeholder placeholder = field.getAnnotation(Placeholder.class);
                if (placeholder == null || field.isAnnotationPresent(Hide.class) || Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                String capital = field.getName().substring(0, 1).toUpperCase(Locale.ROOT) + field.getName().substring(1);
                Method getter = parameterless(owner, "get" + capital);
                if (getter == null) {
                    getter = parameterless(owner, "is" + capital);
                }
                if (getter != null && usesMethod(found, getter)) {
                    continue;
                }
                found.add(new Member(field.getName().toLowerCase(Locale.ROOT), placeholder, field.getGenericType(), null, field, getter,
                        field.getAnnotation(UseFormat.class)));
            }
        }
    }

    private static boolean usesMethod(List<Member> found, Method getter) {
        for (Member member : found) {
            if (getter.equals(member.getMethod())) {
                return true;
            }
        }
        return false;
    }

    private static Method parameterless(Class<?> owner, String name) {
        for (Method method : owner.getMethods()) {
            if (method.getParameterCount() == 0 && method.getName().equals(name)) {
                return method;
            }
        }
        return null;
    }

    static boolean isPlain(Class<?> type) {
        String name = type.getName();
        return type.isEnum() || type.isArray() || type.isPrimitive() || name.startsWith("java.") || name.startsWith("kotlin.")
                || name.startsWith("net.kyori.");
    }

    /** The class whose placeholders continue the address: the object returned, or the one held by a Reply or a CompletionStage. */
    static Class<?> branchTypeOf(Member member) {
        Class<?> type = leafType(member);
        if (type == Object.class) {
            return null;
        }
        return isPlain(type) ? null : type;
    }

    /** The type of the value: what is inside {@code Reply<...>} and {@code CompletionStage<...>}, otherwise the return type itself. */
    static Class<?> leafType(Member member) {
        Type generic = member.getGeneric();
        for (int step = 0; step < 2; step++) {
            Class<?> raw = rawOf(generic);
            if (raw == null) {
                return Object.class;
            }
            if (CompletionStage.class.isAssignableFrom(raw) || raw == Reply.class) {
                generic = firstArgument(generic);
                if (generic == null) {
                    return Object.class;
                }
            }
        }
        Class<?> raw = rawOf(generic);
        return raw == null ? Object.class : ArgumentTypes.boxed(raw);
    }

    static boolean returnsReply(Member member) {
        Type generic = member.getGeneric();
        Class<?> raw = rawOf(generic);
        if (raw != null && CompletionStage.class.isAssignableFrom(raw)) {
            generic = firstArgument(generic);
            if (generic == null) {
                return false;
            }
        }
        return rawOf(generic) == Reply.class;
    }

    private static Type firstArgument(Type generic) {
        if (!(generic instanceof ParameterizedType)) {
            return null;
        }
        Type[] arguments = ((ParameterizedType) generic).getActualTypeArguments();
        return arguments.length == 0 ? null : arguments[0];
    }

    private static Class<?> rawOf(Type type) {
        if (type instanceof Class) {
            return (Class<?>) type;
        }
        if (type instanceof ParameterizedType) {
            Type raw = ((ParameterizedType) type).getRawType();
            return raw instanceof Class ? (Class<?>) raw : null;
        }
        if (type instanceof WildcardType) {
            Type[] bounds = ((WildcardType) type).getUpperBounds();
            return bounds.length == 0 ? null : rawOf(bounds[0]);
        }
        return null;
    }
}
