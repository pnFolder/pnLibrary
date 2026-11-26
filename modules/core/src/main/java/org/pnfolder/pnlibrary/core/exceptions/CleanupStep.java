package org.pnfolder.pnlibrary.core.exceptions;

/** One cleanup action; any failure is collected by {@link ResourceCleanup}. */
@FunctionalInterface
public interface CleanupStep {
    void run() throws Throwable;
}
