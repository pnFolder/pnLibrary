package org.pnfolder.pnlibrary.core.link.wire;

import java.io.DataOutputStream;
import java.io.IOException;

/** Writes the body of one message. */
interface FrameWriter {
    void write(DataOutputStream out) throws IOException;
}
