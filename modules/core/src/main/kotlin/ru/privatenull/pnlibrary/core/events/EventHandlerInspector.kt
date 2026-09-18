package ru.privatenull.pnlibrary.core.events

import ru.privatenull.pnlibrary.api.events.Event
import ru.privatenull.pnlibrary.api.events.EventHandler
import ru.privatenull.pnlibrary.api.events.Listener
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * Discovers and validates annotation-based event handlers.
 *
 * Methods declared by a subclass replace matching inherited methods. Private
 * methods remain distinct because they cannot override a superclass method.
 * Invalid handlers are rejected during registration rather than during the
 * first event dispatch.
 */
internal object EventHandlerInspector {
    /** Immutable description of one validated handler method. */
    data class Handler(
        val method: Method,
        val annotation: EventHandler,
        val eventType: Class<out Event>,
    ) {
        /** Invokes the handler and exposes the listener's original exception. */
        fun invoke(listener: Listener, event: Event) {
            try {
                method.invoke(listener, event)
            } catch (error: InvocationTargetException) {
                throw error.targetException
            }
        }
    }

    /** Returns all valid handlers in deterministic registration order. */
    fun inspect(listener: Listener): List<Handler> {
        val methods = linkedMapOf<String, Method>()
        var type: Class<*>? = listener.javaClass
        while (type != null && type != Any::class.java) {
            type.declaredMethods
                .asSequence()
                .filterNot { it.isBridge || it.isSynthetic }
                .forEach { method -> methods.putIfAbsent(methodKey(method), method) }
            type = type.superclass
        }

        return methods.values.mapNotNull(::validatedHandler).sortedWith(
            compareBy<Handler> { it.annotation.priority }
                .thenBy { it.method.name }
                .thenBy { it.eventType.name },
        )
    }

    private fun validatedHandler(method: Method): Handler? {
        val annotation = method.getAnnotation(EventHandler::class.java) ?: return null
        require(!Modifier.isStatic(method.modifiers)) {
            "@EventHandler method must not be static: ${description(method)}"
        }
        require(!Modifier.isAbstract(method.modifiers)) {
            "@EventHandler method must not be abstract: ${description(method)}"
        }
        require(method.parameterCount == 1) {
            "@EventHandler method must have exactly one parameter: ${description(method)}"
        }
        require(Event::class.java.isAssignableFrom(method.parameterTypes[0])) {
            "@EventHandler parameter must implement Event: ${description(method)}"
        }
        require(method.returnType == Void.TYPE) {
            "@EventHandler method must return Unit or void: ${description(method)}"
        }

        require(method.trySetAccessible()) {
            "@EventHandler method is not accessible: ${description(method)}"
        }
        return Handler(method, annotation, method.parameterTypes[0].asSubclass(Event::class.java))
    }

    private fun methodKey(method: Method): String {
        val owner = if (Modifier.isPrivate(method.modifiers)) method.declaringClass.name else ""
        return "$owner#${method.name}(${method.parameterTypes.joinToString(",") { it.name }})"
    }

    private fun description(method: Method): String =
        "${method.declaringClass.name}#${method.name}"
}
