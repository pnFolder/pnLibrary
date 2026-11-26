package org.pnfolder.pnlibrary.core.metrics.faststats;

/** Minimal lifecycle contract shared by all FastStats platform contexts. */
public interface FastStatsContext {

    /** Starts submission; called after the configured metrics are registered. */
    void ready();

    /** Stops submission and releases the context. */
    void shutdown();
}
