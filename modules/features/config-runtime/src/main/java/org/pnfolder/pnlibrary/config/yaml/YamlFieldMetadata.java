package org.pnfolder.pnlibrary.config.yaml;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.jetbrains.annotations.NotNull;

/** Metadata used to place documentation around one generated YAML field. */
final class YamlFieldMetadata {
    private final List<String> comments;
    private final boolean separateWithBlankLine;
    private final Map<String, YamlFieldMetadata> children;

    /**
     * Creates the metadata of one field.
     *
     * @param comments comment lines written above the field
     * @param separateWithBlankLine whether an empty line precedes the comments
     * @param children metadata of the nested fields, by YAML key
     */
    YamlFieldMetadata(@NotNull List<String> comments, boolean separateWithBlankLine,
                      @NotNull Map<String, YamlFieldMetadata> children) {
        this.comments = comments;
        this.separateWithBlankLine = separateWithBlankLine;
        this.children = children;
    }

    @NotNull
    List<String> getComments() {
        return comments;
    }

    boolean isSeparateWithBlankLine() {
        return separateWithBlankLine;
    }

    @NotNull
    Map<String, YamlFieldMetadata> getChildren() {
        return children;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof YamlFieldMetadata)) {
            return false;
        }
        YamlFieldMetadata that = (YamlFieldMetadata) other;
        return separateWithBlankLine == that.separateWithBlankLine && comments.equals(that.comments)
                && children.equals(that.children);
    }

    @Override
    public int hashCode() {
        return Objects.hash(comments, separateWithBlankLine, children);
    }
}
