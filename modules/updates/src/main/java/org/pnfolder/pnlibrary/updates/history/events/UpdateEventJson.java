package org.pnfolder.pnlibrary.updates.history.events;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.updates.product.ProductId;
import org.pnfolder.pnlibrary.api.updates.state.UpdateActor;
import org.pnfolder.pnlibrary.api.updates.state.UpdateState;
import org.pnfolder.pnlibrary.api.version.SemanticVersion;

/** The file format of journal entries: one JSON object per entry. */
final class UpdateEventJson {
    private UpdateEventJson() {
    }

    @NotNull
    static JsonObject write(@NotNull UpdateEvent event) {
        UpdateActor actor = event.getActor();
        JsonObject json = new JsonObject();
        json.addProperty("schema", 1);
        json.addProperty("kind", "STATE_OBSERVED");
        json.addProperty("id", event.getId().toString());
        json.addProperty("occurredAt", event.getOccurredAt().toString());
        json.addProperty("planId", event.getPlanId().toString());
        json.addProperty("revision", event.getRevision());
        json.addProperty("state", event.getState());
        json.addProperty("operation", event.getOperation());
        json.addProperty("message", event.getMessage());
        JsonObject actorNode = new JsonObject();
        actorNode.addProperty("kind", actor.getKind().name());
        actorNode.addProperty("id", actor.getId());
        actorNode.addProperty("name", actor.getName());
        json.add("actor", actorNode);
        JsonArray changes = new JsonArray();
        for (UpdateEventChange change : event.getChanges()) {
            JsonObject node = new JsonObject();
            node.addProperty("product", change.getProduct());
            node.addProperty("from", change.getFrom());
            node.addProperty("to", change.getTo());
            changes.add(node);
        }
        json.add("changes", changes);
        return json;
    }

    /** Reads and validates one entry; throws when the content is not a well-formed entry. */
    @NotNull
    static UpdateEvent read(@NotNull JsonObject json, @NotNull Set<String> operations) {
        require(json.get("schema").getAsInt() == 1 && json.get("kind").getAsString().equals("STATE_OBSERVED"),
            "Unsupported event schema");
        List<UpdateEventChange> changes = new ArrayList<>();
        for (JsonElement element : json.getAsJsonArray("changes")) {
            JsonObject change = element.getAsJsonObject();
            changes.add(new UpdateEventChange(change.get("product").getAsString(), text(change.get("from")),
                change.get("to").getAsString()));
        }
        JsonObject actorNode = json.getAsJsonObject("actor");
        UpdateActor actor = actorNode == null ? UpdateActor.UNKNOWN
            : new UpdateActor(UpdateActor.Kind.valueOf(actorNode.get("kind").getAsString()),
            actorNode.get("id").getAsString(), actorNode.get("name").getAsString());
        JsonElement operationNode = json.get("operation");
        UpdateEvent event = new UpdateEvent(UUID.fromString(json.get("id").getAsString()),
            Instant.parse(json.get("occurredAt").getAsString()), UUID.fromString(json.get("planId").getAsString()),
            json.get("revision").getAsLong(), json.get("state").getAsString(), changes, actor,
            operationNode == null ? "STATE_OBSERVED" : operationNode.getAsString(), text(json.get("message")));
        validate(event, operations);
        return event;
    }

    private static void validate(UpdateEvent event, Set<String> operations) {
        require(event.getRevision() >= 0, "Invalid event revision");
        UpdateState.valueOf(event.getState());
        require(operations.contains(event.getOperation()), "Invalid update operation");
        for (UpdateEventChange change : event.getChanges()) {
            ProductId.of(change.getProduct());
            if (change.getFrom() != null) {
                SemanticVersion.parse(change.getFrom());
            }
            SemanticVersion.parse(change.getTo());
        }
    }

    @Nullable
    private static String text(@Nullable JsonElement element) {
        return element == null || element.isJsonNull() ? null : element.getAsString();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
