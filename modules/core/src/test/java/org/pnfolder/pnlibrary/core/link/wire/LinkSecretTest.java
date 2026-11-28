package org.pnfolder.pnlibrary.core.link.wire;

import org.pnfolder.pnlibrary.core.link.wire.seal.LinkSecret;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LinkSecretTest {
    @Test
    void createsTheKeyOnceAndReadsTheSameKeyBack(@TempDir Path folder) {
        byte[] key = LinkSecret.readOrCreate(folder);
        assertNotNull(key);
        assertArrayEquals(key, LinkSecret.readOrCreate(folder));
    }

    @Test
    void theKeyFileIsPrivateToItsOwner(@TempDir Path folder) throws IOException {
        assumeTrue(folder.getFileSystem().supportedFileAttributeViews().contains("posix"));
        LinkSecret.readOrCreate(folder);
        assertEquals("rw-------",
            PosixFilePermissions.toString(Files.getPosixFilePermissions(folder.resolve(LinkSecret.FILE))));
    }
}
