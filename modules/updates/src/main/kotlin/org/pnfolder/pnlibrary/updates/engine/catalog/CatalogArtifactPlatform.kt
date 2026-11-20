package org.pnfolder.pnlibrary.updates.engine.catalog

import org.pnfolder.pnlibrary.api.platform.PlatformType

/** Whether this artifact runs on [target]: an exact platform match, or one built for every platform. */
internal fun CatalogArtifact.matchesPlatform(target: PlatformType): Boolean =
    platform == target || platform == PlatformType.UNIVERSAL
