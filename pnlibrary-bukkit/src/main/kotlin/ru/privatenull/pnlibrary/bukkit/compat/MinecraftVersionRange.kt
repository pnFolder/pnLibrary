package ru.privatenull.pnlibrary.bukkit.compat

/**
 * Диапазон версий Minecraft с настраиваемыми границами.
 *
 * Отсутствующая [minimum] означает отсутствие нижней границы, отсутствующая
 * [maximum] — отсутствие верхней. [MinecraftVersion.UNKNOWN] не может быть
 * границей и никогда не входит ни в один диапазон.
 *
 * Обычно удобнее использовать фабрики из [Companion]:
 * ```kotlin
 * val legacy = MinecraftVersionRange.between(V1_8_8, V1_12_2)
 * val modern = MinecraftVersionRange.atLeast(V1_20_5)
 * if (MinecraftVersion.current() in modern) enableModernApi()
 * ```
 * Java:
 * ```java
 * MinecraftVersionRange legacy = MinecraftVersionRange.between(
 *     MinecraftVersion.V1_8_8,
 *     MinecraftVersion.V1_12_2
 * );
 * if (legacy.contains(MinecraftVersion.current())) {
 *     enableLegacyApi();
 * }
 * ```
 *
 * @property minimum нижняя граница либо `null`.
 * @property maximum верхняя граница либо `null`.
 * @property includeMinimum входит ли нижняя граница в диапазон.
 * @property includeMaximum входит ли верхняя граница в диапазон.
 */
