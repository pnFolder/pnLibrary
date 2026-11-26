package org.pnfolder.pnlibrary.spi.platform;

import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Map;

/** Installed native plugin inventory exposed to dependency and update services. */
public interface PlatformPluginInventory {
    /** Installed plugin names mapped to their versions. */
    @NotNull
    default Map<String, String> installedPlugins() {
        return Collections.emptyMap();
    }
}
