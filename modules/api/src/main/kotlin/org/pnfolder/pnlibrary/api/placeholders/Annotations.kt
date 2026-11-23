package org.pnfolder.pnlibrary.api.placeholders

/**
 * Marks a class whose members are placeholders of one group: `@Placeholders("clan")` makes `{clan.level}`.
 * Pass an instance to `module.placeholders(instance)`.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class Placeholders(val value: String, val visibility: Visibility = Visibility.ALL)

/** What a placeholder shows when its method has no value (returns `null`). */
enum class Invalid {
    /** Leave the address in the text as it is. This is the default. */
    KEEP,

    /** Show [Placeholder.default]. Only for text placeholders. */
    DEFAULT,

    /** Show nothing. Only for text placeholders. */
    EMPTY,
}

/**
 * Makes a method or a property a placeholder, named after it in lower case. It returns a [Reply]. A method whose reply holds a plain value
 * is a leaf; a method that returns an object becomes a branch whose own `@Placeholder` members continue the address.
 *
 * @property value description shown in `/pn placeholders`
 * @property onInvalid what to show when there is no value
 * @property default the text for [Invalid.DEFAULT]
 *
 * On a branch, an [onInvalid] other than [Invalid.KEEP] covers every text placeholder below it that sets none of its own.
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY, AnnotationTarget.FIELD, AnnotationTarget.PROPERTY_GETTER)
@Retention(AnnotationRetention.RUNTIME)
annotation class Placeholder(
    val value: String = "",
    val onInvalid: Invalid = Invalid.KEEP,
    val default: String = "",
    val visibility: Visibility = Visibility.ALL,
)

/** Keeps a property or method out of the placeholders even when its class is scanned. */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY, AnnotationTarget.FIELD, AnnotationTarget.PROPERTY_GETTER)
@Retention(AnnotationRetention.RUNTIME)
annotation class Hide

/**
 * Marks a parameter of type [ArgumentValue]: the part of the address it reads. Every such parameter needs it; the options are all optional.
 *
 * @property range allowed numbers for an integer argument, such as `"1..100"`
 * @property choices allowed words for a text argument
 * @property name the name of the path part, for lists and error messages
 * @property converter the class that reads the text, for a type that has no default or needs another reading; see [ArgumentConverter]
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class Argument(
    val range: String = "",
    val choices: Array<String> = [],
    val name: String = "",
    val converter: kotlin.reflect.KClass<out ArgumentConverter<*>> = ArgumentConverter::class,
)

/**
 * Marks a parameter whose class gathers several arguments: `fun kills(@Group query: KillsQuery)`. The class needs
 * no annotation; it has one public constructor and every parameter of it is an [ArgumentValue] marked with [Argument].
 * The arguments stand in the address one after another, in the order of the constructor.
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class Group

/**
 * Creates a format named [value]: `{clan.level|rank}`. The first parameter of the method is the value, the
 * following [ArgumentValue] parameters read what comes after the colon (`|pad0:4`). Pass the holder to `module.formats(holder)`.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Format(val value: String)

/**
 * Applies the format [value] when the text names none. On a method it covers that placeholder, on a class every
 * placeholder under it whose value is of [type] (any type when it is left out).
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY, AnnotationTarget.FIELD, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class UseFormat(val value: String, val type: kotlin.reflect.KClass<*> = Any::class)

/**
 * Marks the parameter that receives the [ViewerInfo]: who asks, in which language, and through which way.
 * The library fills it; it is not a part of the address. `fun level(@Viewer viewer: ViewerInfo): Int?`
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class Viewer

/**
 * Marks the parameter that receives the [RouteInfo]: every part of the asked address and what each one is
 * (group, branch, argument, field, format). `fun war(@Route route: RouteInfo): WarBranch`
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class Route
