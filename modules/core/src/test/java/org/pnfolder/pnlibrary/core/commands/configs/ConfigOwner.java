package org.pnfolder.pnlibrary.core.commands.configs;

import java.nio.file.Path;

/** An owner object that has a data folder. */
final class ConfigOwner {
    final Path folder;

    ConfigOwner(Path folder) {
        this.folder = folder;
    }
}
