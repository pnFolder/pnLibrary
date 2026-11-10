package org.pnfolder.pnlibrary.core.link

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Writes and reads the signed messages of the link between a game server and the proxy.
 *
 * A message is the report followed by an HMAC-SHA256 of everything before it, made with the key both sides share.
 * A message with a wrong signature, an unknown version or a time too far from now is dropped, so a player who
 * sends bytes on the channel by hand cannot pose as a server.
 */
object LinkCodec {
    /** The plugin message channel the link uses between servers and the proxy. */
    const val CHANNEL = "pnlibrary:link"

    /** Longer lists are cut so the message stays under the plugin message limit. */
    const val MAX_PLUGINS = 150

    /** The most plugins listed per server in a network map, before the map is cut further to fit the frame. */
    const val MAX_NETWORK_PLUGINS = 40

    /** A plugin message may not be larger than this. */
    const val MAX_FRAME_BYTES = 32_000

    /** The most servers listed in a network map. */
    const val MAX_SERVERS = 30

    private const val MAX_GROUPS = 30
    private const val MAX_GROUP_ITEMS = 60
    private const val PAIR = '\u0001'
    private const val VERSION = 1
    private const val NETWORK_VERSION = 2
    private const val REQUEST_VERSION = 3
    private const val RESULT_VERSION = 4
    private const val CALL_VERSION = 5
    private const val CHUNK_VERSION = 6
    private const val MAX_RESULT_TEXT = 1_000
    private const val MAC_BYTES = 32
    private const val MAX_TEXT = 200
    private val TOLERANCE: Duration = Duration.ofMinutes(2)
    private val hmac: FrameSigner = CachedHmac()

    /** Encodes and signs a server report with [key]. */
    fun encode(report: LinkReport, key: ByteArray): ByteArray = signed(key) { out ->
        out.writeByte(VERSION)
        ReportWire.write(out, report, MAX_PLUGINS)
    }

    /** Reads [message], or returns null when the signature, the version or the time is wrong. */
    fun decode(message: ByteArray, key: ByteArray, now: Instant = Instant.now()): LinkReport? =
        verified(message, key, hmac) { input ->
            if (input.readUnsignedByte() != VERSION) return@verified null
            ReportWire.read(input).takeIf { fresh(it.sentAt, now) }
        }

    /**
     * Encodes the network map and signs it with [key].
     *
     * Servers refuse plugin messages over 32767 bytes and kick the player who carried one, so when the map is larger
     * than [MAX_FRAME_BYTES] the plugin lists, then the groups and the servers are cut until it fits.
     */
    fun encodeNetwork(network: NetworkReport, key: ByteArray): ByteArray {
        var plugins = MAX_NETWORK_PLUGINS
        var items = MAX_GROUP_ITEMS
        var servers = MAX_SERVERS
        var groups = MAX_GROUPS
        while (true) {
            val bytes = networkBytes(network, key, plugins, items, servers, groups)
            if (bytes.size <= MAX_FRAME_BYTES || (plugins == 0 && items == 0 && servers == 0 && groups == 0)) return bytes
            if (plugins > 0) {
                plugins /= 2
            } else if (items > 0) {
                items /= 2
            } else {
                servers /= 2
                groups /= 2
            }
        }
    }

    private fun networkBytes(network: NetworkReport, key: ByteArray, pluginLimit: Int, itemLimit: Int, serverLimit: Int, groupLimit: Int): ByteArray =
        signed(key) { out ->
            out.writeByte(NETWORK_VERSION)
            ReportWire.write(out, network.proxy, pluginLimit)
            val servers = network.servers.take(serverLimit)
            out.writeInt(servers.size)
            servers.forEach {
                out.writeUTF(it.name.take(MAX_TEXT))
                out.writeBoolean(it.online)
                ReportWire.write(out, it.report, pluginLimit)
            }
            val groups = network.groups.take(groupLimit)
            out.writeInt(groups.size)
            groups.forEach { group ->
                out.writeUTF(group.name.take(MAX_TEXT))
                writeNames(out, group.servers, itemLimit)
                writeNames(out, group.plugins, itemLimit)
                writeNames(out, pairs(group.extra), itemLimit)
                writeNames(out, pairs(group.skip), itemLimit)
            }
        }

