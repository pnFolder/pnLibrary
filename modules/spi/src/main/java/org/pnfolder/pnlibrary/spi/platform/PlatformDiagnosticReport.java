package org.pnfolder.pnlibrary.spi.platform;

import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Map;

/** Produces the privacy-aware native details included in support reports. */
public interface PlatformDiagnosticReport {
    /** Native details for support reports; sensitive values are included only when {@code includeSensitive} is true. */
    @NotNull
    default Map<String, Object> diagnosticDetails(boolean includeSensitive) {
        return Collections.emptyMap();
    }
}
