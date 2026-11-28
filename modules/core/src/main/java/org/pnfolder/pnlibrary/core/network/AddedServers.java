package org.pnfolder.pnlibrary.core.network;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.network.NetworkServer;

/**
 * The servers added to a proxy while it runs, and the removal of any server. It checks the name and the address of a
 * new server, refuses a name that is taken and remembers what it added, so that none is left behind when it closes.
 * Any registered server can be removed, also the proxy's own and those of other plugins; the proxy platforms only say
 * how to register and unregister a server natively.
 */
public final class AddedServers {
    /** What a proxy platform does natively. */
    public interface Platform {
        /** Whether a server of that name (any case) is known to the proxy. */
        boolean exists(String name);

        /** Registers the server and returns it. */
        NetworkServer register(String name, InetSocketAddress address);

        /** Unregisters the server of that name, whoever registered it. */
        void unregister(String name);
    }

    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    private final Platform platform;
    private final Set<String> added = new HashSet<>();
    private final List<String> order = new ArrayList<>();

    public AddedServers(@NotNull Platform platform) {
        this.platform = platform;
    }

    /** Adds the server; see {@code NetworkProxy#addServer}. */
    public synchronized @NotNull NetworkServer add(@NotNull String name, @NotNull InetSocketAddress address) {
        if (!NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("A server name is 1 to 64 letters, digits, - or _: " + name);
        }
        if (address.isUnresolved()) {
            throw new IllegalArgumentException("The address " + address + " cannot be resolved; use new InetSocketAddress(host, port)");
        }
        if (platform.exists(name)) {
            throw new IllegalArgumentException("A server named " + name + " already exists");
        }
        NetworkServer server = platform.register(name, address);
        added.add(name.toLowerCase(Locale.ROOT));
        order.add(name);
        return server;
    }

    /** Removes {@code server}, whoever registered it; false when the proxy does not know it. */
    public synchronized boolean remove(@NotNull NetworkServer server) {
        String name = server.getName();
        if (!platform.exists(name)) {
            return false;
        }
        platform.unregister(name);
        added.remove(name.toLowerCase(Locale.ROOT));
        order.removeIf(known -> known.equalsIgnoreCase(name));
        return true;
    }

    /** The names of the servers added here that are still registered, oldest first. */
    public synchronized @NotNull List<String> names() {
        return new ArrayList<>(order);
    }

    /** Removes every server this added, for the shutdown of the library. */
    public synchronized void close() {
        for (String name : new ArrayList<>(order)) {
            platform.unregister(name);
        }
        added.clear();
        order.clear();
    }
}
