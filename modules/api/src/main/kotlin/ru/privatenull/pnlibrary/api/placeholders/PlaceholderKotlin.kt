package ru.privatenull.pnlibrary.api.placeholders

import java.util.function.Consumer

/** Configures and registers a typed placeholder without requiring an explicit Java [Class]. */
inline fun <reified T : Any> PlaceholderService.register(
    name: String,
    noinline configure: (PlaceholderBuilder<T>) -> Unit,
): PlaceholderRegistration<T> = register(name, T::class.java, Consumer(configure))

/** Registers [formatter] for values of [T] under [name]. */
inline fun <reified T : Any> PlaceholderService.formatter(
    name: String, formatter: PlaceholderFormatter<T>,
) = formatter(name, T::class.java, formatter)
