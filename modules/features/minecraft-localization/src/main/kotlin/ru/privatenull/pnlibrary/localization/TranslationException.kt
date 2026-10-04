package ru.privatenull.pnlibrary.localization

/** Failure raised while locating, downloading, verifying, or parsing translations. */
class TranslationException(
    /** Stable failure category suitable for programmatic handling. */
    val reason: Reason,
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause) {
    /** Supported translation failure categories. */
    enum class Reason {
        UNSUPPORTED_VERSION, INVALID_LOCALE, UNAVAILABLE_LOCALE, OFFLINE,
        REMOTE, INTEGRITY, MALFORMED_DATA, CLOSED,
    }
}
