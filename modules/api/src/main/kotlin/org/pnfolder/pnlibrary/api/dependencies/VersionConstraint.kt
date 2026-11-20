package org.pnfolder.pnlibrary.api.dependencies

import org.pnfolder.pnlibrary.api.version.SemanticVersion

/**
 * Inclusive lower bound plus one optional upper bound for a dependency version.
 *
 * @property minimum oldest accepted version
 * @property maximumInclusive newest accepted version, inclusive
 * @property maximumExclusive newest boundary, exclusive
 */
class VersionConstraint(
    val minimum: SemanticVersion,
    val maximumInclusive: SemanticVersion? = null,
    val maximumExclusive: SemanticVersion? = null,
) {
    init {
        require(maximumInclusive == null || maximumExclusive == null) {
            "Only one maximum version bound may be declared"
        }
        require(maximumInclusive == null || maximumInclusive >= minimum) {
            "Inclusive maximum version must be >= minimum version"
        }
        require(maximumExclusive == null || maximumExclusive > minimum) {
            "Exclusive maximum version must be > minimum version"
        }
    }

    /** Returns whether [version] lies inside this interval. */
    fun accepts(version: SemanticVersion): Boolean =
        version >= minimum &&
            (maximumInclusive == null || version <= maximumInclusive) &&
            (maximumExclusive == null || version < maximumExclusive)
}
