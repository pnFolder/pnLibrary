package org.pnfolder.pnlibrary.core.link.wire

import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

internal fun interface FrameSigner {
    fun sign(bytes: ByteArray, key: ByteArray): ByteArray
}

// One Mac per thread per key — Mac init is the expensive part; doFinal() resets to the initialized state for reuse.
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
