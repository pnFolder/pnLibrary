package org.pnfolder.pnlibrary.core.link

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.util.UUID
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

    /** Most plugins listed per server inside the network map. */
    const val MAX_NETWORK_PLUGINS = 40

    /** Most servers the network map carries. */
    const val MAX_SERVERS = 30

    private const val VERSION = 1
    private const val NETWORK_VERSION = 2
    private const val REQUEST_VERSION = 3
    private const val RESULT_VERSION = 4
    private const val MAX_RESULT_TEXT = 1_000
    private const val MAC_ALGORITHM = "HmacSHA256"
    private const val MAC_BYTES = 32
    private const val MAX_TEXT = 200
    private val TOLERANCE: Duration = Duration.ofMinutes(2)

    /** Encodes [report] and signs it with [key]. */
    fun encode(report: LinkReport, key: ByteArray): ByteArray = signed(key) { out ->
        out.writeByte(VERSION)
        writeReport(out, report, MAX_PLUGINS)
    }

    /** Reads [message], or returns null when the signature, the version or the time is wrong. */
    fun decode(message: ByteArray, key: ByteArray, now: Instant = Instant.now()): LinkReport? =
        verified(message, key) { input ->
            if (input.readUnsignedByte() != VERSION) return@verified null
            readReport(input).takeIf { fresh(it.sentAt, now) }
        }

    /** Encodes the network map and signs it with [key]; the plugin lists are cut shorter so the whole map fits. */
    fun encodeNetwork(network: NetworkReport, key: ByteArray): ByteArray = signed(key) { out ->
        out.writeByte(NETWORK_VERSION)
        writeReport(out, network.proxy, MAX_NETWORK_PLUGINS)
        val servers = network.servers.take(MAX_SERVERS)
        out.writeInt(servers.size)
        servers.forEach {
            out.writeUTF(it.name.take(MAX_TEXT))
            out.writeBoolean(it.online)
            writeReport(out, it.report, MAX_NETWORK_PLUGINS)
        }
    }

    /** Reads a network map, or returns null when the signature, the version or the time is wrong. */
    fun decodeNetwork(message: ByteArray, key: ByteArray, now: Instant = Instant.now()): NetworkReport? =
        verified(message, key) { input ->
            if (input.readUnsignedByte() != NETWORK_VERSION) return@verified null
            val proxy = readReport(input)
            if (!fresh(proxy.sentAt, now)) return@verified null
            val count = input.readInt().coerceIn(0, MAX_SERVERS)
            NetworkReport(proxy, List(count) { NetworkServer(input.readUTF(), input.readBoolean(), readReport(input)) }, proxy.sentAt)
        }

    /** Encodes a remote request and signs it with [key]. */
    fun encodeRequest(request: RemoteRequest, key: ByteArray): ByteArray = signed(key) { out ->
        out.writeByte(REQUEST_VERSION)
        out.writeLong(request.id.mostSignificantBits)
        out.writeLong(request.id.leastSignificantBits)
        out.writeLong(request.sentAt.toEpochMilli())
        out.writeUTF(request.target.take(MAX_TEXT))
        out.writeUTF(request.origin.take(MAX_TEXT))
        out.writeUTF(request.actor.take(MAX_TEXT))
        out.writeUTF(request.action.name)
        out.writeUTF(request.product.orEmpty().take(MAX_TEXT))
    }

    /** Reads a remote request, or returns null when the signature, the version, the time or the action is wrong. */
    fun decodeRequest(message: ByteArray, key: ByteArray, now: Instant = Instant.now()): RemoteRequest? =
        verified(message, key) { input ->
            if (input.readUnsignedByte() != REQUEST_VERSION) return@verified null
            val id = UUID(input.readLong(), input.readLong())
            val sentAt = Instant.ofEpochMilli(input.readLong())
            if (!fresh(sentAt, now)) return@verified null
            val target = input.readUTF()
            val origin = input.readUTF()
            val actor = input.readUTF()
            val actionName = input.readUTF()
            val action = RemoteAction.values().firstOrNull { it.name == actionName } ?: return@verified null
            RemoteRequest(id, target, origin, actor, action, input.readUTF().ifEmpty { null }, sentAt)
        }

    /** Encodes the answer to a remote request and signs it with [key]. */
    fun encodeResult(result: RemoteResult, key: ByteArray): ByteArray = signed(key) { out ->
        out.writeByte(RESULT_VERSION)
        out.writeLong(result.id.mostSignificantBits)
        out.writeLong(result.id.leastSignificantBits)
        out.writeLong(result.sentAt.toEpochMilli())
        out.writeUTF(result.origin.take(MAX_TEXT))
        out.writeBoolean(result.ok)
        out.writeUTF(result.text.take(MAX_RESULT_TEXT))
    }

    /** Reads the answer to a remote request, or returns null when the signature, the version or the time is wrong. */
    fun decodeResult(message: ByteArray, key: ByteArray, now: Instant = Instant.now()): RemoteResult? =
        verified(message, key) { input ->
            if (input.readUnsignedByte() != RESULT_VERSION) return@verified null
            val id = UUID(input.readLong(), input.readLong())
            val sentAt = Instant.ofEpochMilli(input.readLong())
            if (!fresh(sentAt, now)) return@verified null
            RemoteResult(id, input.readUTF(), input.readBoolean(), input.readUTF(), sentAt)
        }

    private fun fresh(sentAt: Instant, now: Instant): Boolean = Math.abs(Duration.between(sentAt, now).toMillis()) <= TOLERANCE.toMillis()

    private fun writeReport(out: DataOutputStream, report: LinkReport, limit: Int) {
        out.writeLong(report.sentAt.toEpochMilli())
        out.writeUTF(report.platform.take(MAX_TEXT))
        out.writeUTF(report.libraryVersion.take(MAX_TEXT))
        out.writeInt(report.players)
        val plugins = report.plugins.take(limit)
        out.writeInt(plugins.size)
        plugins.forEach {
            out.writeUTF(it.product.take(MAX_TEXT))
            out.writeUTF(it.version.take(MAX_TEXT))
            out.writeUTF(it.latest.orEmpty().take(MAX_TEXT))
            out.writeUTF(it.state.take(MAX_TEXT))
        }
    }

    private fun readReport(input: DataInputStream): LinkReport {
        val sentAt = Instant.ofEpochMilli(input.readLong())
        val platform = input.readUTF()
        val library = input.readUTF()
        val players = input.readInt()
        val count = input.readInt().coerceIn(0, MAX_PLUGINS)
        val plugins = List(count) { LinkPlugin(input.readUTF(), input.readUTF(), input.readUTF().ifEmpty { null }, input.readUTF()) }
        return LinkReport(platform, library, players, plugins, sentAt)
    }

    private fun signed(key: ByteArray, write: (DataOutputStream) -> Unit): ByteArray {
        val body = ByteArrayOutputStream()
        DataOutputStream(body).use(write)
        val bytes = body.toByteArray()
        return bytes + sign(bytes, key)
    }

    private fun <T> verified(message: ByteArray, key: ByteArray, read: (DataInputStream) -> T?): T? {
        if (message.size <= MAC_BYTES) return null
        val body = message.copyOfRange(0, message.size - MAC_BYTES)
        val mac = message.copyOfRange(message.size - MAC_BYTES, message.size)
        if (!MessageDigest.isEqual(mac, sign(body, key))) return null
        return runCatching { DataInputStream(ByteArrayInputStream(body)).use(read) }.getOrNull()
    }

    private fun sign(bytes: ByteArray, key: ByteArray): ByteArray =
        Mac.getInstance(MAC_ALGORITHM).apply { init(SecretKeySpec(key, MAC_ALGORITHM)) }.doFinal(bytes)
}
