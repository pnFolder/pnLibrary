package org.pnfolder.pnlibrary.updates.engine.net;

import java.io.IOException;
import java.io.InputStream;

/**
 * Consumes a response body that is not kept in memory.
 *
 * @param <T> what the reader produces
 */
@FunctionalInterface
public interface StreamReader<T> {
    /** Reads {@code input} to the end or as far as it needs; the client closes the stream afterwards. */
    T read(InputStream input) throws IOException;
}
