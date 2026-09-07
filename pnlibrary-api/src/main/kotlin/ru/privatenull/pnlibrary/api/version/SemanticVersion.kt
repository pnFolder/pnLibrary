package ru.privatenull.pnlibrary.api.version

/**
 * Платформенно-независимая семантическая версия.
 *
 * Поддерживает стабильные версии и суффиксы `alpha`, `beta`, `rc`. Числовые
 * идентификаторы сравниваются как числа: `beta.10` новее `beta.2`.
 *
 * Kotlin: `SemanticVersion.parse("2.0.0-beta.2").isAtLeast("2.0.0-beta.1")`.
 * Java: `SemanticVersion.parse("2.0.0").isAtLeast("1.9.0")`.
 */
class SemanticVersion private constructor(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val prerelease: List<String>,
) : Comparable<SemanticVersion> {

    /** Возвращает `true`, если версия не старее [minimum]. */
    fun isAtLeast(minimum: SemanticVersion): Boolean = this >= minimum

    /** Удобная перегрузка, принимающая строку версии. */
    fun isAtLeast(minimum: String): Boolean = isAtLeast(parse(minimum))

    /** Проверяет попадание в диапазон. */
    fun isIn(range: VersionRange): Boolean = range.contains(this)

    override fun compareTo(other: SemanticVersion): Int {
        compareValuesBy(this, other, SemanticVersion::major, SemanticVersion::minor, SemanticVersion::patch)
            .takeIf { it != 0 }?.let { return it }
        if (prerelease.isEmpty()) return if (other.prerelease.isEmpty()) 0 else 1
        if (other.prerelease.isEmpty()) return -1
        for (index in 0 until maxOf(prerelease.size, other.prerelease.size)) {
            val left = prerelease.getOrNull(index) ?: return -1
            val right = other.prerelease.getOrNull(index) ?: return 1
            val leftNumber = left.toIntOrNull()
            val rightNumber = right.toIntOrNull()
            val result = when {
                leftNumber != null && rightNumber != null -> leftNumber.compareTo(rightNumber)
                leftNumber != null -> -1
                rightNumber != null -> 1
                else -> left.compareTo(right, ignoreCase = true)
            }
            if (result != 0) return result
        }
        return 0
    }

    override fun equals(other: Any?): Boolean = other is SemanticVersion && compareTo(other) == 0
    override fun hashCode(): Int = listOf(major, minor, patch, prerelease.map(String::lowercase)).hashCode()
    override fun toString(): String = "$major.$minor.$patch" +
        prerelease.takeIf(List<String>::isNotEmpty)?.joinToString(".", prefix = "-").orEmpty()

    companion object {
        /** Разбирает SemVer; начальный `v` и build metadata допускаются. */
        @JvmStatic
        fun parse(raw: String): SemanticVersion = tryParse(raw)
            ?: throw IllegalArgumentException("Invalid semantic version: $raw")

        /** Возвращает версию либо `null`, если строка некорректна. */
        @JvmStatic
        fun tryParse(raw: String): SemanticVersion? {
            val value = raw.trim().removePrefix("v").substringBefore('+')
            val match = Regex("^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)(?:-([0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*))?$")
                .matchEntire(value) ?: return null
            val prerelease = match.groupValues[4].split('.').filter(String::isNotBlank)
            if (prerelease.any { it.length > 1 && it.all(Char::isDigit) && it.startsWith('0') }) return null
            return SemanticVersion(
                match.groupValues[1].toIntOrNull() ?: return null,
                match.groupValues[2].toIntOrNull() ?: return null,
                match.groupValues[3].toIntOrNull() ?: return null,
                prerelease,
            )
        }
    }
}
