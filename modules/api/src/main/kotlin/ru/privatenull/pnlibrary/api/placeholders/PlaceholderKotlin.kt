package ru.privatenull.pnlibrary.api.placeholders

/** Creates a typed placeholder builder without requiring an explicit Java [Class]. */
inline fun <reified T : Any> PlaceholderService.placeholder(name: String): PlaceholderBuilder<T> =
    placeholder(name, T::class.java)

/** Registers [formatter] for values of [T] under [name]. */
inline fun <reified T : Any> PlaceholderService.formatter(
    name: String, formatter: PlaceholderFormatter<T>,
) = formatter(name, T::class.java, formatter)
