package org.pnfolder.pnlibrary.core.link

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Writes and reads the signed messages of the link between a game server and the proxy.
 *
 * A message is the report followed by an HMAC-SHA256 of everything before it, made with the key both sides share.
 * A message with a wrong signature, an unknown version or a time too far from now is dropped, so a player who
 * sends bytes on the channel by hand cannot pose as a server.
 */
object LinkCodec {
    /** Plugin message channel of the link. */
    const val CHANNEL = "pnlibrary:link"

    /** Most plugins one report carries; the rest are cut so the message stays under the plugin message limit. */
    const val MAX_PLUGINS = 150

    private const val VERSION = 1
    private const val MAC_ALGORITHM = "HmacSHA256"
    private const val MAC_BYTES = 32
    private const val MAX_TEXT = 200
    private val TOLERANCE: Duration = Duration.ofMinutes(2)

    /** Encodes [report] and signs it with [key]. */
    fun encode(report: LinkReport, key: ByteArray): ByteArray {
        val body = ByteArrayOutputStream()
        DataOutputStream(body).use { out ->
            out.writeByte(VERSION)
            out.writeLong(report.sentAt.toEpochMilli())
            out.writeUTF(report.platform.take(MAX_TEXT))
            out.writeUTF(report.libraryVersion.take(MAX_TEXT))
            out.writeInt(report.players)
            val plugins = report.plugins.take(MAX_PLUGINS)
            out.writeInt(plugins.size)
            plugins.forEach {
                out.writeUTF(it.product.take(MAX_TEXT))
                out.writeUTF(it.version.take(MAX_TEXT))
                out.writeUTF(it.latest.orEmpty().take(MAX_TEXT))
                out.writeUTF(it.state.take(MAX_TEXT))
            }
        }
        val bytes = body.toByteArray()
        return bytes + sign(bytes, key)
    }

    /** Reads [message], or returns null when the signature, the version or the time is wrong. */
    fun decode(message: ByteArray, key: ByteArray, now: Instant = Instant.now()): LinkReport? {
        if (message.size <= MAC_BYTES) return null
        val body = message.copyOfRange(0, message.size - MAC_BYTES)
        val mac = message.copyOfRange(message.size - MAC_BYTES, message.size)
        if (!MessageDigest.isEqual(mac, sign(body, key))) return null
        return runCatching {
            DataInputStream(ByteArrayInputStream(body)).use { input ->
                if (input.readUnsignedByte() != VERSION) return null
                val sentAt = Instant.ofEpochMilli(input.readLong())
                if (Duration.between(sentAt, now).toMillis().let(Math::abs) > TOLERANCE.toMillis()) return null
                val platform = input.readUTF()
                val library = input.readUTF()
                val players = input.readInt()
                val count = input.readInt().coerceIn(0, MAX_PLUGINS)
                val plugins = List(count) { LinkPlugin(input.readUTF(), input.readUTF(), input.readUTF().ifEmpty { null }, input.readUTF()) }
                LinkReport(platform, library, players, plugins, sentAt)
            }
        }.getOrNull()
    }

    private fun sign(bytes: ByteArray, key: ByteArray): ByteArray =
        Mac.getInstance(MAC_ALGORITHM).apply { init(SecretKeySpec(key, MAC_ALGORITHM)) }.doFinal(bytes)
}
