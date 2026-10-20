package org.pnfolder.pnlibrary.core.runtime

import java.time.ZoneId

/**
 * Time zone in which every time shown to a person is written. Stored times are always UTC instants;
 * only the display changes. The runtime sets it from `time-zone` in config.yml at startup.
 */
internal object DisplayZone {
    @Volatile
    var zone: ZoneId = ZoneId.systemDefault()
}
