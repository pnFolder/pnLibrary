package org.pnfolder.pnlibrary.bukkit.commands;

import java.util.Locale;

import org.pnfolder.pnlibrary.api.runtime.PnLibraryIdentity;
import org.pnfolder.pnlibrary.api.updates.state.UpdateSnapshot;
import org.pnfolder.pnlibrary.core.text.ConsoleStyle;
import org.pnfolder.pnlibrary.updates.policy.UpdateChannelLabels;

/** Shared human-readable labels used by console and in-game status output. */
final class BukkitStatusLabels {
    private BukkitStatusLabels() {
    }

    static String javaRuntime() {
        String version = property("java.version");
        String vm = property("java.vm.name");
        String runtime;
        if (vm.toLowerCase(Locale.ROOT).startsWith("openjdk")) {
            runtime = "OpenJDK";
        } else if (!vm.trim().isEmpty()) {
            int cut = vm.indexOf(" 64-Bit");
            runtime = (cut < 0 ? vm : vm.substring(0, cut)).trim();
        } else {
            runtime = property("java.vm.vendor");
        }
        StringBuilder joined = new StringBuilder();
        for (String part : new String[] {runtime, version}) {
            if (!part.trim().isEmpty()) {
                joined.append(joined.length() == 0 ? "" : " ").append(part);
            }
        }
        return joined.toString().trim().isEmpty() ? System.getProperty("java.runtime.version", "unknown") : joined.toString();
    }

    static String channel(UpdateSnapshot snapshot) {
        return ConsoleStyle.paint(UpdateChannelLabels.title(snapshot.getChannel()),
            UpdateChannelLabels.color(snapshot.getChannel()));
    }

    static String product(String product) {
        return product.equalsIgnoreCase(PnLibraryIdentity.PRODUCT_ID) ? PnLibraryIdentity.NAME : product;
    }

    private static String property(String name) {
        String value = System.getProperty(name);
        return value == null ? "" : value.trim();
    }
}
