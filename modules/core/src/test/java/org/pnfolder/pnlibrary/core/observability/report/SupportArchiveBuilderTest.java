package org.pnfolder.pnlibrary.core.observability.report;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SupportArchiveBuilderTest {
    @Test
    void enforcesTheUncompressedPayloadBudget() {
        SupportArchiveBuilder builder = new SupportArchiveBuilder(4);
        builder.bytes("first.txt", new byte[]{1, 2, 3});

        assertThrows(IllegalArgumentException.class, () -> builder.bytes("second.txt", new byte[]{4, 5}));
    }

    @Test
    void copiesEntryBytesAndBuildsWithoutMutatingState() throws IOException {
        byte[] source = {1, 2, 3};
        SupportArchiveBuilder builder = new SupportArchiveBuilder(10);
        builder.bytes("value.bin", source);
        source[0] = 9;

        Map<String, byte[]> first = unzip(builder.build());
        Map<String, byte[]> second = unzip(builder.build());

        assertArrayEquals(new byte[]{1, 2, 3}, first.get("pn-diagnostic/value.bin"));
        assertArrayEquals(first.get("pn-diagnostic/value.bin"), second.get("pn-diagnostic/value.bin"));
        assertTrue(first.containsKey("pn-diagnostic/checksums.json"));
        assertTrue(second.containsKey("pn-diagnostic/checksums.json"));
    }

    private static Map<String, byte[]> unzip(byte[] archive) throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
            for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                ByteArrayOutputStream content = new ByteArrayOutputStream();
                byte[] chunk = new byte[1024];
                for (int read = zip.read(chunk); read >= 0; read = zip.read(chunk)) {
                    content.write(chunk, 0, read);
                }
                entries.put(entry.getName(), content.toByteArray());
            }
        }
        return entries;
    }
}
