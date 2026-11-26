package org.pnfolder.pnlibrary.core.link.wire;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.pnfolder.pnlibrary.core.link.report.LinkPlugin;
import org.pnfolder.pnlibrary.core.link.report.LinkReport;

/** The byte layout of a {@link LinkReport}, shared by server reports and network maps. */
final class ReportWire {
    private static final int MAX_TEXT = 200;

    private ReportWire() {
    }

    static void write(DataOutputStream out, LinkReport report, int limit) throws IOException {
        out.writeLong(report.getSentAt().toEpochMilli());
        out.writeUTF(WireText.take(report.getPlatform(), MAX_TEXT));
        out.writeUTF(WireText.take(report.getLibraryVersion(), MAX_TEXT));
        out.writeInt(report.getPlayers());
        List<LinkPlugin> plugins = report.getPlugins().subList(0, Math.min(limit, report.getPlugins().size()));
        out.writeInt(plugins.size());
        for (LinkPlugin plugin : plugins) {
            out.writeUTF(WireText.take(plugin.getProduct(), MAX_TEXT));
            out.writeUTF(WireText.take(plugin.getVersion(), MAX_TEXT));
            out.writeUTF(WireText.take(plugin.getLatest() == null ? "" : plugin.getLatest(), MAX_TEXT));
            out.writeUTF(WireText.take(plugin.getState(), MAX_TEXT));
        }
    }

    static LinkReport read(DataInputStream input) throws IOException {
        Instant sentAt = Instant.ofEpochMilli(input.readLong());
        String platform = input.readUTF();
        String library = input.readUTF();
        int players = input.readInt();
        int count = Math.max(0, Math.min(input.readInt(), LinkCodec.MAX_PLUGINS));
        List<LinkPlugin> plugins = new ArrayList<LinkPlugin>(count);
        for (int i = 0; i < count; i++) {
            String product = input.readUTF();
            String version = input.readUTF();
            String latest = input.readUTF();
            String state = input.readUTF();
            plugins.add(new LinkPlugin(product, version, latest.isEmpty() ? null : latest, state));
        }
        return new LinkReport(platform, library, players, plugins, sentAt);
    }
}
