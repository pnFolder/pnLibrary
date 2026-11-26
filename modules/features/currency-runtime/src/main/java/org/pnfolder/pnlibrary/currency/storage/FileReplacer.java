package org.pnfolder.pnlibrary.currency.storage;

import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Consumer;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.common.io.AtomicFiles;

/** Replaces the currency file with what {@code fill} writes to the temporary file it is given. */
public interface FileReplacer {
    void replace(@NotNull Path target, @NotNull Consumer<Path> fill) throws IOException;

    /** The default: the new file reaches the disk before it replaces the old one. */
    @NotNull
    static FileReplacer durable() {
        return (target, fill) -> AtomicFiles.write(target, true, fill);
    }
}
