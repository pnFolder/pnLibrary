package org.pnfolder.pnlibrary.api.updates

import org.pnfolder.pnlibrary.api.platform.PlatformType

/**
 * Release-asset name pattern bounded to a range of Java feature versions.
 *
 * @property pattern regular expression matched against complete release asset names
 * @property minimumJava lowest supported Java feature version, inclusive
 * @property maximumJava highest supported Java feature version, inclusive, or `null`
 * @property platform optional server/proxy platform restriction
 */
class PluginUpdateArtifact(
    val pattern: String,
    val minimumJava: Int,
    val maximumJava: Int?,
    val platform: PlatformType? = null,
) {

    /** Returns whether [javaFeature] is inside this artifact's inclusive Java range. */
    fun supports(javaFeature: Int): Boolean = javaInRange(javaFeature, minimumJava, maximumJava)
}
