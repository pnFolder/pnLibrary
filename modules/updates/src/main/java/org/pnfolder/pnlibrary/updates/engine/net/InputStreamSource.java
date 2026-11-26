package org.pnfolder.pnlibrary.updates.engine.net;

import java.io.IOException;
import java.io.InputStream;

/** Opens the body of an artifact; may fail with an I/O error. */
@FunctionalInterface
public interface InputStreamSource {
    /** Opens a new stream; the caller closes it. */
    InputStream open() throws IOException;
}
