package org.pnfolder.pnlibrary.bukkit.item;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Profile ids and names derived from the texture URL, so one texture always gives the same profile. */
final class ProfileNames {
    private ProfileNames() {
    }

    static UUID id(URL url) {
        return UUID.nameUUIDFromBytes(("pnLibrary:" + url).getBytes(StandardCharsets.UTF_8));
    }

    static String name(UUID id) {
        return "pn" + id.toString().replace("-", "").substring(0, 14);
    }
}
