package org.pnfolder.pnlibrary.spi.commands;

/** Native registration handle owned by the shared command service. */
@FunctionalInterface
public interface PlatformCommandRegistration extends AutoCloseable {
    /** Removes the native registration. */
    @Override
    void close();
}
