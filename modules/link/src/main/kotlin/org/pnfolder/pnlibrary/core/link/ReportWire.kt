package org.pnfolder.pnlibrary.core.link

import java.io.DataInputStream
import java.io.DataOutputStream
import java.time.Instant

/** The byte layout of a [LinkReport], shared by server reports and network maps. */
internal object ReportWire {
    private const val MAX_TEXT = 200

    fun write(out: DataOutputStream, report: LinkReport, limit: Int) {
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

    fun read(input: DataInputStream): LinkReport {
        val sentAt = Instant.ofEpochMilli(input.readLong())
        val platform = input.readUTF()
        val library = input.readUTF()
        val players = input.readInt()
        val count = input.readInt().coerceIn(0, LinkCodec.MAX_PLUGINS)
        val plugins = List(count) { LinkPlugin(input.readUTF(), input.readUTF(), input.readUTF().ifEmpty { null }, input.readUTF()) }
        return LinkReport(platform, library, players, plugins, sentAt)
    }
}
