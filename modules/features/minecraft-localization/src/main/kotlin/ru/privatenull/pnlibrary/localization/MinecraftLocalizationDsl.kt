package ru.privatenull.pnlibrary.localization

import ru.privatenull.pnlibrary.common.minecraft.MinecraftVersion

/** Kotlin DSL used to construct and load a [TranslationRequest]. */
class TranslationRequestDsl internal constructor() {
    /** Minecraft version whose translation data should be loaded. */
    lateinit var version: MinecraftVersion
    private val locales = linkedSetOf<String>()
    private var fallback: String? = null
    /** Adds preferred locale identifiers in lookup order. */
    fun locales(vararg values: String) { locales += values }

    /** Sets the optional fallback [locale]. */
    fun fallback(locale: String) { fallback = locale }
    internal fun build(): TranslationRequest = TranslationRequest.builder()
        .version(version).locales(locales).fallback(fallback).build()
}

/** Loads translations described by the Kotlin receiver [block]. */
@JvmSynthetic
fun MinecraftLocalization.load(block: TranslationRequestDsl.() -> Unit) =
    load(TranslationRequestDsl().apply(block).build())
