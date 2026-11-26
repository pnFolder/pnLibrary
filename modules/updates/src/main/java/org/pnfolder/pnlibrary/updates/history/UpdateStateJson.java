package org.pnfolder.pnlibrary.updates.history;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.updates.product.ProductId;
import org.pnfolder.pnlibrary.api.updates.state.BlockedReason;
import org.pnfolder.pnlibrary.api.updates.state.UpdatePlanSnapshot;
import org.pnfolder.pnlibrary.api.version.ApiVersionRange;
import org.pnfolder.pnlibrary.api.version.SemanticVersion;

/** The file format of a saved plan snapshot: its fields and the structured reasons that block it. */
final class UpdateStateJson {
    private UpdateStateJson() {
    }

    @NotNull
    static String encode(@NotNull UpdatePlanSnapshot snapshot, @NotNull Gson gson) {
        JsonObject json = new JsonObject();
        json.addProperty("schema", 1);
        json.addProperty("id", snapshot.getId().toString());
        json.addProperty("revision", snapshot.getRevision());
        json.addProperty("state", snapshot.getState().name());
        json.addProperty("message", snapshot.getMessage());
        json.add("plan", gson.toJsonTree(snapshot.getPlan()));
        JsonArray blockers = new JsonArray();
        for (BlockedReason reason : snapshot.getBlockers()) {
            blockers.add(encodeBlocker(reason));
        }
        json.add("blockers", blockers);
        return json.toString();
    }

    private static JsonObject encodeBlocker(BlockedReason reason) {
        JsonObject json = new JsonObject();
        if (reason instanceof BlockedReason.ApiMismatch) {
            BlockedReason.ApiMismatch mismatch = (BlockedReason.ApiMismatch) reason;
            json.addProperty("type", "api-mismatch");
            json.addProperty("plugin", mismatch.getProduct().getValue());
            json.addProperty("minimumApi", mismatch.getSupportedApi().getMinimum());
            json.addProperty("maximumApi", mismatch.getSupportedApi().getMaximum());
            json.addProperty("requiredApi", mismatch.getRequiredApi());
            if (mismatch.getRepository() != null) {
                json.addProperty("repository", mismatch.getRepository());
            }
        } else if (reason instanceof BlockedReason.MissingDependency) {
            BlockedReason.MissingDependency missing = (BlockedReason.MissingDependency) reason;
            json.addProperty("type", "missing-dependency");
            json.addProperty("plugin", missing.getProduct().getValue());
            json.addProperty("dependency", missing.getDependency().getValue());
            json.addProperty("minimumVersion", missing.getMinimumVersion().toString());
        } else if (reason instanceof BlockedReason.MissingExternalPluginDependency) {
            BlockedReason.MissingExternalPluginDependency missing =
                (BlockedReason.MissingExternalPluginDependency) reason;
            json.addProperty("type", "missing-external-plugin");
            json.addProperty("plugin", missing.getProduct().getValue());
            json.addProperty("dependency", missing.getPlugin());
            json.addProperty("minimumVersion", missing.getMinimumVersion().toString());
            if (missing.getDownloadPage() != null) {
                json.addProperty("downloadPage", missing.getDownloadPage());
            }
        } else if (reason instanceof BlockedReason.Frozen) {
            json.addProperty("type", "frozen");
            json.addProperty("plugin", ((BlockedReason.Frozen) reason).getProduct().getValue());
        } else if (reason instanceof BlockedReason.NoCompatibleRelease) {
            BlockedReason.NoCompatibleRelease none = (BlockedReason.NoCompatibleRelease) reason;
            json.addProperty("type", "no-compatible-release");
            json.addProperty("plugin", none.getProduct().getValue());
            json.addProperty("requiredApi", none.getRequiredApi());
        }
        return json;
    }

    @NotNull
    static List<BlockedReason> decodeBlockers(@NotNull JsonObject root) {
        List<BlockedReason> result = new ArrayList<>();
        JsonArray blockers = root.getAsJsonArray("blockers");
        if (blockers == null) {
            return result;
        }
        for (JsonElement element : blockers) {
            result.add(decodeBlocker(element.getAsJsonObject()));
        }
        return result;
    }

    private static BlockedReason decodeBlocker(JsonObject item) {
        ProductId product = ProductId.of(item.get("plugin").getAsString());
        String type = item.get("type").getAsString();
        switch (type) {
            case "api-mismatch":
                return new BlockedReason.ApiMismatch(product,
                    new ApiVersionRange(item.get("minimumApi").getAsInt(), item.get("maximumApi").getAsInt()),
                    item.get("requiredApi").getAsInt(), text(item, "repository"));
            case "missing-dependency":
                return new BlockedReason.MissingDependency(product, ProductId.of(item.get("dependency").getAsString()),
                    SemanticVersion.parse(item.get("minimumVersion").getAsString()));
            case "missing-external-plugin":
                return new BlockedReason.MissingExternalPluginDependency(product, item.get("dependency").getAsString(),
                    SemanticVersion.parse(item.get("minimumVersion").getAsString()), text(item, "downloadPage"));
            case "frozen":
                return new BlockedReason.Frozen(product);
            case "no-compatible-release":
                return new BlockedReason.NoCompatibleRelease(product, item.get("requiredApi").getAsInt());
            default:
                throw new IllegalStateException("Unknown update blocker type: " + type);
        }
    }

    @Nullable
    private static String text(JsonObject item, String name) {
        JsonElement element = item.get(name);
        return element == null ? null : element.getAsString();
    }
}
