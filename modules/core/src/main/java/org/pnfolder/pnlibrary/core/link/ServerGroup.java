package org.pnfolder.pnlibrary.core.link;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jetbrains.annotations.NotNull;

/**
 * One group of servers: the servers in it and the plugins each of them should have.
 *
 * <p>Inside a group one server may differ from the rest: it can skip a plugin the group asks for, or carry one more
 * that the others do not need.
 */
public final class ServerGroup {
    private final String name;
    private final Set<String> servers;
    private final Set<String> plugins;
    private final Map<String, Set<String>> extra;
    private final Map<String, Set<String>> skip;

    public ServerGroup(@NotNull String name, @NotNull Set<String> servers, @NotNull Set<String> plugins) {
        this(name, servers, plugins, Collections.emptyMap(), Collections.emptyMap());
    }

    /**
     * @param name the group name
     * @param servers the servers in the group
     * @param plugins the plugins every server of the group should have
     * @param extra for a server, the plugins it should have besides {@code plugins}
     * @param skip for a server, the plugins of {@code plugins} it does not need
     */
    public ServerGroup(@NotNull String name, @NotNull Set<String> servers, @NotNull Set<String> plugins,
                       @NotNull Map<String, Set<String>> extra, @NotNull Map<String, Set<String>> skip) {
        this.name = Objects.requireNonNull(name, "name");
        this.servers = Objects.requireNonNull(servers, "servers");
        this.plugins = Objects.requireNonNull(plugins, "plugins");
        this.extra = extra;
        this.skip = skip;
    }

    public @NotNull String getName() {
        return name;
    }

    public @NotNull Set<String> getServers() {
        return servers;
    }

    public @NotNull Set<String> getPlugins() {
        return plugins;
    }

    public @NotNull Map<String, Set<String>> getExtra() {
        return extra;
    }

    public @NotNull Map<String, Set<String>> getSkip() {
        return skip;
    }

    /** The plugins {@code server} should have in this group: the common ones, without its skips, and with its extras. */
    public @NotNull Set<String> wanted(@NotNull String server) {
        Set<String> skipped = named(skip, server);
        Set<String> result = new LinkedHashSet<>();
        for (String plugin : plugins) {
            if (!containsIgnoreCase(skipped, plugin)) {
                result.add(plugin);
            }
        }
        result.addAll(named(extra, server));
        return result;
    }

    public boolean customized(@NotNull String server) {
        return !named(extra, server).isEmpty() || !named(skip, server).isEmpty();
    }

    /** The same group with the plugin set the way {@code server} should have it: {@code wanted} is true to ask for it, false to drop it. */
    public @NotNull ServerGroup with(@NotNull String server, @NotNull String plugin, boolean wanted) {
        boolean common = containsIgnoreCase(plugins, plugin);
        Set<String> extras = without(named(extra, server), plugin);
        Set<String> skips = without(named(skip, server), plugin);
        if (wanted && !common) {
            extras.add(plugin);
        }
        if (!wanted && common) {
            skips.add(plugin);
        }
        return new ServerGroup(name, servers, plugins, replaced(extra, server, extras), replaced(skip, server, skips));
    }

    public @NotNull ServerGroup addServer(@NotNull String server) {
        Set<String> next = new LinkedHashSet<>(servers);
        next.add(server);
        return new ServerGroup(name, next, plugins, extra, skip);
    }

    /** The same group without {@code server} and without what made it differ. */
    public @NotNull ServerGroup removeServer(@NotNull String server) {
        return new ServerGroup(name, without(servers, server), plugins, extra, skip).reset(server);
    }

    public @NotNull ServerGroup addPlugin(@NotNull String plugin) {
        Set<String> next = new LinkedHashSet<>(plugins);
        next.add(plugin);
        return new ServerGroup(name, servers, next, extra, skip);
    }

    /** The same group where no server has {@code plugin}, as a common one or as a difference. */
    public @NotNull ServerGroup removePlugin(@NotNull String plugin) {
        return new ServerGroup(name, servers, without(plugins, plugin), withoutPlugin(extra, plugin),
            withoutPlugin(skip, plugin));
    }

    /** The same group without any difference for {@code server}. */
    public @NotNull ServerGroup reset(@NotNull String server) {
        Set<String> none = Collections.emptySet();
        return new ServerGroup(name, servers, plugins, replaced(extra, server, none), replaced(skip, server, none));
    }

    /** The same group under another name. */
    public @NotNull ServerGroup withName(@NotNull String newName) {
        return new ServerGroup(newName, servers, plugins, extra, skip);
    }

    private static Set<String> named(Map<String, Set<String>> map, String server) {
        for (Map.Entry<String, Set<String>> entry : map.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(server)) {
                return entry.getValue();
            }
        }
        return Collections.emptySet();
    }

    private static Map<String, Set<String>> replaced(Map<String, Set<String>> map, String server, Set<String> plugins) {
        Map<String, Set<String>> result = new LinkedHashMap<>();
        for (Map.Entry<String, Set<String>> entry : map.entrySet()) {
            if (!entry.getKey().equalsIgnoreCase(server)) {
                result.put(entry.getKey(), entry.getValue());
            }
        }
        if (!plugins.isEmpty()) {
            result.put(server, plugins);
        }
        return result;
    }

    private static Map<String, Set<String>> withoutPlugin(Map<String, Set<String>> map, String plugin) {
        Map<String, Set<String>> result = new LinkedHashMap<>();
        for (Map.Entry<String, Set<String>> entry : map.entrySet()) {
            Set<String> left = without(entry.getValue(), plugin);
            if (!left.isEmpty()) {
                result.put(entry.getKey(), left);
            }
        }
        return result;
    }

    private static Set<String> without(Set<String> names, String name) {
        Set<String> result = new LinkedHashSet<>();
        for (String each : names) {
            if (!each.equalsIgnoreCase(name)) {
                result.add(each);
            }
        }
        return result;
    }

    private static boolean containsIgnoreCase(Set<String> names, String name) {
        for (String each : names) {
            if (each.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ServerGroup)) {
            return false;
        }
        ServerGroup g = (ServerGroup) other;
        return name.equals(g.name) && servers.equals(g.servers) && plugins.equals(g.plugins)
            && extra.equals(g.extra) && skip.equals(g.skip);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, servers, plugins, extra, skip);
    }

    @Override
    public String toString() {
        return "ServerGroup(name=" + name + ", servers=" + servers + ", plugins=" + plugins + ", extra=" + extra
            + ", skip=" + skip + ")";
    }
}
