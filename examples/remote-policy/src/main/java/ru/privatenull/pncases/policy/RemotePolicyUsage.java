package ru.privatenull.pncases.policy;

import org.bukkit.plugin.java.JavaPlugin;
import ru.privatenull.pnlibrary.remote.bukkit.RemoteCheckListener;
import ru.privatenull.pnlibrary.remote.bukkit.RemoteCheckOptions;
import ru.privatenull.pnlibrary.remote.bukkit.RemoteCheckRunner;

/** Copy this into the host plugin's onEnable; the URL points directly to a .java file. */
public final class RemotePolicyUsage {
    private RemotePolicyUsage() { }

    public static void start(JavaPlugin plugin) {
        RemoteCheckOptions options = RemoteCheckOptions.builder(
                "https://raw.githubusercontent.com/pnFolder/pnRemotePolicies/main/pnCase/Policy.java")
            .value("product", plugin.getName())
            .intervalTicks(6L * 60L * 60L * 20L)
            .listener(new RemoteCheckListener() {
                @Override public void denied(ru.privatenull.pnlibrary.remote.bukkit.RemoteCheckContext context, String reason) {
                    plugin.getLogger().warning("Remote policy denied startup: " + reason);
                }
                @Override public void failed(Throwable error) {
                    plugin.getLogger().warning("Remote policy failed: " + error.getMessage());
                }
            })
            .build();
        RemoteCheckRunner.schedule(plugin, options);
    }
}
