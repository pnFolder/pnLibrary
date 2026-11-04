package org.pnfolder.pnlibrary.api.placeholders.tree

/**
 * Where branches can be added. A branch gets a word (`int("kills")`) or takes an argument from the path
 * (`player("who")`, `param("place", int)`), and may have a value and further branches of its own.
 *
 * @param T the type of value of the branch the new children hang from
 * @param A the type of the last argument on the path
 */
abstract class BranchScope<T : Any, A> internal constructor(
    internal val children: MutableList<Node>,
    internal val valueType: Class<*>,
) {
    /** Argument type `int`, for `param("place", int)`. */
    val int: ArgType<Int> get() = ArgTypes.int

    /** Argument type `number`. */
    val number: ArgType<Double> get() = ArgTypes.number

    /** Argument type `text`. */
    val text: ArgType<String> get() = ArgTypes.text

    /** Argument type `bool`. */
    val bool: ArgType<Boolean> get() = ArgTypes.bool

    /** A branch with a whole-number value. */
    fun int(name: String, build: NodeScope<Int, A>.() -> Unit = {}) { word(name, Int::class.javaObjectType, build) }

    /** A branch with a decimal value. */
    fun number(name: String, build: NodeScope<Double, A>.() -> Unit = {}) { word(name, Double::class.javaObjectType, build) }

    /** A branch with a text value. */
    fun text(name: String, build: NodeScope<String, A>.() -> Unit = {}) { word(name, String::class.java, build) }

    /** A branch with a yes/no value. */
    fun bool(name: String, build: NodeScope<Boolean, A>.() -> Unit = {}) { word(name, Boolean::class.javaObjectType, build) }

    /** A branch with a value of any other type. */
    fun <R : Any> value(name: String, type: Class<R>, build: NodeScope<R, A>.() -> Unit = {}) { word(name, type, build) }

    /** A path part that is a player (name or UUID); the branches under it see [PlaceholderCall.arg] as a [PlayerArg]. */
    fun player(name: String, build: NodeScope<T, PlayerArg>.() -> Unit) { argument(name, ArgTypes.player, build) }

    /** A path part that is one of the listed words. */
    fun choice(name: String, vararg values: String, build: NodeScope<T, String>.() -> Unit) {
        argument(name, ArgTypes.choice(*values), build)
    }

    /** A path part that is an int in [range]. */
    fun param(name: String, type: ArgType<Int>, range: IntRange, build: NodeScope<T, Int>.() -> Unit) {
        argument(name, if (type === ArgTypes.int) ArgTypes.int(range) else type, build)
    }

    /** A path part of [type]. */
    fun <X : Any> param(name: String, type: ArgType<X>, build: NodeScope<T, X>.() -> Unit) { argument(name, type, build) }

    private fun <R : Any, X> word(name: String, type: Class<R>, build: NodeScope<R, X>.() -> Unit): Node {
        val node = add(Segment.Literal(checkName(name)), type)
        NodeScope<R, X>(node).build()
        return node
    }

    private fun <X : Any> argument(name: String, type: ArgType<X>, build: NodeScope<T, X>.() -> Unit): Node {
        val node = add(Segment.Arg(checkName(name), type), valueType)
        NodeScope<T, X>(node).build()
        return node
    }

    private fun add(segment: Segment, type: Class<*>): Node = Node(segment, type).also { children += it }

    private fun checkName(name: String): String = checkPart(name)
}

private val PART_NAME = Regex("[a-z0-9_-]+")

internal fun checkPart(name: String): String {
    require(PART_NAME.matches(name)) { "Placeholder part '$name' must use lowercase letters, digits, '_' or '-'" }
    return name
}
