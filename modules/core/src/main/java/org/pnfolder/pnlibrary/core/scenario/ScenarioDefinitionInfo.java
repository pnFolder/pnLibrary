package org.pnfolder.pnlibrary.core.scenario;

import java.util.Objects;
import org.jetbrains.annotations.NotNull;

/** One named action, requirement or function as an administrator sees it. {@code kind} is {@code action}, {@code requirement} or {@code function}. */
public final class ScenarioDefinitionInfo {
    private final String kind;
    private final String name;
    private final String owner;
    private final String visibility;

    public ScenarioDefinitionInfo(@NotNull String kind, @NotNull String name, @NotNull String owner, @NotNull String visibility) {
        this.kind = kind;
        this.name = name;
        this.owner = owner;
        this.visibility = visibility;
    }

    public @NotNull String getKind() {
        return kind;
    }

    public @NotNull String getName() {
        return name;
    }

    public @NotNull String getOwner() {
        return owner;
    }

    public @NotNull String getVisibility() {
        return visibility;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ScenarioDefinitionInfo)) {
            return false;
        }
        ScenarioDefinitionInfo that = (ScenarioDefinitionInfo) other;
        return kind.equals(that.kind) && name.equals(that.name) && owner.equals(that.owner)
            && visibility.equals(that.visibility);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, name, owner, visibility);
    }

    @Override
    public String toString() {
        return "ScenarioDefinitionInfo(kind=" + kind + ", name=" + name + ", owner=" + owner + ", visibility=" + visibility + ")";
    }
}
