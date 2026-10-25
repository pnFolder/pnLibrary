package org.pnfolder.pnlibrary.api.downloads

import org.pnfolder.pnlibrary.api.internal.requireHttpsUri
import org.pnfolder.pnlibrary.api.platform.PlatformType
import java.net.URI
import java.util.Locale

/**
 * Verified HTTPS source and runtime compatibility constraints for one file.
 *
 * @property uri HTTPS URI from which the file is downloaded
 * @property platform optional platform restriction
 * @property minimumJava oldest supported Java feature version
 * @property maximumJava newest supported Java feature version, or `null` when unbounded
 * @property size expected byte length, when integrity metadata is available
 * @property sha256 expected lowercase SHA-256 digest, when available
 */
class DirectDownloadSource private constructor(builder: Builder) {
    val uri: URI = requireNotNull(builder.uri) { "Download URL is required" }
    val platform: PlatformType? = builder.platform
    val minimumJava: Int = builder.minimumJava
    val maximumJava: Int? = builder.maximumJava
    val size: Long? = builder.size
    val sha256: String? = builder.sha256

    /** Returns whether this source supports [platform] on [javaFeature]. */
    fun supports(platform: PlatformType, javaFeature: Int): Boolean =
        (this.platform == null || this.platform == platform) && javaFeature >= minimumJava &&
            (maximumJava == null || javaFeature <= maximumJava)

    /** Fluent builder for a validated [DirectDownloadSource]. */
    class Builder internal constructor() {
        internal var uri: URI? = null
        internal var platform: PlatformType? = null
        internal var minimumJava = 8
        internal var maximumJava: Int? = null
        internal var size: Long? = null
        internal var sha256: String? = null

        /** Sets the required HTTPS download URL. */
        fun url(value: String): Builder = apply {
            uri = requireHttpsUri(value, "Download URL must use HTTPS")
        }

        /** Restricts the source to [value]. */
        fun platform(value: PlatformType): Builder = apply { platform = value }

        /** Restricts the source to the inclusive Java feature-version range. */
        @JvmOverloads
        fun java(minimum: Int, maximum: Int? = null): Builder = apply {
            require(minimum >= 8 && (maximum == null || maximum >= minimum)) { "Java version range is invalid" }
            minimumJava = minimum
            maximumJava = maximum
        }

        /** Sets the expected [size] and SHA-256 digest used after download. */
        fun integrity(size: Long, sha256: String): Builder = apply {
            require(size > 0) { "Download size must be positive" }
            require(sha256.matches(Regex("[0-9a-fA-F]{64}"))) { "SHA-256 must contain 64 hexadecimal characters" }
            this.size = size
            this.sha256 = sha256.lowercase(Locale.ROOT)
        }

        /** Validates this configuration and creates an immutable source. */
        fun build(): DirectDownloadSource = DirectDownloadSource(this)
    }

    /** Creates source builders. */
    companion object {
        /** Returns an empty source builder. */
        @JvmStatic
        fun builder(): Builder = Builder()
    }
}
