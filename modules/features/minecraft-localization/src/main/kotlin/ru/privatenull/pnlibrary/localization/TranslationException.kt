package ru.privatenull.pnlibrary.localization

class TranslationException(
    val reason: Reason,
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause) {
    enum class Reason {
        UNSUPPORTED_VERSION, INVALID_LOCALE, UNAVAILABLE_LOCALE, OFFLINE,
        REMOTE, INTEGRITY, MALFORMED_DATA, CLOSED,
    }
}
