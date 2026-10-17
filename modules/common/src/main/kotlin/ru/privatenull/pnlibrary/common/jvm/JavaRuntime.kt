package ru.privatenull.pnlibrary.common.jvm

/** Runtime Java information available on every JVM supported by pnLibrary. */
object JavaRuntime {
    /** Java feature release, such as 8, 17 or 25; unknown values fall back to the minimum, 8. */
    @get:JvmStatic
    val feature: Int get() = parseFeature(System.getProperty("java.specification.version"))

    internal fun parseFeature(value: String?): Int = value?.removePrefix("1.")
        ?.substringBefore('.')?.substringBefore('-')?.toIntOrNull()?.takeIf { it >= 8 } ?: 8
}
