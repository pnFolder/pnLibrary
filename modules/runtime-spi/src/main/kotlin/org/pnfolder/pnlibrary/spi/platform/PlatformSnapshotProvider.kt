package org.pnfolder.pnlibrary.spi.platform

/** Provides the lightweight native platform snapshot used by runtime logging. */
interface PlatformSnapshotProvider {
    fun snapshot(): PlatformSnapshot = PlatformSnapshot.unknown("unknown")
}
