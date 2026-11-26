package org.pnfolder.pnlibrary.config.yaml;

import org.pnfolder.pnlibrary.config.yaml.text.YamlText;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.config.CommentPolicy;
import org.pnfolder.pnlibrary.api.config.MissingValuePolicy;

/**
 * Adds keys and comments of the code defaults to an administrator's YAML line by line, so that every value,
 * comment and ordering the administrator wrote stays as it was. A YAML parser would lose the comments.
 *
 * <p>Nested keys are found by the indentation the file actually uses: an administrator who re-indented a
 * section with four spaces must not get a second copy of every key in it.
 */
final class YamlDefaultsMerger {
    private YamlDefaultsMerger() {
    }

    /** Outcome of {@code merge}. */
    static final class Result {
        private final String content;
        private final List<String> addedPaths;
        private final List<String> addedComments;

        /**
         * Creates a result.
         *
         * @param content the merged YAML text
         * @param addedPaths dotted paths of the keys inserted from the defaults
         * @param addedComments dotted paths of the keys that received a default comment
         */
        Result(@NotNull String content, @NotNull List<String> addedPaths, @NotNull List<String> addedComments) {
            this.content = content;
            this.addedPaths = addedPaths;
            this.addedComments = addedComments;
        }

        @NotNull
        String getContent() {
            return content;
        }

        @NotNull
        List<String> getAddedPaths() {
            return addedPaths;
        }

        @NotNull
        List<String> getAddedComments() {
            return addedComments;
        }

        boolean isChanged() {
            return !addedPaths.isEmpty() || !addedComments.isEmpty();
        }
    }

    private static final class Place {
        final int line;
        final int indent;

        Place(int line, int indent) {
            this.line = line;
            this.indent = indent;
        }
    }

    /** Adds to {@code existing} every key of {@code defaults} and keeps its comments as they are. */
    @NotNull
    static Result merge(@NotNull String existing, @NotNull String defaults) {
        return merge(existing, defaults, MissingValuePolicy.ADD, CommentPolicy.PRESERVE);
    }

    /** Adds to {@code existing} the keys of {@code defaults} allowed by {@code values}. */
    @NotNull
    static Result merge(@NotNull String existing, @NotNull String defaults, @NotNull MissingValuePolicy values) {
        return merge(existing, defaults, values, CommentPolicy.PRESERVE);
    }

    /** Adds to {@code existing} the keys of {@code defaults} allowed by {@code values} and the comments allowed by {@code comments}. */
    @NotNull
    static Result merge(@NotNull String existing, @NotNull String defaults, @NotNull MissingValuePolicy values,
                        @NotNull CommentPolicy comments) {
        List<String> target = dropTrailingEmpty(YamlText.lines(existing));
        List<String> source = dropTrailingEmpty(YamlText.lines(defaults));
        List<YamlBlock> candidates = collect(source, 0, source.size(), 0, Collections.emptyList());
        List<String> added = values == MissingValuePolicy.ADD
                ? addMissing(target, source, candidates) : Collections.emptyList();
        List<String> commented = Collections.emptyList();
        if (comments == CommentPolicy.ADD_MISSING) {
            List<YamlBlock> remaining = new ArrayList<>();
            for (YamlBlock candidate : candidates) {
                if (!added.contains(candidate.dotted())) {
                    remaining.add(candidate);
                }
            }
            commented = addComments(target, source, remaining);
        }
        return new Result(YamlText.trimEnd(String.join("\n", target)) + "\n", added, commented);
    }

    /** Dotted paths of every key in {@code yaml}, parents before their children. */
    @NotNull
    static List<String> paths(@NotNull String yaml) {
        List<String> lines = YamlText.lines(yaml);
        List<String> paths = new ArrayList<>();
        for (YamlBlock block : collect(lines, 0, lines.size(), 0, Collections.emptyList())) {
            paths.add(block.dotted());
        }
        return paths;
    }

    private static List<String> dropTrailingEmpty(List<String> lines) {
        List<String> result = new ArrayList<>(lines);
        while (!result.isEmpty() && result.get(result.size() - 1).isEmpty()) {
            result.remove(result.size() - 1);
        }
        return result;
    }

    private static List<String> addMissing(List<String> target, List<String> source, List<YamlBlock> candidates) {
        List<String> added = new ArrayList<>();
        for (YamlBlock candidate : candidates) {
            if (find(target, candidate.path()) != null) {
                continue;
            }
            Place place = insertionPlace(target, candidate.path().subList(0, candidate.path().size() - 1));
            if (place == null) {
                continue;
            }
            if (place.line == target.size() && !target.isEmpty() && !YamlText.isBlank(target.get(target.size() - 1))) {
                target.add("");
            }
            boolean afterBlank = place.line == target.size() - 1 && !target.isEmpty() && target.get(target.size() - 1).isEmpty();
            int index = afterBlank ? target.size() : place.line;
            target.addAll(index, reindent(source.subList(candidate.start(), candidate.end()), candidate.indent(), place.indent));
            added.add(candidate.dotted());
        }
        return added;
    }