data class MinecraftVersionRange(
    val minimum: MinecraftVersion?,
    val maximum: MinecraftVersion?,
    val includeMinimum: Boolean,
    val includeMaximum: Boolean,
) {
    init {
        require(minimum?.known != false && maximum?.known != false) { "UNKNOWN cannot be a range boundary" }
        if (minimum != null && maximum != null) {
            require(!minimum.isNewerThan(maximum)) { "Minimum ${minimum.text} is newer than maximum ${maximum.text}" }
        }
    }

    /**
     * Поддерживает Kotlin-выражение `version in range`.
     * Из Java вызывается как обычный метод:
     * ```java
     * if (range.contains(version)) enableAdapter();
     * ```
     */
    operator fun contains(version: MinecraftVersion): Boolean {
        if (!version.known) return false
        val afterMinimum = minimum == null || if (includeMinimum) version.isAtLeast(minimum) else version.isNewerThan(minimum)
        val beforeMaximum = maximum == null || if (includeMaximum) version.isAtMost(maximum) else version.isOlderThan(maximum)
        return afterMinimum && beforeMaximum
    }

    /**
     * Проверяет версию запущенного ядра без отдельного вызова `current()`.
     * ```java
     * if (MinecraftVersionRange.atLeast(MinecraftVersion.V1_20_5).containsCurrent()) {
     *     enableDataComponents();
     * }
     * ```
     */
    fun containsCurrent(): Boolean = MinecraftVersion.current() in this

    /** Возвращает `true`, когда [version] не входит в диапазон. */
    fun excludes(version: MinecraftVersion): Boolean = version !in this

    /** Возвращает `true`, если диапазон содержит ровно одну версию. */
    fun isExact(): Boolean = minimum != null && minimum == maximum && includeMinimum && includeMaximum

    /**
     * Проверяет наличие хотя бы одной общей версии с [other].
     * ```java
     * if (supported.overlaps(featureVersions)) registerFeature();
     * ```
     */
    fun overlaps(other: MinecraftVersionRange): Boolean = intersection(other) != null

    /**
     * Возвращает пересечение с [other] или `null`, если общей части нет.
     * Учитывает открытость обеих границ.
     * ```java
     * MinecraftVersionRange common = supported.intersection(featureVersions);
     * if (common != null) logger.info("Общие версии: " + common);
     * ```
     */
    fun intersection(other: MinecraftVersionRange): MinecraftVersionRange? {
        val min = newer(minimum, other.minimum)
        val max = older(maximum, other.maximum)
        if (min != null && max != null && min.isNewerThan(max)) return null
        val includeMin = boundIncluded(min, true, this) && boundIncluded(min, true, other)
        val includeMax = boundIncluded(max, false, this) && boundIncluded(max, false, other)
        if (min != null && min == max && !(includeMin && includeMax)) return null
        return MinecraftVersionRange(min, max, includeMin, includeMax)
    }

    /** Формирует значение вроде `[1.8.8, 1.12.2]`, `>=1.20.5` или `<1.13`. */
    override fun toString(): String = when {
        minimum == null && maximum == null -> "all known versions"
        minimum == null -> "${if (includeMaximum) "<=" else "<"}${maximum!!.text}"
        maximum == null -> "${if (includeMinimum) ">=" else ">"}${minimum.text}"
        else -> "${if (includeMinimum) "[" else "("}${minimum.text}, ${maximum.text}${if (includeMaximum) "]" else ")"}"
    }

    companion object {
        /**
         * Включённый диапазон `[minimum, maximum]`.
         * ```java
         * MinecraftVersionRange range = MinecraftVersionRange.between(
         *     MinecraftVersion.V1_16_5,
         *     MinecraftVersion.V1_21_11
         * );
         * ```
         */
        @JvmStatic fun between(minimum: MinecraftVersion, maximum: MinecraftVersion) =
            MinecraftVersionRange(minimum, maximum, true, true)

        /**
         * Открытый диапазон `(minimum, maximum)`, не включающий границы.
         * ```java
         * MinecraftVersionRange range = MinecraftVersionRange.betweenExclusive(
         *     MinecraftVersion.V1_16_5,
         *     MinecraftVersion.V1_21
         * );
         * ```
         */
        @JvmStatic fun betweenExclusive(minimum: MinecraftVersion, maximum: MinecraftVersion) =
            MinecraftVersionRange(minimum, maximum, false, false)

        /**
         * Диапазон от [minimum] без верхней границы.
         * Java может передать второй аргумент либо использовать перегрузку:
         * ```java
         * MinecraftVersionRange inclusive = MinecraftVersionRange.from(MinecraftVersion.V1_20_5);
         * MinecraftVersionRange exclusive = MinecraftVersionRange.from(MinecraftVersion.V1_20_5, false);
         * ```
         */
        @JvmStatic @JvmOverloads fun from(minimum: MinecraftVersion, inclusive: Boolean = true) =
            MinecraftVersionRange(minimum, null, inclusive, false)

        /** Диапазон до [maximum] без нижней границы. */
        @JvmStatic @JvmOverloads fun until(maximum: MinecraftVersion, inclusive: Boolean = true) =
            MinecraftVersionRange(null, maximum, false, inclusive)

        /** Диапазон `[minimum, +∞)`. */
        @JvmStatic fun atLeast(minimum: MinecraftVersion) = from(minimum, true)

        /** Диапазон `(minimum, +∞)`. */
        @JvmStatic fun newerThan(minimum: MinecraftVersion) = from(minimum, false)

        /** Диапазон `(-∞, maximum]`. */
        @JvmStatic fun atMost(maximum: MinecraftVersion) = until(maximum, true)

        /** Диапазон `(-∞, maximum)`. */
        @JvmStatic fun olderThan(maximum: MinecraftVersion) = until(maximum, false)

        /** Диапазон, содержащий только [version]. */
        @JvmStatic fun exact(version: MinecraftVersion) = MinecraftVersionRange(version, version, true, true)

        /** Все известные библиотеке версии; [MinecraftVersion.UNKNOWN] не входит. */
        @JvmStatic fun allKnown() = MinecraftVersionRange(null, null, false, false)

        private fun newer(a: MinecraftVersion?, b: MinecraftVersion?): MinecraftVersion? = when {
            a == null -> b; b == null -> a; a.isAtLeast(b) -> a; else -> b
        }
        private fun older(a: MinecraftVersion?, b: MinecraftVersion?): MinecraftVersion? = when {
            a == null -> b; b == null -> a; a.isAtMost(b) -> a; else -> b
        }
        private fun boundIncluded(value: MinecraftVersion?, minimum: Boolean, range: MinecraftVersionRange): Boolean {
            if (value == null) return true
            return if (minimum) {
                range.minimum == null || value != range.minimum || range.includeMinimum
            } else {
                range.maximum == null || value != range.maximum || range.includeMaximum
            }
        }
    }
}
