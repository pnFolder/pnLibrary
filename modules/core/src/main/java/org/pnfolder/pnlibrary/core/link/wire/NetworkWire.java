package org.pnfolder.pnlibrary.core.link.wire;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.pnfolder.pnlibrary.core.link.ServerGroup;
import org.pnfolder.pnlibrary.core.link.report.LinkReport;
import org.pnfolder.pnlibrary.core.link.report.NetworkReport;
import org.pnfolder.pnlibrary.core.link.report.NetworkServer;

/** The byte layout of a {@link NetworkReport}: the proxy's report, the servers and the groups. */
final class NetworkWire {
    private static final char PAIR = '\u0001';

    private NetworkWire() {
    }

    static void write(DataOutputStream out, NetworkReport network, int pluginLimit, int itemLimit, int serverLimit,
                      int groupLimit) throws IOException {
        ReportWire.write(out, network.getProxy(), pluginLimit);
        List<NetworkServer> servers = head(network.getServers(), serverLimit);
        out.writeInt(servers.size());
        for (NetworkServer server : servers) {
            out.writeUTF(WireText.take(server.getName(), LinkCodec.MAX_TEXT));
            out.writeBoolean(server.getOnline());
            ReportWire.write(out, server.getReport(), pluginLimit);
        }
        List<ServerGroup> groups = head(network.getGroups(), groupLimit);
        out.writeInt(groups.size());
        for (ServerGroup group : groups) {
            out.writeUTF(WireText.take(group.getName(), LinkCodec.MAX_TEXT));
            writeNames(out, group.getServers(), itemLimit);
            writeNames(out, group.getPlugins(), itemLimit);
            writeNames(out, pairs(group.getExtra()), itemLimit);
            writeNames(out, pairs(group.getSkip()), itemLimit);
        }
    }

    static NetworkReport read(DataInputStream input, Instant now) throws IOException {
        LinkReport proxy = ReportWire.read(input);
        if (!LinkCodec.fresh(proxy.getSentAt(), now)) {
            return null;
        }
        int count = Math.max(0, Math.min(input.readInt(), LinkCodec.MAX_SERVERS));
        List<NetworkServer> servers = new ArrayList<NetworkServer>(count);
        for (int i = 0; i < count; i++) {
            String name = input.readUTF();
            boolean online = input.readBoolean();
            servers.add(new NetworkServer(name, online, ReportWire.read(input)));
        }
        int groupCount = Math.max(0, Math.min(input.readInt(), LinkCodec.MAX_GROUPS));
        List<ServerGroup> groups = new ArrayList<ServerGroup>(groupCount);
        for (int i = 0; i < groupCount; i++) {
            String name = input.readUTF();
            Set<String> groupServers = readNames(input);
            Set<String> plugins = readNames(input);
            Map<String, Set<String>> extra = unpair(readNames(input));
            Map<String, Set<String>> skip = unpair(readNames(input));
            groups.add(new ServerGroup(name, groupServers, plugins, extra, skip));
        }
        return new NetworkReport(proxy, servers, proxy.getSentAt(), groups);
    }

    private static <T> List<T> head(List<T> items, int limit) {
        return items.subList(0, Math.min(limit, items.size()));
    }

    private static Set<String> pairs(Map<String, Set<String>> overrides) {
        Set<String> result = new LinkedHashSet<String>();
        for (Map.Entry<String, Set<String>> entry : overrides.entrySet()) {
            for (String plugin : entry.getValue()) {
                result.add(entry.getKey() + PAIR + plugin);
            }
        }
        return result;
    }

    private static Map<String, Set<String>> unpair(Set<String> pairs) {
        Map<String, Set<String>> result = new LinkedHashMap<String, Set<String>>();
        for (String pair : pairs) {
            int at = pair.indexOf(PAIR);
            if (at < 0) {
                continue;
            }
            String server = pair.substring(0, at);
            Set<String> plugins = result.get(server);
            if (plugins == null) {
                plugins = new LinkedHashSet<String>();
                result.put(server, plugins);
            }
            plugins.add(pair.substring(at + 1));
        }
        return result;
    }

    private static void writeNames(DataOutputStream out, Set<String> names, int limit) throws IOException {
        List<String> shown = head(new ArrayList<String>(names), limit);
        out.writeInt(shown.size());
        for (String name : shown) {
            out.writeUTF(WireText.take(name, LinkCodec.MAX_TEXT));
        }
    }

    private static Set<String> readNames(DataInputStream input) throws IOException {
        int count = Math.max(0, Math.min(input.readInt(), LinkCodec.MAX_GROUP_ITEMS));
        Set<String> names = new LinkedHashSet<String>();
        for (int i = 0; i < count; i++) {
            names.add(input.readUTF());
        }
        return names;
    }
}
