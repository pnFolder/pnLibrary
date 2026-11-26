package org.pnfolder.pnlibrary.core.link;

import java.util.Objects;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.updates.state.UpdateState;
import org.pnfolder.pnlibrary.common.io.TsvRecords;
import org.pnfolder.pnlibrary.core.link.report.LinkPlugin;
import org.pnfolder.pnlibrary.core.link.report.NetworkServer;

/**
 * Groups of servers on the proxy, each with the plugins every server of the group should have.
 *
 * <p>A server may be in any number of groups; a group never takes a server away from another. The groups live in
 * {@code groups.tsv} next to {@code link.key}; every call reads the file and every change writes it back, so the file
 * may also be edited by hand while the proxy runs.
 */
public final class ServerGroups {
    public static final String FILE = "groups.tsv";

    /** The name under which the proxy is a member of a group; no server can have it, so it never clashes with a real one. */
    public static final String PROXY = "@proxy";

    private static final String HEADER = "#pnlibrary-groups 2";
    private static final String GROUP = "group";
    static final String SERVER = "server";
    static final String PLUGIN = "plugin";
    static final String EXTRA = "extra";
    static final String SKIP = "skip";
    private static final int MAX_NAME = 32;

    private final Supplier<Path> file;
    private final Object lock = new Object();

    /** @param file where the groups are kept, or null while the proxy has no folder yet */
    public ServerGroups(@NotNull Supplier<Path> file) {
        this.file = Objects.requireNonNull(file, "file");
    }

    /** How {@code server} is shown to people: the proxy by a word, a server by its name. */
    public static @NotNull String label(@NotNull String server) {
        return server.equals(PROXY) ? "Прокси" : server;
    }

    /** Whether {@code name} may name a group: short and without spaces. Any word will do, {@code create} too. */
    public static boolean validName(@NotNull String name) {
        if (name.isEmpty() || name.length() > MAX_NAME) {
            return false;
        }
        for (int i = 0; i < name.length(); i++) {
            if (Character.isWhitespace(name.charAt(i)) || Character.isISOControl(name.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /** Every group in the file, sorted by name ignoring case. */
    public @NotNull List<ServerGroup> all() {
        GroupRecords records = new GroupRecords();
        for (List<String> fields : read()) {
            records.add(fields);
        }
        return records.groups();
    }

    /** The group called {@code name}, ignoring case, or null. */
    public @Nullable ServerGroup get(@NotNull String name) {
        for (ServerGroup group : all()) {
            if (group.getName().equalsIgnoreCase(name)) {
                return group;
            }
        }
        return null;
    }

    /** The groups that {@code server} belongs to, ignoring case. */
    public @NotNull List<ServerGroup> of(@NotNull String server) {
        List<ServerGroup> result = new ArrayList<>();
        for (ServerGroup group : all()) {
            for (String member : group.getServers()) {
                if (member.equalsIgnoreCase(server)) {
                    result.add(group);
                    break;
                }
            }
        }
        return result;
    }

    /** Makes an empty group; returns false when the name is taken, reserved or unfit. */
    public boolean create(@NotNull String name) {
        if (!validName(name)) {
            return false;
        }
        synchronized (lock) {
            if (get(name) != null) {
                return false;
            }
            List<ServerGroup> groups = all();
            groups.add(new ServerGroup(name, Collections.emptySet(), Collections.emptySet()));
            return write(groups);
        }
    }

    /** Deletes the group called {@code name}; returns whether there was one. */
    public boolean delete(@NotNull String name) {
        synchronized (lock) {
            List<ServerGroup> groups = all();
            ServerGroup group = find(groups, name);
            if (group == null) {
                return false;
            }
            groups.remove(group);
            return write(groups);
        }
    }

    /** Replaces the group called {@code name} with {@code change} of it; returns false when there is no such group. */
    public boolean edit(@NotNull String name, @NotNull Function<ServerGroup, ServerGroup> change) {
        synchronized (lock) {
            List<ServerGroup> groups = all();
            ServerGroup group = find(groups, name);
            if (group == null) {
                return false;
            }
            List<ServerGroup> next = new ArrayList<>();
            for (ServerGroup each : groups) {
                next.add(each == group ? change.apply(group).withName(group.getName()) : each);
            }
            return write(next);
        }
    }

    /** Compares each server of {@code group} with its report in {@code servers}. */
    public @NotNull List<ServerGroupState> compare(@NotNull ServerGroup group, @NotNull List<NetworkServer> servers) {
        List<String> names = new ArrayList<>(group.getServers());
        Collections.sort(names, new LowerCaseOrder());
        List<ServerGroupState> states = new ArrayList<>();
        for (String name : names) {
            states.add(stateOf(group, name, servers));
        }
        return states;
    }

    private static ServerGroupState stateOf(ServerGroup group, String name, List<NetworkServer> servers) {
        NetworkServer known = null;
        for (NetworkServer server : servers) {
            if (server.getName().equalsIgnoreCase(name)) {
                known = server;
                break;
            }
        }
        List<LinkPlugin> have = known == null ? Collections.emptyList() : known.getReport().getPlugins();
        Set<String> wanted = group.wanted(name);
        List<String> missing = new ArrayList<>();
        List<String> outdated = new ArrayList<>();
        if (known != null) {
            for (String plugin : wanted) {
                if (!hasProduct(have, plugin)) {
                    missing.add(plugin);
                }
            }
        }
        for (LinkPlugin plugin : have) {
            if (plugin.getState().equals(UpdateState.UPDATE_AVAILABLE.name()) && containsIgnoreCase(wanted, plugin.getProduct())) {
                outdated.add(plugin.getProduct());
            }
        }
        return new ServerGroupState(name, known, missing, outdated, group.customized(name));
    }

    private static boolean hasProduct(List<LinkPlugin> have, String plugin) {
        for (LinkPlugin each : have) {
            if (each.getProduct().equalsIgnoreCase(plugin)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsIgnoreCase(Set<String> names, String name) {
        for (String each : names) {
            if (each.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    private static ServerGroup find(List<ServerGroup> groups, String name) {
        for (ServerGroup group : groups) {
            if (group.getName().equalsIgnoreCase(name)) {
                return group;
            }
        }
        return null;
    }

    private List<List<String>> read() {
        Path path = file.get();
        return path == null ? Collections.emptyList() : TsvRecords.read(path);
    }

    private boolean write(List<ServerGroup> groups) {
        Path path = file.get();
        if (path == null) {
            return false;
        }
        List<List<String>> records = new ArrayList<>();
        for (ServerGroup group : groups) {
            records.add(row(group.getName(), GROUP, ""));
            for (String server : group.getServers()) {
                records.add(row(group.getName(), SERVER, server));
            }
            for (String plugin : group.getPlugins()) {
                records.add(row(group.getName(), PLUGIN, plugin));
            }
            addOverrides(records, group.getName(), EXTRA, group.getExtra());
            addOverrides(records, group.getName(), SKIP, group.getSkip());
        }
        return TsvRecords.write(path, HEADER, records);
    }

    private static void addOverrides(List<List<String>> records, String group, String kind, Map<String, Set<String>> map) {
        for (Map.Entry<String, Set<String>> entry : map.entrySet()) {
            for (String plugin : entry.getValue()) {
                records.add(row(group, kind, entry.getKey(), plugin));
            }
        }
    }

    private static List<String> row(String... fields) {
        return java.util.Arrays.asList(fields);
    }
}
