package ru.privatenull.pnlibrary.api.security

/** Owns a secret buffer and lends disposable copies for scoped access. */
class SecretValue(value: CharArray) : AutoCloseable {

    private val characters = value.copyOf()
    private var closed = false

    /** Erases the borrowed copy even when the operation fails. Do not retain that copy. */
    @Synchronized
    fun <R> useSecret(operation: (CharArray) -> R): R {
        check(!closed) { "Secret value is closed" }
        val borrowed = characters.copyOf()

        return try {
            operation(borrowed)
        } finally {
            borrowed.fill('\u0000')
        }
    }

    /** Erases the owned buffer and prevents subsequent access. */
    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        characters.fill('\u0000')
    }

    override fun toString(): String = "SecretValue([REDACTED])"
}
