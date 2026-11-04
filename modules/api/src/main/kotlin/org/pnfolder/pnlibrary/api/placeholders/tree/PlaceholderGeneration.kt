package org.pnfolder.pnlibrary.api.placeholders.tree

import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.concurrent.CompletableFuture
import kotlin.reflect.KProperty1

/**
 * The branches made from the properties of a class. Shape them with [only], [exclude] and [rename]
 * right after `from`; the placeholders are registered when the block ends.
 */
class Generated<R : Any> internal constructor(
    private val properties: MutableMap<String, Node>,
    private val holder: MutableList<Node>,
) {
    /** Keeps only these properties. */
    fun only(vararg keep: KProperty1<R, *>): Generated<R> = apply {
        val names = keep.map { it.name.lowercase() }.toSet()
        properties.keys.filter { it !in names }.forEach(::drop)
    }

    /** Leaves these properties out. */
    fun exclude(vararg leave: KProperty1<R, *>): Generated<R> = apply { leave.forEach { drop(it.name.lowercase()) } }

    /** Gives a property another word in the path. */
    fun rename(property: KProperty1<R, *>, word: String): Generated<R> = apply {
        val node = properties.remove(property.name.lowercase()) ?: return@apply
        node.segment = Segment.Literal(checkPart(word))
        properties[word] = node
    }

    private fun drop(name: String) {
        properties.remove(name)?.let(holder::remove)
    }
}

/** The properties of [type] that can be read: public getters without parameters. */
internal fun readableProperties(type: Class<*>): Map<String, Method> = type.methods
    .filter { Modifier.isPublic(it.modifiers) && !Modifier.isStatic(it.modifiers) && it.parameterCount == 0 }
    .mapNotNull { method -> propertyName(method)?.let { it to method } }
    .toMap()

private fun propertyName(method: Method): String? {
    val name = method.name
    return when {
        method.declaringClass == Any::class.java || name == "getClass" || method.returnType == Void.TYPE -> null
        name.startsWith("get") && name.length > 3 -> name.substring(3).replaceFirstChar { it.lowercase() }
        name.startsWith("is") && name.length > 2 && method.returnType == java.lang.Boolean.TYPE ->
            name.substring(2).replaceFirstChar { it.lowercase() }
        else -> null
    }
}

private fun boxed(type: Class<*>): Class<*> = when (type) {
    Integer.TYPE -> Int::class.javaObjectType
    java.lang.Long.TYPE -> Long::class.javaObjectType
    java.lang.Double.TYPE -> Double::class.javaObjectType
    java.lang.Float.TYPE -> Float::class.javaObjectType
    java.lang.Boolean.TYPE -> Boolean::class.javaObjectType
    java.lang.Short.TYPE -> Short::class.javaObjectType
    java.lang.Byte.TYPE -> Byte::class.javaObjectType
    else -> type
}

@PublishedApi
@Suppress("UNCHECKED_CAST")
internal fun <R : Any, A> BranchScope<*, A>.generateFrom(
    word: String,
    type: Class<R>,
    by: String?,
    source: (PlaceholderCall<Any?>) -> Any?,
): Generated<R> {
    val root = Node(Segment.Literal(checkPart(word)), type).also { children += it }
    val holder = by?.let { Node(Segment.Arg(checkPart(it), ArgTypes.player), type).also { node -> root.children += node } } ?: root
    val nodes = linkedMapOf<String, Node>()
    readableProperties(type).toSortedMap().forEach { (name, method) ->
        val node = Node(Segment.Literal(checkPart(name.lowercase())), boxed(method.returnType))
        node.value = { call -> CompletableFuture.completedFuture(source(call)?.let(method::invoke)) }
        holder.children += node
        nodes[name.lowercase()] = node
    }
    return Generated(nodes, holder.children)
}

/**
 * Makes a branch [word] whose sub-branches are the properties of [R]: `from<Clan>("clan") { ... }` gives
 * `clan.name`, `clan.tag` and so on. [source] returns the object, or `null` when there is none.
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified R : Any, A> BranchScope<*, A>.from(
    word: String,
    noinline source: PlaceholderCall<A>.() -> R?,
): Generated<R> = generateFrom(word, R::class.java, null) { call -> (call as PlaceholderCall<A>).source() }

/** Like [from], with a player taken from the path first: `clan.<player>.name`. [by] names that path part. */
@Suppress("UNCHECKED_CAST")
inline fun <reified R : Any, A> BranchScope<*, A>.from(
    word: String,
    by: String,
    noinline source: PlaceholderCall<PlayerArg>.() -> R?,
): Generated<R> = generateFrom(word, R::class.java, by) { call ->
    (call as PlaceholderCall<PlayerArg>).source()
}

/** One branch [word] per key of [keys]: `price.sword`, `price.apple`. A key that is not listed gives no value. */
inline fun <reified R : Any, A> BranchScope<*, A>.table(
    word: String,
    noinline keys: () -> Collection<String>,
    noinline value: PlaceholderCall<String>.(String) -> R?,
) = tableOf(word, R::class.javaObjectType, keys, value)

/** One branch [word] per place from 1 to [size]: `top.1`, `top.2`. */
inline fun <reified R : Any, A> BranchScope<*, A>.list(
    word: String,
    noinline size: () -> Int,
    noinline value: PlaceholderCall<Int>.(Int) -> R?,
) = placesOf(word, R::class.javaObjectType, size, value)

@PublishedApi
@Suppress("UNCHECKED_CAST")
internal fun <A> BranchScope<*, A>.tableOf(
    word: String,
    type: Class<*>,
    keys: () -> Collection<String>,
    value: PlaceholderCall<String>.(String) -> Any?,
) {
    val root = Node(Segment.Literal(checkPart(word)), type).also { children += it }
    val leaf = Node(Segment.Arg("key", ArgTypes.text), type).also { root.children += it }
    leaf.value = { call ->
        val key = call.arg as String
        CompletableFuture.completedFuture(if (key in keys()) (call as PlaceholderCall<String>).value(key) else null)
    }
}

@PublishedApi
@Suppress("UNCHECKED_CAST")
internal fun <A> BranchScope<*, A>.placesOf(
    word: String,
    type: Class<*>,
    size: () -> Int,
    value: PlaceholderCall<Int>.(Int) -> Any?,
) {
    val places = object : ArgType<Int>("int") {
        override fun parse(text: String) = text.toIntOrNull()?.takeIf { it in 1..size() }
    }
    val root = Node(Segment.Literal(checkPart(word)), type).also { children += it }
    val leaf = Node(Segment.Arg("place", places), type).also { root.children += it }
    leaf.value = { call -> CompletableFuture.completedFuture((call as PlaceholderCall<Int>).value(call.arg as Int)) }
}
