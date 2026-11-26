package org.pnfolder.pnlibrary.config;

import java.io.File;
import java.util.logging.Logger;

/** A plugin as the tests see it: a folder and a logger. */
public final class FakeConfigOwner {
    private final File folder;
    private final Logger logger = Logger.getLogger("fake-owner");

    public FakeConfigOwner(File folder) {
        this.folder = folder;
    }

    public File getFolder() {
        return folder;
    }

    public Logger getLogger() {
        return logger;
    }
}
