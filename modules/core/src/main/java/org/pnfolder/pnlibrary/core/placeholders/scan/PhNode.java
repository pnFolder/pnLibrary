package org.pnfolder.pnlibrary.core.placeholders.scan;

import org.pnfolder.pnlibrary.core.placeholders.Call;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import org.pnfolder.pnlibrary.api.registry.Visibility;

/** One branch of the tree. A node with a value is a placeholder; a node without one only groups. */
public final class PhNode {
    private final PhPart part;
    private final Class<?> valueType;
    private final List<PhNode> children = new ArrayList<>();
    private Function<Call, CompletionStage<Object>> value;
    private String format;
    private String description = "";
    private Visibility visibility = Visibility.ALL;

    public PhNode(PhPart part, Class<?> valueType) {
        this.part = part;
        this.valueType = valueType;
    }

    public PhPart getPart() {
        return part;
    }

    public Class<?> getValueType() {
        return valueType;
    }

    public List<PhNode> getChildren() {
        return children;
    }

    public Function<Call, CompletionStage<Object>> getValue() {
        return value;
    }

    public void setValue(Function<Call, CompletionStage<Object>> value) {
        this.value = value;
    }

    public String getFormat() {
        return format;
    }

    void setFormat(String format) {
        this.format = format;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    /** Who may use this placeholder; set on the node that holds the value. */
    public Visibility getVisibility() {
        return visibility;
    }

    public void setVisibility(Visibility visibility) {
        this.visibility = visibility;
    }

    /** Chains {@code nodes} so that each one is the child of the one before. */
    public static void link(List<PhNode> nodes) {
        for (int i = 0; i + 1 < nodes.size(); i++) {
            nodes.get(i).children.add(nodes.get(i + 1));
        }
    }
}
