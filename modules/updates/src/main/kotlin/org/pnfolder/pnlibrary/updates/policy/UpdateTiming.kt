package org.pnfolder.pnlibrary.updates.policy

import java.time.Duration

/**
 * The update times an administrator may change; the library reads them once at startup.
 *
 * @property checkInterval how often the release catalogs are asked for new versions
 * @property connectTimeout how long to wait for a connection while downloading an update file
 * @property readTimeout how long to wait for data from a silent server while downloading an update file
 */
data class UpdateTiming(
    val checkInterval: Duration = UpdateDefaults.CHECK_INTERVAL,
    val connectTimeout: Duration = UpdateDefaults.CONNECT_TIMEOUT,
    val readTimeout: Duration = UpdateDefaults.READ_TIMEOUT,
)