    private fun pairs(overrides: Map<String, Set<String>>): Set<String> =
        overrides.flatMap { (server, plugins) -> plugins.map { "$server$PAIR$it" } }.toSet()

    private fun unpair(pairs: Set<String>): Map<String, Set<String>> =
        pairs.mapNotNull { pair -> pair.split(PAIR, limit = 2).takeIf { it.size == 2 } }.groupBy({ it[0] }, { it[1] }).mapValues { it.value.toSet() }

    private fun writeNames(out: DataOutputStream, names: Set<String>, limit: Int) {
        val shown = names.take(limit)
        out.writeInt(shown.size)
        shown.forEach { out.writeUTF(it.take(MAX_TEXT)) }
    }

    private fun readNames(input: DataInputStream): Set<String> = List(input.readInt().coerceIn(0, MAX_GROUP_ITEMS)) { input.readUTF() }.toSet()

    /** Reads a network map, or returns null when the signature, the version or the time is wrong. */
    fun decodeNetwork(message: ByteArray, key: ByteArray, now: Instant = Instant.now()): NetworkReport? =
        verified(message, key, hmac) { input ->
            if (input.readUnsignedByte() != NETWORK_VERSION) return@verified null
            val proxy = ReportWire.read(input)
            if (!fresh(proxy.sentAt, now)) return@verified null
            val count = input.readInt().coerceIn(0, MAX_SERVERS)
            val servers = List(count) { NetworkServer(input.readUTF(), input.readBoolean(), ReportWire.read(input)) }
            val groups = List(input.readInt().coerceIn(0, MAX_GROUPS)) {
                ServerGroups.Group(input.readUTF(), readNames(input), readNames(input), unpair(readNames(input)), unpair(readNames(input)))
            }
            NetworkReport(proxy, servers, proxy.sentAt, groups)
        }

    /** Encodes and signs a remote update request with [key]. */
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
        verified(message, key, hmac) { input -> if (input.readUnsignedByte() == REQUEST_VERSION) readRequest(input, now) else null }

    private fun readRequest(input: DataInputStream, now: Instant): RemoteRequest? {
        val id = UUID(input.readLong(), input.readLong())
        val sentAt = Instant.ofEpochMilli(input.readLong())
        if (!fresh(sentAt, now)) return null
        val target = input.readUTF()
        val origin = input.readUTF()
        val actor = input.readUTF()
        val actionName = input.readUTF()
        val action = RemoteAction.values().firstOrNull { it.name == actionName } ?: return null
        return RemoteRequest(id, target, origin, actor, action, input.readUTF().ifEmpty { null }, sentAt)
    }

    /** Encodes and signs the answer to a remote request with [key]. */
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
        verified(message, key, hmac) { input -> if (input.readUnsignedByte() == RESULT_VERSION) readResult(input, now) else null }

    private fun readResult(input: DataInputStream, now: Instant): RemoteResult? {
        val id = UUID(input.readLong(), input.readLong())
        val sentAt = Instant.ofEpochMilli(input.readLong())
        if (!fresh(sentAt, now)) return null
        return RemoteResult(id, input.readUTF(), input.readBoolean(), input.readUTF(), sentAt)
    }

    /** Encodes and signs a data question with [key]. */
    fun encodeCall(call: DataCall, key: ByteArray): ByteArray = signed(key) { out ->
        out.writeByte(CALL_VERSION)
        out.writeLong(call.id.mostSignificantBits)
        out.writeLong(call.id.leastSignificantBits)
        out.writeLong(call.sentAt.toEpochMilli())
        out.writeUTF(call.target.take(MAX_TEXT))
        out.writeUTF(call.origin.take(MAX_TEXT))
        out.writeUTF(call.viewer.take(MAX_TEXT))
        out.writeUTF(call.method.take(MAX_TEXT))
        val args = call.args.toByteArray(Charsets.UTF_8)
        out.writeInt(args.size)
        out.write(args)
    }

    /** Reads a question, or returns null when the signature, the version or the time is wrong. */
    fun decodeCall(message: ByteArray, key: ByteArray, now: Instant = Instant.now()): DataCall? =
        verified(message, key, hmac) { input -> if (input.readUnsignedByte() == CALL_VERSION) readCall(input, now) else null }

