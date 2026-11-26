package org.pnfolder.pnlibrary.core.link;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Reads the rows of {@code groups.tsv} into groups. */
final class GroupRecords {
    private final Map<String, Set<String>> servers = new LinkedHashMap<String, Set<String>>();
    private final Map<String, Set<String>> plugins = new LinkedHashMap<String, Set<String>>();
    private final Map<String, Map<String, Set<String>>> extra = new LinkedHashMap<String, Map<String, Set<String>>>();
    private final Map<String, Map<String, Set<String>>> skip = new LinkedHashMap<String, Map<String, Set<String>>>();

    void add(List<String> fields) {
        String group = fields.isEmpty() ? "" : fields.get(0);
        if (group.isEmpty()) {
            return;
        }
        set(servers, group);
        set(plugins, group);
        String kind = field(fields, 1);
        String value = field(fields, 2);
        String plugin = field(fields, 3);
        if (ServerGroups.SERVER.equals(kind) && !value.isEmpty()) {
            servers.get(group).add(value);
        } else if (ServerGroups.PLUGIN.equals(kind) && !value.isEmpty()) {
            plugins.get(group).add(value);
        } else if (ServerGroups.EXTRA.equals(kind) && !value.isEmpty() && !plugin.isEmpty()) {
            override(extra, group, value).add(plugin);
        } else if (ServerGroups.SKIP.equals(kind) && !value.isEmpty() && !plugin.isEmpty()) {
            override(skip, group, value).add(plugin);
        }
    }

    List<ServerGroup> groups() {
        List<String> names = new ArrayList<String>(servers.keySet());
        Collections.sort(names, new LowerCaseOrder());
        List<ServerGroup> result = new ArrayList<ServerGroup>();
        for (String name : names) {
            Map<String, Set<String>> groupExtra = extra.get(name);
            Map<String, Set<String>> groupSkip = skip.get(name);
            result.add(new ServerGroup(name, servers.get(name), plugins.get(name),
                groupExtra == null ? Collections.<String, Set<String>>emptyMap() : groupExtra,
                groupSkip == null ? Collections.<String, Set<String>>emptyMap() : groupSkip));
        }
        return result;
    }

    private static String field(List<String> fields, int index) {
        return index < fields.size() ? fields.get(index) : "";
    }

    private static void set(Map<String, Set<String>> map, String group) {
        if (!map.containsKey(group)) {
            map.put(group, new LinkedHashSet<String>());
        }
    }

    private static Set<String> override(Map<String, Map<String, Set<String>>> map, String group, String server) {
        Map<String, Set<String>> perServer = map.get(group);
        if (perServer == null) {
            perServer = new LinkedHashMap<String, Set<String>>();
            map.put(group, perServer);
        }
        Set<String> plugins = perServer.get(server);
        if (plugins == null) {
            plugins = new LinkedHashSet<String>();
            perServer.put(server, plugins);
        }
        return plugins;
    }
}
