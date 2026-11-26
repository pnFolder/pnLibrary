package org.pnfolder.pnlibrary.updates.engine.catalog;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.net.URI;

/** Strict typed access to the fields of a release-catalog JSON object. */
final class CatalogJson {
    private CatalogJson() {
    }

    static ManifestException fail(String field, String message) {
        return new ManifestException(field, message);
    }

    static ManifestException fail(String field, String message, Throwable cause) {
        return new ManifestException(field, message, cause);
    }

    static String requiredString(JsonObject object, String name) {
        JsonElement value = object.get(name);
        if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
            String text = value.getAsString();
            if (!text.trim().isEmpty()) {
                return text;
            }
        }
        throw fail(name, "expected non-blank string");
    }

    static String optionalString(JsonObject object, String name) {
        if (!object.has(name)) {
            return null;
        }
        JsonElement value = object.get(name);
        if (value.isJsonNull()) {
            throw fail(name, "null is not allowed; omit the field");
        }
        String text = value.getAsString();
        if (text.trim().isEmpty()) {
            throw fail(name, "expected non-blank string");
        }
        return text;
    }

    static int requiredInt(JsonObject object, String name) {
        try {
            JsonElement value = object.get(name);
            if (value == null) {
                throw fail(name, "expected integer");
            }
            return value.getAsInt();
        } catch (ManifestException error) {
            throw error;
        } catch (Exception error) {
            throw fail(name, "expected integer", error);
        }
    }

    static Integer optionalInt(JsonObject object, String name) {
        if (!object.has(name)) {
            return null;
        }
        JsonElement value = object.get(name);
        if (value.isJsonNull()) {
            throw fail(name, "null is not allowed; omit the field");
        }
        try {
            return value.getAsInt();
        } catch (Exception error) {
            throw fail(name, "expected integer", error);
        }
    }

    static Long optionalLong(JsonObject object, String name) {
        if (!object.has(name)) {
            return null;
        }
        JsonElement value = object.get(name);
        if (value.isJsonNull()) {
            throw fail(name, "null is not allowed; omit the field");
        }
        try {
            return value.getAsLong();
        } catch (Exception error) {
            throw fail(name, "expected integer", error);
        }
    }

    static JsonObject requiredObject(JsonObject object, String name) {
        JsonElement value = object.get(name);
        if (value == null || !value.isJsonObject()) {
            throw fail(name, "expected object");
        }
        return value.getAsJsonObject();
    }

    static JsonArray requiredArray(JsonObject object, String name) {
        JsonElement value = object.get(name);
        if (value == null || !value.isJsonArray()) {
            throw fail(name, "expected array");
        }
        return value.getAsJsonArray();
    }

    static VersionRangeText optionalRange(JsonObject object, String name) {
        if (!object.has(name)) {
            return null;
        }
        JsonElement value = object.get(name);
        if (value.isJsonNull() || !value.isJsonObject()) {
            throw fail(name, "expected object or omitted field");
        }
        JsonObject range = value.getAsJsonObject();
        return new VersionRangeText(requiredString(range, "minimum"), optionalString(range, "maximum"));
    }

    static URI optionalHttpsUrl(JsonObject object, String name) {
        if (!object.has(name)) {
            return null;
        }
        JsonElement value = object.get(name);
        if (value.isJsonNull() || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw fail(name, "expected HTTPS URL or omitted field");
        }
        URI uri;
        try {
            uri = URI.create(value.getAsString());
        } catch (Exception error) {
            throw fail(name, "invalid URL", error);
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getHost().trim().isEmpty()) {
            throw new IllegalArgumentException(name + " must use HTTPS");
        }
        return uri;
    }
}
