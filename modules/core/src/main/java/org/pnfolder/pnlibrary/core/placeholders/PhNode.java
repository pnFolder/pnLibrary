package org.pnfolder.pnlibrary.core.placeholders;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import org.pnfolder.pnlibrary.api.registry.Visibility;

/** One branch of the tree. A node with a value is a placeholder; a node without one only groups. */
final class PhNode {
    private final PhPart part;
    private final Class<?> valueType;
    private final List<PhNode> children = new ArrayList<PhNode>();
    private Function<Call, CompletionStage<Object>> value;
    private String format;
    private String description = "";
    private Visibility visibility = Visibility.ALL;

    PhNode(PhPart part, Class<?> valueType) {
        this.part = part;
        this.valueType = valueType;
    }

    PhPart getPart() {
        return part;
    }

    Class<?> getValueType() {
        return valueType;
    }

    List<PhNode> getChildren() {
        return children;
    }

    Function<Call, CompletionStage<Object>> getValue() {
        return value;
    }

    void setValue(Function<Call, CompletionStage<Object>> value) {
        this.value = value;
    }

    String getFormat() {
        return format;
    }

    void setFormat(String format) {
        this.format = format;
    }

    String getDescription() {
        return description;
    }

    void setDescription(String description) {
        this.description = description;
    }

    /** Who may use this placeholder; set on the node that holds the value. */
    Visibility getVisibility() {
        return visibility;
    }

    void setVisibility(Visibility visibility) {
        this.visibility = visibility;
    }

    /** Chains {@code nodes} so that each one is the child of the one before. */
    static void link(List<PhNode> nodes) {
        for (int i = 0; i + 1 < nodes.size(); i++) {
            nodes.get(i).children.add(nodes.get(i + 1));
        }
    }
}
