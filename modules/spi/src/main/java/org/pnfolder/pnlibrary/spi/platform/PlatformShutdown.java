package org.pnfolder.pnlibrary.spi.platform;

/** Native resource shutdown boundary owned by a platform adapter. */
public interface PlatformShutdown extends AutoCloseable {
    /** Removes native hooks and releases platform-owned resources. */
    @Override
    void close();
}
