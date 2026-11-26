package org.pnfolder.pnlibrary.spi.platform;

import java.util.Objects;
import org.jetbrains.annotations.NotNull;

import java.net.InetSocketAddress;
import java.util.function.Supplier;

/** A backend server behind a proxy, as reported by {@link DiagnosticSections#proxySections}. */
public final class ProxyBackend {
    private final String name;
    private final int players;
    private final Supplier<InetSocketAddress> address;

    /**
     * @param name backend server name
     * @param players players currently connected to the backend
     * @param address reads the backend address; called only for reports that include sensitive data
     */
    public ProxyBackend(@NotNull String name, int players, @NotNull Supplier<InetSocketAddress> address) {
        this.name = Objects.requireNonNull(name, "name");
        this.players = players;
        this.address = Objects.requireNonNull(address, "address");
    }

    /** Backend server name. */
    @NotNull
    public String getName() {
        return name;
    }

    /** Players currently connected to the backend. */
    public int getPlayers() {
        return players;
    }

    /** Reads the backend address; called only for reports that include sensitive data. */
    @NotNull
    public Supplier<InetSocketAddress> getAddress() {
        return address;
    }
}
