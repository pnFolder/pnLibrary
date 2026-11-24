package org.pnfolder.pnlibrary.core.placeholders

import org.pnfolder.pnlibrary.api.placeholders.Hide
import org.pnfolder.pnlibrary.api.placeholders.Placeholder
import org.pnfolder.pnlibrary.api.placeholders.Reply
import org.pnfolder.pnlibrary.api.placeholders.UseFormat
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.Type
import java.util.concurrent.CompletionStage

/** One method, Kotlin property or field of a holder class that is marked with `@Placeholder`. */
internal class Member(
    val name: String,
    val ph: Placeholder,
    val returnType: Class<*>,
    val generic: Type,
    val method: Method?,
    val field: Field?,
    val getter: Method?,
    val format: UseFormat?,
) {
    val parameters get() = method?.parameters ?: emptyArray()

    fun read(receiver: Any, args: Array<Any?>): Any? = when {
        method != null -> method.invoke(receiver, *args)
        getter != null -> getter.invoke(receiver)
        else -> field!!.also { it.isAccessible = true }.get(receiver)
    }
}

/** Finds the placeholder members of a class and reads their types. */
internal object Members {
    fun collect(owner: Class<*>): List<Member> {
        val found = mutableListOf<Member>()
        for (method in owner.methods) {
            val ph = method.getAnnotation(Placeholder::class.java) ?: continue
            if (method.isAnnotationPresent(Hide::class.java) || Modifier.isStatic(method.modifiers)) continue
            val name = if (method.parameterCount == 0 && method.name.startsWith("get") && method.name.length > 3) {
                method.name.substring(3)
            } else {
                method.name
            }
            found += Member(name.lowercase(), ph, boxed(method.returnType), method.genericReturnType, method, null, null,
                method.getAnnotation(UseFormat::class.java))
        }
        // A Kotlin property whose annotation is not on the field keeps it on a synthetic `getX$annotations` method.
        var holder: Class<*>? = owner
        while (holder != null && holder != Any::class.java) {
            for (marker in holder.declaredMethods) {
                if (!marker.name.endsWith("\$annotations")) continue
                val ph = marker.getAnnotation(Placeholder::class.java) ?: continue
                if (marker.isAnnotationPresent(Hide::class.java)) continue
                val getterName = marker.name.removeSuffix("\$annotations")
                val getter = owner.methods.firstOrNull { it.name == getterName && it.parameterCount == 0 } ?: continue
                val name = getterName.removePrefix("get").replaceFirstChar { it.lowercase() }
                found += Member(name.lowercase(), ph, boxed(getter.returnType), getter.genericReturnType, getter, null, null,
                    marker.getAnnotation(UseFormat::class.java))
            }
            holder = holder.superclass
        }
        var type: Class<*>? = owner
        while (type != null && type != Any::class.java) {
            for (field in type.declaredFields) {
                val ph = field.getAnnotation(Placeholder::class.java) ?: continue
                if (field.isAnnotationPresent(Hide::class.java) || Modifier.isStatic(field.modifiers)) continue
                val capital = field.name.replaceFirstChar { it.uppercase() }
                val getter = owner.methods.firstOrNull { it.parameterCount == 0 && (it.name == "get$capital" || it.name == "is$capital") }
                if (found.any { it.method == getter && getter != null }) continue
                found += Member(field.name.lowercase(), ph, boxed(field.type), field.genericType, null, field, getter,
                    field.getAnnotation(UseFormat::class.java))
            }
            type = type.superclass
        }
        return found.sortedBy { it.name }
    }

    fun isPlain(type: Class<*>): Boolean =
        type.isEnum || type.isArray || type.isPrimitive || type.name.startsWith("java.") || type.name.startsWith("kotlin.") ||
            type.name.startsWith("net.kyori.")

    /** The class whose placeholders continue the address: the object returned, or the one held by a Reply or a CompletionStage. */
    fun branchTypeOf(member: Member): Class<*>? {
        val type = leafType(member).takeIf { it != Any::class.java } ?: return null
        return type.takeUnless { isPlain(it) }
    }

    /** The type of the value: what is inside `Reply<...>` and `CompletionStage<...>`, otherwise the return type itself. */
    fun leafType(member: Member): Class<*> {
        var generic: Type = member.generic
        repeat(2) {
            val raw = rawOf(generic) ?: return Any::class.java
            if (CompletionStage::class.java.isAssignableFrom(raw) || raw == Reply::class.java) {
                generic = (generic as? java.lang.reflect.ParameterizedType)?.actualTypeArguments?.firstOrNull() ?: return Any::class.java
            }
        }
        return rawOf(generic)?.let(::boxed) ?: Any::class.java
    }

    fun returnsReply(member: Member): Boolean {
        var generic: Type = member.generic
        if (rawOf(generic)?.let { CompletionStage::class.java.isAssignableFrom(it) } == true) {
            generic = (generic as? java.lang.reflect.ParameterizedType)?.actualTypeArguments?.firstOrNull() ?: return false
        }
        return rawOf(generic) == Reply::class.java
    }

    private fun rawOf(type: Type): Class<*>? = when (type) {
        is Class<*> -> type
        is java.lang.reflect.ParameterizedType -> type.rawType as? Class<*>
        is java.lang.reflect.WildcardType -> type.upperBounds.firstOrNull()?.let(::rawOf)
        else -> null
    }
}