    private static List<String> addComments(List<String> target, List<String> source, List<YamlBlock> candidates) {
        List<String> commented = new ArrayList<>();
        for (YamlBlock candidate : candidates) {
            List<String> sourceComments = comments(source.subList(candidate.start(), candidate.keyLine()));
            if (sourceComments.isEmpty()) {
                continue;
            }
            YamlBlock existing = find(target, candidate.path());
            if (existing == null || !comments(target.subList(existing.start(), existing.keyLine())).isEmpty()) {
                continue;
            }
            target.addAll(existing.keyLine(), reindent(sourceComments, candidate.indent(), existing.indent()));
            commented.add(candidate.dotted());
        }
        return commented;
    }

    private static List<String> comments(List<String> lines) {
        List<String> comments = new ArrayList<>();
        for (String line : lines) {
            if (isComment(line)) {
                comments.add(line);
            }
        }
        return comments;
    }

    /** Where a new child of {@code parent} goes: the end of the parent's block, at the indentation its children use. */
    @Nullable
    private static Place insertionPlace(List<String> lines, List<String> parent) {
        if (parent.isEmpty()) {
            return new Place(lines.size(), 0);
        }
        YamlBlock block = find(lines, parent);
        if (block == null) {
            return null;
        }
        Integer child = childIndent(lines, block);
        return new Place(block.end(), child != null ? child : block.indent() + 2);
    }

    @Nullable
    private static YamlBlock find(List<String> lines, List<String> path) {
        YamlBlock found = null;
        for (String key : path) {
            List<YamlBlock> candidates;
            if (found == null) {
                candidates = blocks(lines, 0, lines.size(), 0);
            } else {
                Integer indent = childIndent(lines, found);
                if (indent == null) {
                    return null;
                }
                candidates = blocks(lines, found.keyLine() + 1, found.end(), indent);
            }
            found = firstWithKey(candidates, key);
            if (found == null) {
                return null;
            }
        }
        return found;
    }

    private static YamlBlock firstWithKey(List<YamlBlock> blocks, String key) {
        for (YamlBlock block : blocks) {
            if (block.key().equals(key)) {
                return block;
            }
        }
        return null;
    }

    private static List<YamlBlock> collect(List<String> lines, int start, int end, int indent, List<String> parent) {
        List<YamlBlock> result = new ArrayList<>();
        for (YamlBlock block : blocks(lines, start, end, indent)) {
            YamlBlock located = block.withPath(copyWith(parent, block.key()));
            result.add(located);
            Integer child = childIndent(lines, block);
            if (child != null) {
                result.addAll(collect(lines, block.keyLine() + 1, block.end(), child, located.path()));
            }
        }
        return result;
    }

    private static List<String> copyWith(List<String> parent, String key) {
        List<String> path = new ArrayList<>(parent);
        path.add(key);
        return path;
    }

    @Nullable
    private static Integer childIndent(List<String> lines, YamlBlock parent) {
        for (int index = parent.keyLine() + 1; index < parent.end(); index++) {
            String line = lines.get(index);
            if (!YamlText.isBlank(line) && !isComment(line)) {
                int indent = YamlText.indentOf(line);
                return indent > parent.indent() ? Integer.valueOf(indent) : null;
            }
        }
        return null;
    }

    private static List<YamlBlock> blocks(List<String> lines, int start, int end, int indent) {
        List<Integer> keyLines = new ArrayList<>();
        List<String> keys = new ArrayList<>();
        for (int index = start; index < end; index++) {
            String key = key(lines.get(index), indent);
            if (key != null) {
                keyLines.add(index);
                keys.add(key);
            }
        }
        List<YamlBlock> blocks = new ArrayList<>();
        for (int position = 0; position < keyLines.size(); position++) {
            int keyLine = keyLines.get(position);
            int decoratedStart = decoratedStart(lines, keyLine, start);
            int next = position + 1 < keyLines.size() ? decoratedStart(lines, keyLines.get(position + 1), keyLine + 1) : end;
            blocks.add(new YamlBlock(keys.get(position), indent, decoratedStart, keyLine, next, Collections.emptyList()));
        }
        return blocks;
    }

    private static int decoratedStart(List<String> lines, int keyLine, int lowerBound) {
        int result = keyLine;
        while (result > lowerBound) {
            String previous = YamlText.trim(lines.get(result - 1));
            if (previous.isEmpty() || previous.startsWith("#")) {
                result--;
            } else {
                break;
            }
        }
        return result;
    }

    @Nullable
    private static String key(String line, int indent) {
        if (YamlText.indentOf(line) != indent) {
            return null;
        }
        String trimmed = YamlText.trim(line);
        if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("-")) {
            return null;
        }
        int colon = trimmed.indexOf(':');
        if (colon <= 0) {
            return null;
        }
        String key = YamlText.trimChars(YamlText.trim(trimmed.substring(0, colon)), '"', '\'');
        return key.isEmpty() ? null : key;
    }

    private static List<String> reindent(List<String> lines, int from, int to) {
        if (from == to) {
            return new ArrayList<>(lines);
        }
        List<String> result = new ArrayList<>();
        for (String line : lines) {
            if (YamlText.isBlank(line)) {
                result.add(line);
            } else {
                result.add(YamlText.spaces(to) + line.substring(Math.min(from, YamlText.indentOf(line))));
            }
        }
        return result;
    }

    private static boolean isComment(String line) {
        return YamlText.trimStart(line).startsWith("#");
    }
}