    private fun readCall(input: DataInputStream, now: Instant): DataCall? {
        val id = UUID(input.readLong(), input.readLong())
        val sentAt = Instant.ofEpochMilli(input.readLong())
        if (!fresh(sentAt, now)) return null
        val target = input.readUTF()
        val origin = input.readUTF()
        val viewer = input.readUTF()
        val method = input.readUTF()
        val args = String(ByteArray(input.readInt().coerceIn(0, MAX_FRAME_BYTES)).also { input.readFully(it) }, Charsets.UTF_8)
        return DataCall(id, target, origin, viewer, method, args, sentAt)
    }

    /** Encodes and signs one piece of an answer with [key]. */
    fun encodeChunk(chunk: DataChunk, key: ByteArray): ByteArray = signed(key) { out ->
        out.writeByte(CHUNK_VERSION)
        out.writeLong(chunk.id.mostSignificantBits)
        out.writeLong(chunk.id.leastSignificantBits)
        out.writeLong(chunk.sentAt.toEpochMilli())
        out.writeUTF(chunk.to.take(MAX_TEXT))
        out.writeBoolean(chunk.ok)
        out.writeInt(chunk.index)
        out.writeInt(chunk.count)
        out.writeInt(chunk.bytes.size)
        out.write(chunk.bytes)
    }

    /** Reads one piece of an answer, or returns null when the signature, the version, the time or the numbers are wrong. */
    fun decodeChunk(message: ByteArray, key: ByteArray, now: Instant = Instant.now()): DataChunk? =
        verified(message, key, hmac) { input -> if (input.readUnsignedByte() == CHUNK_VERSION) readChunk(input, now) else null }

    private fun readChunk(input: DataInputStream, now: Instant): DataChunk? {
        val id = UUID(input.readLong(), input.readLong())
        val sentAt = Instant.ofEpochMilli(input.readLong())
        if (!fresh(sentAt, now)) return null
        val to = input.readUTF()
        val ok = input.readBoolean()
        val index = input.readInt()
        val count = input.readInt()
        if (count !in 1..DataChunks.MAX_PIECES || index !in 0 until count) return null
        val bytes = ByteArray(input.readInt().coerceIn(0, DataChunks.PIECE)).also { input.readFully(it) }
        return DataChunk(id, to, ok, index, count, bytes, sentAt)
    }

    /** Reads a message of any kind a server may send to the proxy, checking the signature once; null when anything is wrong. */
    fun decodeFrame(message: ByteArray, key: ByteArray, now: Instant = Instant.now()): LinkFrame? = decodeFrame(message, key, now, hmac)

    internal fun decodeFrame(message: ByteArray, key: ByteArray, now: Instant, signer: FrameSigner): LinkFrame? =
        verified(message, key, signer) { input ->
            when (input.readUnsignedByte()) {
                VERSION -> ReportWire.read(input).takeIf { fresh(it.sentAt, now) }?.let(LinkFrame::Report)
                REQUEST_VERSION -> readRequest(input, now)?.let(LinkFrame::Request)
                RESULT_VERSION -> readResult(input, now)?.let(LinkFrame::Result)
                CALL_VERSION -> readCall(input, now)?.let(LinkFrame::Call)
                CHUNK_VERSION -> readChunk(input, now)?.let(LinkFrame::Chunk)
                else -> null
            }
        }

    private fun fresh(sentAt: Instant, now: Instant): Boolean = Math.abs(Duration.between(sentAt, now).toMillis()) <= TOLERANCE.toMillis()

    private fun signed(key: ByteArray, write: (DataOutputStream) -> Unit): ByteArray {
        val body = ByteArrayOutputStream()
        DataOutputStream(body).use(write)
        val bytes = body.toByteArray()
        return bytes + hmac.sign(bytes, key)
    }

    private fun <T> verified(message: ByteArray, key: ByteArray, signer: FrameSigner, read: (DataInputStream) -> T?): T? {
        if (message.size <= MAC_BYTES) return null
        val body = message.copyOfRange(0, message.size - MAC_BYTES)
        val mac = message.copyOfRange(message.size - MAC_BYTES, message.size)
        if (!MessageDigest.isEqual(mac, signer.sign(body, key))) return null
        return runCatching { DataInputStream(ByteArrayInputStream(body)).use(read) }.getOrNull()
    }
}
