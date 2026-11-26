package org.pnfolder.pnlibrary.core.observability.report;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Collects a compact JVM and host snapshot used to diagnose common server failures. */
public final class SystemReportCollector {
    private static final double HIGH_HEAP_RATIO = 0.90;
    private static final double HIGH_LOAD_PER_PROCESSOR = 1.0;

    /** The JVM, OS, memory, thread, storage and health sections of the report. */
    @NotNull
    public Map<String, Object> collect() {
        OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
        MemoryUsage heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        List<Long> deadlockedIds = deadlockedThreadIds();
        Double loadPerProcessor = os.getSystemLoadAverage() >= 0
                ? Double.valueOf(os.getSystemLoadAverage() / Math.max(os.getAvailableProcessors(), 1)) : null;
        List<Map<String, Object>> signals = healthSignals(ratio(heap.getUsed(), heap.getMax()),
                deadlockedIds.size(), loadPerProcessor);
        Map<String, Object> health = new LinkedHashMap<>();
        health.put("status", signals.isEmpty() ? "healthy" : "attention");
        health.put("signals", signals);

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("generatedUtc", Instant.now().toString());
        report.put("java", java());
        report.put("os", os(loadPerProcessor));
        report.put("memory", memory());
        report.put("threads", threads(deadlockedIds));
        report.put("storage", storage());
        report.put("health", health);
        return report;
    }

    /** Every live thread with its state and stack, as plain text. */
    @NotNull
    public String threadDump() {
        ThreadMXBean bean = ManagementFactory.getThreadMXBean();
        StringBuilder dump = new StringBuilder();
        boolean first = true;
        for (ThreadInfo info : bean.dumpAllThreads(true, true)) {
            if (!first) {
                dump.append("\n\n");
            }
            first = false;
            dump.append(threadBlock(info));
        }
        return dump.toString();
    }

    private static String threadBlock(ThreadInfo info) {
        StringBuilder block = new StringBuilder();
        block.append('"').append(info.getThreadName()).append('"');
        block.append(" id=").append(info.getThreadId());
        block.append(' ').append(info.getThreadState()).append('\n');
        for (StackTraceElement frame : info.getStackTrace()) {
            block.append("    at ").append(frame).append('\n');
        }
        return block.toString().replaceAll("\\s+$", "");
    }

    private static List<Long> deadlockedThreadIds() {
        long[] ids = ManagementFactory.getThreadMXBean().findDeadlockedThreads();
        List<Long> result = new ArrayList<>();
        if (ids != null) {
            for (long id : ids) {
                result.add(id);
            }
        }
        return result;
    }

    private static List<Map<String, Object>> healthSignals(Double heapRatio, int deadlocked, Double loadPerProcessor) {
        List<Map<String, Object>> signals = new ArrayList<>();
        if (heapRatio != null && heapRatio >= HIGH_HEAP_RATIO) {
            signals.add(signal("highHeapUsage", "usedRatio", heapRatio));
        }
        if (deadlocked > 0) {
            signals.add(signal("deadlockedThreads", "count", deadlocked));
        }
        if (loadPerProcessor != null && loadPerProcessor >= HIGH_LOAD_PER_PROCESSOR) {
            signals.add(signal("highSystemLoad", "loadPerProcessor", loadPerProcessor));
        }
        return signals;
    }

    private static Map<String, Object> signal(String code, String field, Object value) {
        Map<String, Object> signal = new LinkedHashMap<>();
        signal.put("code", code);
        signal.put(field, value);
        return signal;
    }

    private static Map<String, Object> java() {
        RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
        Map<String, Object> java = new LinkedHashMap<>();
        java.put("version", System.getProperty("java.version", "unknown"));
        java.put("vendor", System.getProperty("java.vendor", "unknown"));
        java.put("vmName", runtime.getVmName());
        java.put("vmVersion", runtime.getVmVersion());
        java.put("uptimeSeconds", runtime.getUptime() / 1_000);
        java.put("startTimeUtc", Instant.ofEpochMilli(runtime.getStartTime()).toString());
        return java;
    }

    private static Map<String, Object> os(Double loadPerProcessor) {
        OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", os.getName());
        result.put("version", os.getVersion());
        result.put("arch", os.getArch());
        result.put("availableProcessors", os.getAvailableProcessors());
        result.put("systemLoadAverage", os.getSystemLoadAverage() >= 0 ? Double.valueOf(os.getSystemLoadAverage()) : null);
        result.put("loadPerProcessor", loadPerProcessor);
        return result;
    }

    private static Map<String, Object> memory() {
        MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
        MemoryUsage heap = memory.getHeapMemoryUsage();
        MemoryUsage nonHeap = memory.getNonHeapMemoryUsage();
        List<Map<String, Object>> collectors = new ArrayList<>();
        for (GarbageCollectorMXBean collector : ManagementFactory.getGarbageCollectorMXBeans()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("name", collector.getName());
            entry.put("collections", collector.getCollectionCount());
            entry.put("collectionTimeMs", collector.getCollectionTime());
            collectors.add(entry);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("heap", memoryUsage(heap));
        result.put("nonHeap", memoryUsage(nonHeap));
        result.put("garbageCollectors", collectors);
        return result;
    }

    private static Map<String, Object> threads(List<Long> deadlockedIds) {
        ThreadMXBean threads = ManagementFactory.getThreadMXBean();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("count", threads.getThreadCount());
        result.put("peakCount", threads.getPeakThreadCount());
        result.put("daemonCount", threads.getDaemonThreadCount());
        result.put("deadlockedCount", deadlockedIds.size());
        result.put("deadlockedThreadIds", deadlockedIds);
        return result;
    }

    private static List<Map<String, Object>> storage() {
        List<Map<String, Object>> roots = new ArrayList<>();
        File[] listed = File.listRoots();
        for (File root : listed == null ? new File[0] : listed) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("root", root.getPath());
            entry.put("totalBytes", root.getTotalSpace());
            entry.put("usableBytes", root.getUsableSpace());
            roots.add(entry);
        }
        return roots;
    }

    private static Map<String, Object> memoryUsage(MemoryUsage usage) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("initBytes", usage.getInit() >= 0 ? Long.valueOf(usage.getInit()) : null);
        result.put("usedBytes", usage.getUsed());
        result.put("committedBytes", usage.getCommitted());
        result.put("maxBytes", usage.getMax() >= 0 ? Long.valueOf(usage.getMax()) : null);
        result.put("usedRatio", ratio(usage.getUsed(), usage.getMax()));
        return result;
    }

    @Nullable
    private static Double ratio(long used, long max) {
        return max > 0 ? Double.valueOf((double) used / max) : null;
    }
}
