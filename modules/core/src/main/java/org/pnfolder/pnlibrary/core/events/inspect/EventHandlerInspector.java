package org.pnfolder.pnlibrary.core.events.inspect;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.events.Event;
import org.pnfolder.pnlibrary.api.events.listener.EventHandler;
import org.pnfolder.pnlibrary.api.events.listener.Listener;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Discovers and validates annotation-based event handlers.
 *
 * <p>Methods declared by a subclass replace matching inherited methods. Private methods remain distinct because they
 * cannot override a superclass method. Invalid handlers are rejected during registration rather than during the first
 * event dispatch.
 */
public final class EventHandlerInspector {
    private static final Comparator<Handler> ORDER = new Comparator<Handler>() {
        @Override
        public int compare(Handler left, Handler right) {
            int byPriority = Integer.compare(left.getAnnotation().priority(), right.getAnnotation().priority());
            if (byPriority != 0) {
                return byPriority;
            }
            int byName = left.getMethod().getName().compareTo(right.getMethod().getName());
            return byName != 0 ? byName : left.getEventType().getName().compareTo(right.getEventType().getName());
        }
    };

    private EventHandlerInspector() {
    }

    /** Returns all valid handlers in deterministic registration order. */
    @NotNull
    public static List<Handler> inspect(@NotNull Listener listener) {
        Map<String, Method> methods = new LinkedHashMap<String, Method>();
        for (Class<?> type = listener.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            for (Method method : type.getDeclaredMethods()) {
                if (!method.isBridge() && !method.isSynthetic() && !methods.containsKey(methodKey(method))) {
                    methods.put(methodKey(method), method);
                }
            }
        }
        List<Handler> handlers = new ArrayList<Handler>();
        for (Method method : methods.values()) {
            Handler handler = validatedHandler(method);
            if (handler != null) {
                handlers.add(handler);
            }
        }
        Collections.sort(handlers, ORDER);
        return handlers;
    }

    private static Handler validatedHandler(Method method) {
        EventHandler annotation = method.getAnnotation(EventHandler.class);
        if (annotation == null) {
            return null;
        }
        validate(method);
        try {
            method.setAccessible(true);
        } catch (RuntimeException error) {
            throw new IllegalArgumentException("@EventHandler method is not accessible: " + description(method), error);
        }
        return new Handler(method, annotation, method.getParameterTypes()[0].asSubclass(Event.class));
    }

    private static void validate(Method method) {
        require(!Modifier.isStatic(method.getModifiers()), "@EventHandler method must not be static: ", method);
        require(!Modifier.isAbstract(method.getModifiers()), "@EventHandler method must not be abstract: ", method);
        require(method.getParameterTypes().length == 1, "@EventHandler method must have exactly one parameter: ", method);
        require(Event.class.isAssignableFrom(method.getParameterTypes()[0]),
                "@EventHandler parameter must implement Event: ", method);
        require(method.getReturnType() == Void.TYPE, "@EventHandler method must return void: ", method);
    }

    private static void require(boolean condition, String message, Method method) {
        if (!condition) {
            throw new IllegalArgumentException(message + description(method));
        }
    }

    private static String methodKey(Method method) {
        String owner = Modifier.isPrivate(method.getModifiers()) ? method.getDeclaringClass().getName() : "";
        StringBuilder key = new StringBuilder(owner).append('#').append(method.getName()).append('(');
        Class<?>[] parameters = method.getParameterTypes();
        for (int index = 0; index < parameters.length; index++) {
            key.append(index == 0 ? "" : ",").append(parameters[index].getName());
        }
        return key.append(')').toString();
    }

    private static String description(Method method) {
        return method.getDeclaringClass().getName() + "#" + method.getName();
    }

    /** Immutable description of one validated handler method. */
    public static final class Handler {
        private final Method method;
        private final EventHandler annotation;
        private final Class<? extends Event> eventType;

        Handler(Method method, EventHandler annotation, Class<? extends Event> eventType) {
            this.method = method;
            this.annotation = annotation;
            this.eventType = eventType;
        }

        /** The handler method. */
        @NotNull
        public Method getMethod() {
            return method;
        }

        /** The {@link EventHandler} annotation of the method. */
        @NotNull
        public EventHandler getAnnotation() {
            return annotation;
        }

        /** The event type the method takes. */
        @NotNull
        public Class<? extends Event> getEventType() {
            return eventType;
        }

        /** Invokes the handler and exposes the listener's original exception. */
        public void invoke(@NotNull Listener listener, @NotNull Event event) {
            try {
                method.invoke(listener, event);
            } catch (InvocationTargetException error) {
                throw Handler.<RuntimeException>sneaky(error.getTargetException());
            } catch (IllegalAccessException error) {
                throw new IllegalStateException(error);
            }
        }

        // A listener may throw a checked exception; the dispatcher logs whatever it was, so it must arrive unwrapped.
        @SuppressWarnings("unchecked")
        private static <T extends Throwable> T sneaky(Throwable error) throws T {
            throw (T) error;
        }
    }
}
