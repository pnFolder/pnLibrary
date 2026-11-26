package org.pnfolder.pnlibrary.config;

import org.pnfolder.pnlibrary.api.config.ConfigRange;

public final class ServiceSettings {
    @ConfigRange(min = 1.0, max = 10.0)
    public int retries = 3;

    public String name = "demo";
}
