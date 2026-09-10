package ru.privatenull.pnlibrary.api.placeholders

inline fun <reified T : Any> PlaceholderService.placeholder(name: String): PlaceholderBuilder<T> =
    placeholder(name, T::class.java)

inline fun <reified T : Any> PlaceholderService.formatter(
    name: String, formatter: PlaceholderFormatter<T>,
) = formatter(name, T::class.java, formatter)
