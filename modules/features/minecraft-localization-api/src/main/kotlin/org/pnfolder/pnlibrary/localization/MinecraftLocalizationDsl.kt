package org.pnfolder.pnlibrary.localization

import org.pnfolder.pnlibrary.common.minecraft.MinecraftVersion
import java.util.concurrent.CompletionStage

/** Kotlin DSL used to construct and load a [TranslationRequest]. */
class TranslationRequestDsl internal constructor() {
    /** Minecraft version whose translation data should be loaded. */
    lateinit var version: MinecraftVersion

    private val locales = linkedSetOf<String>()
    private var fallback: String? = null

    /** Adds preferred locale identifiers in lookup order. */
    fun locales(vararg values: String) { locales += values }

    /** Adds preferred typed locales in lookup order. */
    fun locales(vararg values: MinecraftLocale) { values.forEach { locales += it.code } }

    /** Sets the optional fallback [locale]. */
    fun fallback(locale: String) { fallback = locale }

    /** Sets the optional fallback to a typed [locale]. */
    fun fallback(locale: MinecraftLocale) { fallback = locale.code }

    internal fun build(): TranslationRequest {
        // lateinit keeps the published field and accessors; this check replaces its generic failure message.
        require(::version.isInitialized) { "Minecraft version must be set" }
        return TranslationRequest.builder().version(version).locales(locales).fallback(fallback).build()
    }
}

/** Loads translations described by the Kotlin receiver [block]. */
@JvmSynthetic
fun MinecraftLocalization.load(block: TranslationRequestDsl.() -> Unit): CompletionStage<TranslationBundle> =
    load(TranslationRequestDsl().apply(block).build())
