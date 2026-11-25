package org.pnfolder.pnlibrary.core.placeholders

import org.pnfolder.pnlibrary.api.placeholders.annotation.Argument
import org.pnfolder.pnlibrary.api.placeholders.argument.ArgumentAdapters
import org.pnfolder.pnlibrary.api.placeholders.argument.ArgumentConverter
import org.pnfolder.pnlibrary.api.placeholders.argument.ArgumentInput
import org.pnfolder.pnlibrary.api.placeholders.argument.ArgumentRejected
import org.pnfolder.pnlibrary.api.placeholders.argument.ArgumentValue
import org.pnfolder.pnlibrary.api.placeholders.route.RouteInfo
import org.pnfolder.pnlibrary.api.placeholders.info.ViewerInfo
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.WildcardType
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

/** What `ArgumentValue<T>` of one parameter needs to turn text into a value; built once when the class is scanned. */
internal class ArgumentSpec(val converter: ArgumentConverter<Any>) {
    private fun finish(input: ArgumentInput, value: Any?, rejected: String?): ArgumentValue<Any> {
        val error = if (value == null) rejected ?: "'${input.text}' is not valid: expected ${converter.label}" else null
        return ArgumentValue(input.text, value, error) { viewer -> converter.meOf(viewer) }
    }

    /** At once, for formats. Always gives an [ArgumentValue]: [ArgumentValue.value] is `null` and [ArgumentValue.error] says why when the text does not fit. */
    fun makeNow(input: ArgumentInput): ArgumentValue<Any> = try {
        finish(input, converter.read(input), null)
    } catch (rejected: ArgumentRejected) {
        finish(input, null, rejected.reason)
    }

    /** The same, through the converter's [ArgumentConverter.readLater]. */
    fun make(input: ArgumentInput): CompletionStage<ArgumentValue<Any>> {
        val stage = try {
            converter.readLater(input)
        } catch (rejected: ArgumentRejected) {
            return CompletableFuture.completedFuture(finish(input, null, rejected.reason))
        }
        return stage.handle { value, failure ->
            val rejected = failure as? ArgumentRejected ?: failure?.cause as? ArgumentRejected
            if (failure != null && rejected == null) throw failure
            finish(input, value, rejected?.reason)
        }
    }

    /** The path part used by the placeholder tree. It never rejects a text, so the method always sees what was written. */
    val pathType: SlotType = SlotType(
        converter.label,
        converter.suggest(ArgumentInput("", ViewerInfo.none(), RouteInfo(emptyList()), "", 0, emptyList())),
    ) { input -> make(input).thenApply { ParsedArgument(input.text, it) } }
}

/** The text of one path part together with its [ArgumentValue], kept in the call until the method needs it. */
internal class ParsedArgument(val text: String, val argument: ArgumentValue<Any>)

internal fun boxed(type: Class<*>): Class<*> = when (type) {
    Integer.TYPE -> Int::class.javaObjectType
    java.lang.Long.TYPE -> Long::class.javaObjectType
    java.lang.Double.TYPE -> Double::class.javaObjectType
    java.lang.Float.TYPE -> Float::class.javaObjectType
    java.lang.Boolean.TYPE -> Boolean::class.javaObjectType
    java.lang.Short.TYPE -> Short::class.javaObjectType
    java.lang.Byte.TYPE -> Byte::class.javaObjectType
    else -> type
}

/** The `T` of a parameter declared as `ArgumentValue<T>`. */
internal fun argumentTypeOf(generic: Type, where: String): Class<*> {
    val argument = (generic as? ParameterizedType)?.actualTypeArguments?.firstOrNull()
        ?: error("$where: write the type of the argument, like ArgumentValue<Int>")
    val resolved = when (argument) {
        is Class<*> -> argument
        is ParameterizedType -> argument.rawType as Class<*>
        is WildcardType -> (argument.upperBounds.firstOrNull() as? Class<*>)
        else -> null
    }
    return boxed(resolved ?: error("$where: the argument type cannot be read"))
}

@Suppress("UNCHECKED_CAST")
internal fun specFor(type: Class<*>, options: Argument?, where: String, lookup: (Class<*>) -> ArgumentConverter<Any>? = { null }): ArgumentSpec {
    val custom = options?.converter?.java?.takeIf { it != ArgumentConverter::class.java }
    val base = if (custom != null) {
        create(custom, where)
    } else {
        lookup(type) ?: ArgumentAdapters.find(type) as ArgumentConverter<Any>?
            ?: error("$where: no converter for ${type.simpleName}. Add one with ArgumentAdapters.register or @Argument(converter = ...)")
    }
    return ArgumentSpec(if (options == null) base else withOptions(base, type, options, where))
}

@Suppress("UNCHECKED_CAST")
private fun create(type: Class<out ArgumentConverter<*>>, where: String): ArgumentConverter<Any> {
    val instance = runCatching { type.getField("INSTANCE").get(null) }.getOrNull()
        ?: runCatching { type.getDeclaredConstructor().newInstance() }.getOrElse {
            error("$where: ${type.simpleName} needs a constructor without parameters")
        }
    return instance as ArgumentConverter<Any>
}

/** A converter built from the options of [Argument]: it reads with [read] and keeps the `me` of [inner]. */
private class LimitedConverter(
    private val inner: ArgumentConverter<Any>,
    override val label: String,
    private val words: List<String>,
    private val reader: (ArgumentInput) -> Any?,
) : ArgumentConverter<Any> {
    override fun read(input: ArgumentInput): Any? = reader(input)

    override fun suggest(input: ArgumentInput): List<String> =
        if (words.isEmpty()) inner.suggest(input) else words.filter { it.startsWith(input.text, ignoreCase = true) }

    override fun meOf(viewer: ViewerInfo): Any? = inner.meOf(viewer)
}

private fun withOptions(base: ArgumentConverter<Any>, type: Class<*>, options: Argument, where: String): ArgumentConverter<Any> {
    var result = base
    if (options.range.isNotEmpty()) {
        require(Number::class.java.isAssignableFrom(type)) { "$where: range works only with numbers" }
        val parts = options.range.split("..")
        val range = (parts.getOrNull(0)?.trim()?.toLongOrNull() ?: error("$where: range must look like 1..100"))..
            (parts.getOrNull(1)?.trim()?.toLongOrNull() ?: error("$where: range must look like 1..100"))
        val inner = result
        result = LimitedConverter(inner, "${base.label} ${range.first}..${range.last}", emptyList()) { input ->
            inner.read(input)?.takeIf { value -> (value as Number).toLong() in range }
        }
    }
    if (options.choices.isNotEmpty()) {
        require(type == String::class.java) { "$where: choices work only with text" }
        val words = options.choices.toList()
        result = LimitedConverter(result, "one of ${words.joinToString(", ")}", words) { input ->
            words.firstOrNull { it.equals(input.text, ignoreCase = true) }
        }
    }
    return result
}
