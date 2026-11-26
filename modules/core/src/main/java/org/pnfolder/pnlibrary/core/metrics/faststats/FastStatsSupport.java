package org.pnfolder.pnlibrary.core.metrics.faststats;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.faststats.Metrics;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.core.metrics.ChartIds;
import org.pnfolder.pnlibrary.internal.faststats.FastStatsBridge;

import java.util.Map;
import java.util.function.Supplier;

/** Shared, platform-neutral FastStats helpers used by every platform adapter. */
public final class FastStatsSupport {
    private FastStatsSupport() {
    }

    /** Publishes a category; a missing one is reported as {@code unknown}. */
    public static void simplePie(@NotNull Metrics.Factory factory, @NotNull String id, @NotNull Supplier<String> value) {
        factory.addMetric(FastStatsBridge.string(ChartIds.validChartId(id), () -> {
            String category = value.get();
            return category == null ? "unknown" : category;
        }));
    }

    /** Publishes name-to-count pairs; FastStats represents pies, lines and simple bars identically. */
    public static void numberMap(@NotNull Metrics.Factory factory, @NotNull String id,
                                 @NotNull Supplier<Map<String, Integer>> values) {
        factory.addMetric(FastStatsBridge.numberMap(ChartIds.validChartId(id), values::get));
    }

    /** Publishes group-to-category-to-count pairs as a JSON object of objects. */
    public static void drilldownPie(@NotNull Metrics.Factory factory, @NotNull String id,
                                    @NotNull Supplier<Map<String, Map<String, Integer>>> values) {
        factory.addMetric(FastStatsBridge.object(ChartIds.validChartId(id), () -> {
            JsonObject root = new JsonObject();
            for (Map.Entry<String, Map<String, Integer>> group : values.get().entrySet()) {
                JsonObject child = new JsonObject();
                for (Map.Entry<String, Integer> entry : group.getValue().entrySet()) {
                    child.addProperty(entry.getKey(), entry.getValue());
                }
                root.add(group.getKey(), child);
            }
            return root;
        }));
    }

    /** Publishes one number. */
    public static void singleLineChart(@NotNull Metrics.Factory factory, @NotNull String id,
                                       @NotNull Supplier<Integer> value) {
        factory.addMetric(FastStatsBridge.number(ChartIds.validChartId(id), value::get));
    }

    /** Publishes name-to-numbers pairs as a JSON object of arrays. */
    public static void advancedBarChart(@NotNull Metrics.Factory factory, @NotNull String id,
                                        @NotNull Supplier<Map<String, int[]>> values) {
        factory.addMetric(FastStatsBridge.object(ChartIds.validChartId(id), () -> {
            JsonObject root = new JsonObject();
            for (Map.Entry<String, int[]> entry : values.get().entrySet()) {
                JsonArray array = new JsonArray();
                for (int number : entry.getValue()) {
                    array.add(number);
                }
                root.add(entry.getKey(), array);
            }
            return root;
        }));
    }
}
