package org.pnfolder.pnlibrary.localization.internal;

import com.google.gson.JsonObject;

import java.util.Map;

/** Parsed asset metadata of one Minecraft version. */
final class VersionAssets {
    private final JsonObject metadata;
    private final Map<String, AssetReference> objects;

    VersionAssets(JsonObject metadata, Map<String, AssetReference> objects) {
        this.metadata = metadata;
        this.objects = objects;
    }

    JsonObject getMetadata() {
        return metadata;
    }

    Map<String, AssetReference> getObjects() {
        return objects;
    }
}
