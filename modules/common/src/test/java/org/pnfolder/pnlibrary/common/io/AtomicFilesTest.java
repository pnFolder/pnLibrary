package org.pnfolder.pnlibrary.common.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AtomicFilesTest {
    @TempDir
    Path directory;

    @Test
    void writeCreatesParentDirectoriesReplacesTheTargetAndLeavesNoTemporaryFiles() throws IOException {
        Path target = directory.resolve("nested/state.json");
        AtomicFiles.write(target, "first".getBytes(StandardCharsets.UTF_8));
        AtomicFiles.write(target, "second".getBytes(StandardCharsets.UTF_8), true);
        assertEquals("second", new String(Files.readAllBytes(target), StandardCharsets.UTF_8));
        List<String> names;
        try (Stream<Path> files = Files.list(target.getParent())) {
            names = files.map(file -> file.getFileName().toString()).collect(Collectors.toList());
        }
        assertEquals(Collections.singletonList("state.json"), names);
    }

    @Test
    void aFailingWriterKeepsThePreviousContentAndRemovesItsTemporaryFile() throws IOException {
        Path target = directory.resolve("state.json");
        AtomicFiles.write(target, "kept".getBytes(StandardCharsets.UTF_8));
        assertThrows(IllegalStateException.class, () -> AtomicFiles.write(target, temporary -> {
            throw new IllegalStateException("broken writer");
        }));
        assertEquals("kept", new String(Files.readAllBytes(target), StandardCharsets.UTF_8));
        try (Stream<Path> files = Files.list(directory)) {
            assertEquals(1L, files.count());
        }
    }

    @Test
    void moveWithoutReplacingRefusesAnExistingTarget() throws IOException {
        Path source = Files.write(directory.resolve("source"), new byte[] {1});
        Path target = Files.write(directory.resolve("target"), new byte[] {2});
        assertThrows(FileAlreadyExistsException.class, () -> AtomicFiles.move(source, target, false));
        AtomicFiles.move(source, target);
        byte[] content = Files.readAllBytes(target);
        assertEquals(1, content.length);
        assertEquals(1, content[0]);
    }

    @Test
    void digestsAreLowercaseHexadecimalAndEqualForBytesAndFiles() throws IOException {
        byte[] bytes = "abc".getBytes(StandardCharsets.UTF_8);
        Path file = Files.write(directory.resolve("data"), bytes);
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", Digests.sha256(bytes));
        assertEquals(Digests.sha256(bytes), Digests.sha256(file));
        assertEquals("00ff10", Digests.hex(new byte[] {0, -1, 16}));
    }
}
