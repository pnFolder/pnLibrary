package org.pnfolder.pnlibrary.api.updates

import java.util.Collections

private val SHA_256_HEX = Regex("[0-9a-fA-F]{64}")

/** Fails with "[what] SHA-256 is invalid" unless [value] is absent or 64 hexadecimal characters. */
internal fun requireSha256(value: String?, what: String) =
    require(value == null || SHA_256_HEX.matches(value)) { "$what SHA-256 is invalid" }

/** Fails with "[what] size must be positive" unless [value] is absent or above zero. */
internal fun requirePositiveSize(value: Long?, what: String) =
    require(value == null || value > 0) { "$what size must be positive" }

/** Fails unless [providesApi] is absent or a positive API generation. */
internal fun requireProvidedApi(providesApi: Int?) =
    require(providesApi == null || providesApi > 0) { "Provided API generation must be positive" }

/** Whether [javaFeature] lies within [minimum] and the optional [maximum], both inclusive. */
internal fun javaInRange(javaFeature: Int, minimum: Int, maximum: Int?): Boolean =
    javaFeature >= minimum && (maximum == null || javaFeature <= maximum)

/** Returns an unmodifiable snapshot of this list that later changes to the original cannot reach. */
internal fun <T> List<T>.frozen(): List<T> = Collections.unmodifiableList(ArrayList(this))
