package org.pnfolder.pnlibrary.api.architecture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Like Paper: a file holds one type and is named after it, so the project tree reads as a list of types. */
class OneTypePerFileTest {
    private static final Path API =
        ProjectRoot.find().resolve("modules/api/src/main/java/org/pnfolder/pnlibrary/api");

    private static final Pattern DECLARATION = Pattern.compile(
        "^(?:@\\w[^\\n]*\\n)*(?:(?:public|protected|private|abstract|final|static|sealed|strictfp)\\s+)*"
            + "(?:@interface|interface|class|enum)\\s+(\\w+)",
        Pattern.MULTILINE);

    /** Internal helpers that live beside the one type that uses them. */
    private static final Set<String> HELPERS = new HashSet<>(Arrays.asList("ExpressionParser.java"));

    @Test
    @DisplayName("every file has at most one top-level type and carries its name")
    void everyFileHasAtMostOneTopLevelTypeAndCarriesItsName() throws IOException {
        List<Path> files;
        try (Stream<Path> paths = Files.walk(API)) {
            files = paths.filter(path -> path.toString().endsWith(".java")
                && !HELPERS.contains(path.getFileName().toString())).collect(Collectors.toList());
        }
        assertFalse(files.isEmpty(), "No API sources found at " + API);

        List<String> offenders = new ArrayList<>();
        int declared = 0;
        for (Path file : files) {
            String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
            List<String> types = new ArrayList<>();
            Matcher match = DECLARATION.matcher(text);
            while (match.find()) {
                types.add(match.group(1));
            }
            declared += types.size();
            String name = file.getFileName().toString().replaceAll("\\.java$", "");
            if (types.size() > 1 || (types.size() == 1 && !types.get(0).equals(name))) {
                offenders.add(API.relativize(file) + ": " + types);
            }
        }
        assertTrue(declared > 100, "The declaration pattern found only " + declared + " types");
        assertTrue(offenders.isEmpty(),
            "Split these files, one type each, named after it:\n" + String.join("\n", offenders));
    }
}
