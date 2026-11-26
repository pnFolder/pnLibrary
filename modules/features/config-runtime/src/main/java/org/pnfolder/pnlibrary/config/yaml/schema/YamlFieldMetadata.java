package org.pnfolder.pnlibrary.config.yaml.schema;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.jetbrains.annotations.NotNull;

/** Metadata used to place documentation around one generated YAML field. */
public final class YamlFieldMetadata {
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
    public YamlFieldMetadata(@NotNull List<String> comments, boolean separateWithBlankLine,
                      @NotNull Map<String, YamlFieldMetadata> children) {
        this.comments = Objects.requireNonNull(comments, "comments");
        this.separateWithBlankLine = separateWithBlankLine;
        this.children = Objects.requireNonNull(children, "children");
    }

    @NotNull
    public List<String> getComments() {
        return comments;
    }

    public boolean isSeparateWithBlankLine() {
        return separateWithBlankLine;
    }

    @NotNull
    public Map<String, YamlFieldMetadata> getChildren() {
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
