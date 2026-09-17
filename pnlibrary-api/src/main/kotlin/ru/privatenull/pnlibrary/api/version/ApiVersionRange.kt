package ru.privatenull.pnlibrary.api.version

/** Inclusive pnLibrary API generations supported by a component release. */
data class ApiVersionRange(
    val minimum: Int,
    val maximum: Int,
) {
    init {
        require(minimum > 0) { "minimum API generation must be positive" }
        require(maximum >= minimum) { "maximum API generation must be at least minimum" }
    }

    /** Returns whether [version] is inside this inclusive range. */
    fun supports(version: Int): Boolean = version in minimum..maximum
}
