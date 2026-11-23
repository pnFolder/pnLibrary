package org.pnfolder.pnlibrary.core.placeholders

import org.pnfolder.pnlibrary.api.placeholders.Argument
import org.pnfolder.pnlibrary.api.placeholders.ArgumentConverter
import org.pnfolder.pnlibrary.api.placeholders.ArgumentInput
import org.pnfolder.pnlibrary.api.placeholders.ArgumentValue
import org.pnfolder.pnlibrary.api.placeholders.Format
import org.pnfolder.pnlibrary.api.placeholders.RouteInfo
import org.pnfolder.pnlibrary.api.placeholders.Viewer
import org.pnfolder.pnlibrary.api.placeholders.ViewerInfo
import java.lang.reflect.InvocationTargetException

/** Turns the methods marked with `@Format` into formats. */
internal object AnnotatedFormats {
    fun register(registry: FormatRegistry, owner: String, holders: List<Any>, lookup: (Class<*>) -> ArgumentConverter<Any>?) {
        for (holder in holders) {
            val methods = holder.javaClass.methods.filter { it.isAnnotationPresent(Format::class.java) }
            require(methods.isNotEmpty()) { "${holder.javaClass.name} has no method marked with @Format" }
            for (method in methods) {
                val name = method.getAnnotation(Format::class.java).value
                val where = "${holder.javaClass.simpleName}.${method.name}"
                require(method.parameterCount >= 1) { "$where: a format takes the value as its first parameter" }
                val valueType = boxed(method.parameterTypes[0])
                val extras = method.parameters.drop(1).map { parameter ->
                    when (parameter.type) {
                        ViewerInfo::class.java -> {
                            require(parameter.isAnnotationPresent(Viewer::class.java)) { "$where: a ViewerInfo parameter needs @Viewer" }
                            null
                        }
                        ArgumentValue::class.java -> {
                            require(parameter.isAnnotationPresent(Argument::class.java)) { "$where: an ArgumentValue parameter needs @Argument" }
                            specFor(argumentTypeOf(parameter.parameterizedType, where), parameter.getAnnotation(Argument::class.java), where, lookup)
                        }
                        else -> error("$where: extra parameters of a format must be ArgumentValue<T> or ViewerInfo")
                    }
                }
                registry.add(
                    FormatDef(owner, name, valueType) { value, arguments, viewer ->
                        var next = 0
                        val parts = extras.map { spec ->
                            if (spec == null) {
                                viewer
                            } else {
                                val position = next
                                spec.makeNow(ArgumentInput(arguments.getOrNull(next++).orEmpty(), viewer, RouteInfo(emptyList()), "", position, emptyList()))
                            }
                        }
                        try {
                            method.invoke(holder, value, *parts.toTypedArray())
                        } catch (failure: InvocationTargetException) {
                            throw failure.targetException
                        }
                    },
                )
            }
        }
    }
}
