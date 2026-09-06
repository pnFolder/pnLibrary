package ru.privatenull.pnlibrary.api

/**
 * Sealed result type used throughout pnLibrary for operations that may fail
 * in a structured way.
 */
sealed class PnResult<out T> {
    data class Ok<T>(val value: T)         : PnResult<T>()
    data class Err(val message: String,
                   val cause: Throwable? = null) : PnResult<Nothing>()

    val isOk: Boolean  get() = this is Ok
    val isErr: Boolean get() = this is Err

    fun getOrNull(): T? = (this as? Ok)?.value
    fun errorOrNull(): String? = (this as? Err)?.message

    companion object {
        fun <T> ok(value: T): PnResult<T> = Ok(value)
        fun err(message: String, cause: Throwable? = null): PnResult<Nothing> = Err(message, cause)
    }
}
