package org.pnfolder.pnlibrary.core.link.wire.frame;

import java.io.DataOutputStream;
import java.io.IOException;

/** Writes the body of one message. */
public interface FrameWriter {
    void write(DataOutputStream out) throws IOException;
}
