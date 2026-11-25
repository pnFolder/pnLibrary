package org.pnfolder.pnlibrary.api.downloads.source

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
 * @property mirrors other addresses of the same file
 */
class DirectDownloadSource private constructor(builder: Builder) {
    val uri: URI = requireNotNull(builder.uri) { "Download URL is required" }
    val platform: PlatformType? = builder.platform
    val minimumJava: Int = builder.minimumJava
    val maximumJava: Int? = builder.maximumJava
    val size: Long? = builder.size
    val sha256: String? = builder.sha256

    /** Largest accepted file in bytes for this file only; `null` means the library-wide limit (512 MB). */
    val maxBytes: Long? = builder.maxBytes

    /** Other HTTPS addresses of the same file, tried in order after [uri] when it does not answer; with a checksum, the first file that matches wins. */
    val mirrors: List<URI> = builder.mirrors.toList()

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
        internal var maxBytes: Long? = null
        internal val mirrors = mutableListOf<URI>()

        /** Sets the HTTPS URL to download from. */
        fun url(value: String): Builder = apply {
            uri = requireHttpsUri(value, "Download URL must use HTTPS")
        }

        /** Adds another HTTPS address of the same file. */
        fun mirror(value: String): Builder = apply {
            mirrors += requireHttpsUri(value, "Mirror URL must use HTTPS")
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

        /** Sets the expected size and SHA-256 checksum the downloaded file must match. */
        fun integrity(size: Long, sha256: String): Builder = apply {
            require(size > 0) { "Download size must be positive" }
            require(sha256.matches(Regex("[0-9a-fA-F]{64}"))) { "SHA-256 must contain 64 hexadecimal characters" }
            this.size = size
            this.sha256 = sha256.lowercase(Locale.ROOT)
        }

        /** Sets the largest accepted size of this file in bytes, smaller or larger than the library-wide limit (up to 2 GB). */
        fun maxBytes(value: Long): Builder = apply {
            require(value in 1..Int.MAX_VALUE.toLong()) { "Size limit must be between 1 byte and 2 GB" }
            maxBytes = value
        }

        /** Sets the SHA-256 checksum the downloaded file must match; the size is not checked. */
        fun sha256(sha256: String): Builder = apply {
            require(sha256.matches(Regex("[0-9a-fA-F]{64}"))) { "SHA-256 must contain 64 hexadecimal characters" }
            this.sha256 = sha256.lowercase(Locale.ROOT)
        }

        /** Validates this configuration and creates an immutable source. */
        fun build(): DirectDownloadSource = DirectDownloadSource(this)
    }

    companion object {
        /** Creates a new [Builder]. */
        @JvmStatic
        fun builder(): Builder = Builder()
    }
}
