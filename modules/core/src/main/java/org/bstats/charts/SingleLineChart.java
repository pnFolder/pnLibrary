package org.bstats.charts;

import org.bstats.json.JsonObjectBuilder;

import java.util.concurrent.Callable;

public class SingleLineChart extends CustomChart {

    private final Callable<Integer> callable;

    public SingleLineChart(String chartId, Callable<Integer> callable) {
        super(chartId);
        this.callable = callable;
    }

    @Override
    protected JsonObjectBuilder.JsonObject getChartData() throws Exception {
        int value = callable.call();
        if (value == 0) {
            // Null = skip the chart
            return null;
        }
        return new JsonObjectBuilder()
                .appendField("value", value)
                .build();
    }

}