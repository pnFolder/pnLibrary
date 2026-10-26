package org.pnfolder.pnlibrary.common.time

import java.time.ZoneId

/**
 * Time zone in which every time shown to a person is written. Stored times are always UTC instants;
 * only the display changes. The runtime sets it from `time-zone` in config.yml at startup.
 */
object DisplayZone {
    /** The zone used for display; defaults to the system zone until the runtime sets it. */
    @Volatile
    var zone: ZoneId = ZoneId.systemDefault()
}
