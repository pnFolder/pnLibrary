package ru.privatenull.pnlibrary.api.placeholders

/** Configures and registers a typed placeholder without requiring an explicit Java [Class]. */
inline fun <reified T : Any> PlaceholderService.register(
    name: String,
    noinline configure: (PlaceholderBuilder<T>) -> Unit,
): PlaceholderRegistration<T> = register(name, T::class.java, java.util.function.Consumer(configure))

/** Registers [formatter] for values of [T] under [name]. */
inline fun <reified T : Any> PlaceholderService.formatter(
    name: String, formatter: PlaceholderFormatter<T>,
) = formatter(name, T::class.java, formatter)
