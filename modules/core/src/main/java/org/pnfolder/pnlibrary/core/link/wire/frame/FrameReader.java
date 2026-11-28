package org.pnfolder.pnlibrary.core.link.wire.frame;

import java.io.DataInputStream;
import java.io.IOException;

/** Reads the body of one message; returns null for a body that must be dropped. */
public interface FrameReader<T> {
    T read(DataInputStream in) throws IOException;
}
