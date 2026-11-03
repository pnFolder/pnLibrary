package org.pnfolder.pnlibrary.core.observability.report

import java.io.File
import java.lang.management.ManagementFactory
import java.time.Instant

/** Collects a compact JVM and host snapshot used to diagnose common server failures. */
internal class SystemReportCollector {
    fun collect(): Map<String, Any?> {
        val os = ManagementFactory.getOperatingSystemMXBean()
        val heap = ManagementFactory.getMemoryMXBean().heapMemoryUsage
        val deadlockedIds = ManagementFactory.getThreadMXBean().findDeadlockedThreads()?.toList().orEmpty()
        val loadPerProcessor = os.systemLoadAverage
            .takeIf { it >= 0 }
            ?.div(os.availableProcessors.coerceAtLeast(1))
        val signals = healthSignals(ratio(heap.used, heap.max), deadlockedIds.size, loadPerProcessor)

        return linkedMapOf(
            "generatedUtc" to Instant.now().toString(),
            "java" to java(),
            "os" to os(loadPerProcessor),
            "memory" to memory(),
            "threads" to threads(deadlockedIds),
            "storage" to File.listRoots().map { root ->
                linkedMapOf("root" to root.path, "totalBytes" to root.totalSpace, "usableBytes" to root.usableSpace)
            },
            "health" to linkedMapOf("status" to if (signals.isEmpty()) "healthy" else "attention", "signals" to signals),
        )
    }

    private fun healthSignals(heapRatio: Double?, deadlocked: Int, loadPerProcessor: Double?) = buildList<Map<String, Any?>> {
        if (heapRatio != null && heapRatio >= HIGH_HEAP_RATIO) {
            add(linkedMapOf("code" to "highHeapUsage", "usedRatio" to heapRatio))
        }
        if (deadlocked > 0) add(linkedMapOf("code" to "deadlockedThreads", "count" to deadlocked))
        if (loadPerProcessor != null && loadPerProcessor >= HIGH_LOAD_PER_PROCESSOR) {
            add(linkedMapOf("code" to "highSystemLoad", "loadPerProcessor" to loadPerProcessor))
        }
    }

    private fun java(): Map<String, Any?> {
        val runtime = ManagementFactory.getRuntimeMXBean()
        return linkedMapOf(
            "version" to System.getProperty("java.version", "unknown"),
            "vendor" to System.getProperty("java.vendor", "unknown"),
            "vmName" to runtime.vmName,
            "vmVersion" to runtime.vmVersion,
            "uptimeSeconds" to runtime.uptime / 1_000,
            "startTimeUtc" to Instant.ofEpochMilli(runtime.startTime).toString(),
        )
    }

    private fun os(loadPerProcessor: Double?): Map<String, Any?> {
        val os = ManagementFactory.getOperatingSystemMXBean()
        return linkedMapOf(
            "name" to os.name,
            "version" to os.version,
            "arch" to os.arch,
            "availableProcessors" to os.availableProcessors,
            "systemLoadAverage" to os.systemLoadAverage.takeIf { it >= 0 },
            "loadPerProcessor" to loadPerProcessor,
        )
    }

    private fun memory(): Map<String, Any?> {
        val memory = ManagementFactory.getMemoryMXBean()
        val heap = memory.heapMemoryUsage
        val nonHeap = memory.nonHeapMemoryUsage
        return linkedMapOf(
            "heap" to memoryUsage(heap.init, heap.used, heap.committed, heap.max),
            "nonHeap" to memoryUsage(nonHeap.init, nonHeap.used, nonHeap.committed, nonHeap.max),
            "garbageCollectors" to ManagementFactory.getGarbageCollectorMXBeans().map { collector ->
                linkedMapOf(
                    "name" to collector.name,
                    "collections" to collector.collectionCount,
                    "collectionTimeMs" to collector.collectionTime,
                )
            },
        )
    }

    private fun threads(deadlockedIds: List<Long>): Map<String, Any?> {
        val threads = ManagementFactory.getThreadMXBean()
        return linkedMapOf(
            "count" to threads.threadCount,
            "peakCount" to threads.peakThreadCount,
            "daemonCount" to threads.daemonThreadCount,
            "deadlockedCount" to deadlockedIds.size,
            "deadlockedThreadIds" to deadlockedIds,
        )
    }

    fun threadDump(): String {
        val bean = ManagementFactory.getThreadMXBean()
        return bean.dumpAllThreads(true, true).joinToString("\n\n") { info ->
            buildString {
                append('"').append(info.threadName).append('"')
                append(" id=").append(info.threadId)
                append(' ').append(info.threadState).append('\n')
                info.stackTrace.forEach { frame -> append("    at ").append(frame).append('\n') }
            }.trimEnd()
        }
    }

    private fun memoryUsage(init: Long, used: Long, committed: Long, max: Long): Map<String, Any?> = linkedMapOf(
        "initBytes" to init.takeIf { it >= 0 },
        "usedBytes" to used,
        "committedBytes" to committed,
        "maxBytes" to max.takeIf { it >= 0 },
        "usedRatio" to ratio(used, max),
    )

    private fun ratio(used: Long, max: Long): Double? =
        max.takeIf { it > 0 }?.let { used.toDouble() / it }

    private companion object {
        const val HIGH_HEAP_RATIO = 0.90
        const val HIGH_LOAD_PER_PROCESSOR = 1.0
    }
}
