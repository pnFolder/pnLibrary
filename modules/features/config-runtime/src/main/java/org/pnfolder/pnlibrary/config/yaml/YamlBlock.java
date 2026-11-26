package org.pnfolder.pnlibrary.config.yaml;

import java.util.ArrayList;
import java.util.List;

/** One key of a YAML text with the comment and blank lines that decorate it and the lines it owns. */
final class YamlBlock {
    private final String key;
    private final int indent;
    private final int start;
    private final int keyLine;
    private final int end;
    private final List<String> path;

    YamlBlock(String key, int indent, int start, int keyLine, int end, List<String> path) {
        this.key = key;
        this.indent = indent;
        this.start = start;
        this.keyLine = keyLine;
        this.end = end;
        this.path = path;
    }

    String key() {
        return key;
    }

    int indent() {
        return indent;
    }

    /** First line of the block, including the comments above its key. */
    int start() {
        return start;
    }

    int keyLine() {
        return keyLine;
    }

    /** Line after the last line of the block. */
    int end() {
        return end;
    }

    List<String> path() {
        return path;
    }

    YamlBlock withPath(List<String> newPath) {
        return new YamlBlock(key, indent, start, keyLine, end, newPath);
    }

    String dotted() {
        return String.join(".", path);
    }

    List<String> childPath(String child) {
        List<String> result = new ArrayList<String>(path);
        result.add(child);
        return result;
    }
}
