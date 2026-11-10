package org.pnfolder.pnlibrary.core.link

import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** Makes the signature that closes every link message. */
internal fun interface FrameSigner {
    fun sign(bytes: ByteArray, key: ByteArray): ByteArray
}

/** HMAC-SHA256 that keeps one initialised [Mac] per thread and key, because creating one is the costly part of a signature. */
internal class CachedHmac(private val create: () -> Mac = { Mac.getInstance(ALGORITHM) }) : FrameSigner {
    private val perThread = ThreadLocal<Keyed>()

    override fun sign(bytes: ByteArray, key: ByteArray): ByteArray {
        val cached = perThread.get()?.takeIf { MessageDigest.isEqual(it.key, key) }
            ?: Keyed(key.copyOf(), create().apply { init(SecretKeySpec(key, ALGORITHM)) }).also(perThread::set)
        return cached.mac.doFinal(bytes)
    }

    private class Keyed(val key: ByteArray, val mac: Mac)

    private companion object {
        const val ALGORITHM = "HmacSHA256"
    }
}
