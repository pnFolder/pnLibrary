package org.pnfolder.pnlibrary.api.remote

import org.pnfolder.pnlibrary.api.platform.PlatformType
import java.util.Locale

/**
 * Runtime platform identity exposed to a remote policy.
 *
 * @property type normalized pnLibrary platform family
 * @property name platform implementation name
 * @property version platform implementation version
 */
data class PlatformInfo(
    val type: PlatformType,
    val name: String,
    val version: String,
) {
    /** Normalized alphanumeric implementation key used for tolerant comparisons. */
    val key: String = name.filter(Char::isLetterOrDigit).lowercase(Locale.ROOT)

    /** Returns whether the normalized platform name equals [value]. */
    fun isNamed(value: String): Boolean =
        key == value.filter(Char::isLetterOrDigit).lowercase(Locale.ROOT)
}
