package ru.privatenull.pnlibrary.localization

import ru.privatenull.pnlibrary.common.minecraft.MinecraftVersion

class TranslationRequestDsl internal constructor() {
    lateinit var version: MinecraftVersion
    private val locales = linkedSetOf<String>()
    private var fallback: String? = null
    fun locales(vararg values: String) { locales += values }
    fun fallback(locale: String) { fallback = locale }
    internal fun build(): TranslationRequest = TranslationRequest.builder()
        .version(version).locales(locales).fallback(fallback).build()
}

fun MinecraftLocalization.load(block: TranslationRequestDsl.() -> Unit) =
    load(TranslationRequestDsl().apply(block).build())
