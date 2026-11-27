package org.pnfolder.pnlibrary.core.network;

import java.net.InetSocketAddress;
import org.jetbrains.annotations.NotNull;

/** Reads {@code host:port} or {@code [ipv6]:port}, as a command takes the address of a server. */
public final class ServerAddress {
    private ServerAddress() {
    }

    /**
     * The resolved address.
     *
     * @throws IllegalArgumentException when the text is not {@code host:port}, the port is not 1 to 65535 or the host is unknown
     */
    public static @NotNull InetSocketAddress parse(@NotNull String text) {
        int colon = text.lastIndexOf(':');
        if (colon <= 0 || colon == text.length() - 1) {
            throw new IllegalArgumentException("Write the address as host:port, for example 10.0.0.5:25570: " + text);
        }
        String host = text.substring(0, colon);
        if (host.startsWith("[") && host.endsWith("]")) {
            host = host.substring(1, host.length() - 1);
        }
        int port;
        try {
            port = Integer.parseInt(text.substring(colon + 1));
        } catch (NumberFormatException notANumber) {
            throw new IllegalArgumentException("The port is not a number: " + text, notANumber);
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("The port must be 1 to 65535: " + text);
        }
        InetSocketAddress address = new InetSocketAddress(host, port);
        if (address.isUnresolved()) {
            throw new IllegalArgumentException("Unknown host: " + host);
        }
        return address;
    }
}
