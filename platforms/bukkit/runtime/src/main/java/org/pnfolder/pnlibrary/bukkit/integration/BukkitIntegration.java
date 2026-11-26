package org.pnfolder.pnlibrary.bukkit.integration;

/** An optional plugin the library connects to while that plugin is enabled. */
interface BukkitIntegration extends AutoCloseable {
    String getPluginName();

    // connect() must be idempotent: it is called on every event that might make the integration available.
    void connect();

    @Override
    void close();
}
