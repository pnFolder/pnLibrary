package org.pnfolder.pnlibrary.core.link.wire;

import java.io.DataInputStream;
import java.io.IOException;

/** Reads the body of one message; returns null for a body that must be dropped. */
interface FrameReader<T> {
    T read(DataInputStream in) throws IOException;
}
