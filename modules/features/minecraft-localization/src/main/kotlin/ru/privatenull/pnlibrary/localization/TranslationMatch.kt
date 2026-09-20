package ru.privatenull.pnlibrary.localization

/** One deterministic reverse-lookup result. [value] is null for keys unknown to the local API. */
data class TranslationMatch<T>(
    val key: String,
    val translation: String,
    val value: T?,
)

interface TranslationIndex<T> {
    fun findExact(text: String): List<TranslationMatch<T>>
    fun search(text: String): List<TranslationMatch<T>>
}
