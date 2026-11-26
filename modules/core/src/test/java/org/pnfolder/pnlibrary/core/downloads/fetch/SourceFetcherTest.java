package org.pnfolder.pnlibrary.core.downloads.fetch;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.downloads.source.DirectDownloadSource;
import org.pnfolder.pnlibrary.common.io.Digests;
import org.pnfolder.pnlibrary.updates.engine.net.TrustedHttpClient;

class SourceFetcherTest {
    private final String first = "https://a.example.org/x.jar";
    private final String second = "https://b.example.org/x.jar";

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private static TrustedHttpClient client(Map<String, byte[]> answers) {
        return new TrustedHttpClient(Duration.ofSeconds(1), Duration.ofSeconds(1)) {
            @Override
            public byte[] get(URI uri, int maximumBytes) {
                byte[] answer = answers.get(uri.toString());
                if (answer == null) {
                    throw new IllegalStateException("нет ответа");
                }
                return answer;
            }
        };
    }

    private DirectDownloadSource source(String mirror, String sha) {
        DirectDownloadSource.Builder builder = DirectDownloadSource.builder().url(first).mirror(mirror);
        if (sha != null) {
            builder.sha256(sha);
        }
        return builder.build();
    }

    @Test
    void withoutAChecksumTheFirstAddressThatAnswersIsUsedAndNothingIsCompared() throws Exception {
        Map<String, byte[]> answers = new HashMap<>();
        answers.put(first, bytes("jar1"));
        answers.put(second, bytes("other"));

        assertArrayEquals(bytes("jar1"), new SourceFetcher(client(answers)).fetch(source(second, null), 100));
    }

    @Test
    void aMirrorAnswersWhenTheFirstAddressIsDown() throws Exception {
        Map<String, byte[]> answers = new HashMap<>();
        answers.put(second, bytes("jar1"));

        byte[] result = new SourceFetcher(client(answers)).fetch(source(second, null), 100);

        assertEquals("jar1", new String(result, StandardCharsets.UTF_8));
    }

    @Test
    void withAChecksumOnlyTheFileThatMatchesIsTaken() throws Exception {
        byte[] good = bytes("jar1");
        Map<String, byte[]> answers = new HashMap<>();
        answers.put(first, bytes("evil"));
        answers.put(second, good);

        byte[] result = new SourceFetcher(client(answers)).fetch(source(second, Digests.sha256(good)), 100);

        assertArrayEquals(good, result);
    }

    @Test
    void withAChecksumNothingIsTakenWhenNoFileMatches() {
        Map<String, byte[]> answers = new HashMap<>();
        answers.put(first, bytes("evil"));
        answers.put(second, bytes("worse"));

        assertThrows(IllegalArgumentException.class, () ->
            new SourceFetcher(client(answers)).fetch(source(second, Digests.sha256(bytes("jar1"))), 100));
    }
}
