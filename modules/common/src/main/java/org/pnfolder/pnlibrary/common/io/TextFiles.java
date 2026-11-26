package org.pnfolder.pnlibrary.common.io;

import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;

/** Text-file operations that retain the runtime's Java 8 compatibility. */
public final class TextFiles {
    private TextFiles() {
    }

    /** Reads the whole file as UTF-8 text. */
    @NotNull
    public static String read(@NotNull Path path) throws IOException {
        return read(path, StandardCharsets.UTF_8);
    }

    /**
     * Reads the whole file as text in {@code charset}.
     *
     * @throws java.nio.charset.MalformedInputException when the content is not valid in {@code charset}
     */
    @NotNull
    public static String read(@NotNull Path path, @NotNull Charset charset) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(path, charset)) {
            StringBuilder text = new StringBuilder();
            char[] buffer = new char[8192];
            for (int read = reader.read(buffer); read >= 0; read = reader.read(buffer)) {
                text.append(buffer, 0, read);
            }
            return text.toString();
        }
    }

    /** Writes {@code text} to {@code path} as UTF-8 and returns {@code path}. */
    @NotNull
    public static Path write(@NotNull Path path, @NotNull CharSequence text) throws IOException {
        return write(path, text, StandardCharsets.UTF_8);
    }

    /** Writes {@code text} to {@code path} encoded with {@code charset} and returns {@code path}. */
    @NotNull
    public static Path write(@NotNull Path path, @NotNull CharSequence text, @NotNull Charset charset) throws IOException {
        return write(path, text, charset, new OpenOption[0]);
    }

    /** Writes {@code text} to {@code path} as UTF-8 with {@code options} and returns {@code path}. */
    @NotNull
    public static Path write(@NotNull Path path, @NotNull CharSequence text, @NotNull OpenOption... options)
        throws IOException {
        return write(path, text, StandardCharsets.UTF_8, options);
    }

    /**
     * Writes {@code text} to {@code path} encoded with {@code charset} and returns {@code path}. The text is encoded
     * before the file is touched, so invalid text leaves an existing file unchanged.
     *
     * @param options passed to {@link Files#write(Path, byte[], OpenOption...)}
     */
    @NotNull
    public static Path write(@NotNull Path path, @NotNull CharSequence text, @NotNull Charset charset,
                             @NotNull OpenOption... options) throws IOException {
        ByteBuffer encoded = charset.newEncoder().encode(CharBuffer.wrap(text));
        byte[] bytes = new byte[encoded.remaining()];
        encoded.get(bytes);
        return Files.write(path, bytes, options);
    }
}
