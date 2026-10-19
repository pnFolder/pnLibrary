package org.pnfolder.pnlibrary.core.observability.report

import java.io.File
import java.lang.management.ManagementFactory
import java.time.Instant

/** Collects a compact JVM and host snapshot used to diagnose common server failures. */
internal class SystemReportCollector {
    fun collect(): Map<String, Any?> {
        val runtime = ManagementFactory.getRuntimeMXBean()
        val os = ManagementFactory.getOperatingSystemMXBean()
        val memory = ManagementFactory.getMemoryMXBean()
        val threads = ManagementFactory.getThreadMXBean()
        val heap = memory.heapMemoryUsage
        val nonHeap = memory.nonHeapMemoryUsage
        val heapRatio = ratio(heap.used, heap.max)
        val deadlockedIds = threads.findDeadlockedThreads()?.toList().orEmpty()
        val loadPerProcessor = os.systemLoadAverage
            .takeIf { it >= 0 }
            ?.div(os.availableProcessors.coerceAtLeast(1))
        val signals = buildList<Map<String, Any?>> {
            if (heapRatio != null && heapRatio >= HIGH_HEAP_RATIO) {
                add(linkedMapOf("code" to "highHeapUsage", "usedRatio" to heapRatio))
            }
            if (deadlockedIds.isNotEmpty()) {
                add(linkedMapOf("code" to "deadlockedThreads", "count" to deadlockedIds.size))
            }
            if (loadPerProcessor != null && loadPerProcessor >= HIGH_LOAD_PER_PROCESSOR) {
                add(linkedMapOf("code" to "highSystemLoad", "loadPerProcessor" to loadPerProcessor))
            }
        }

        return linkedMapOf(
            "generatedUtc" to Instant.now().toString(),
            "java" to linkedMapOf(
                "version" to System.getProperty("java.version", "unknown"),
                "vendor" to System.getProperty("java.vendor", "unknown"),
                "vmName" to runtime.vmName,
                "vmVersion" to runtime.vmVersion,
                "uptimeSeconds" to runtime.uptime / 1_000,
                "startTimeUtc" to Instant.ofEpochMilli(runtime.startTime).toString(),
            ),
            "os" to linkedMapOf(
                "name" to os.name,
                "version" to os.version,
                "arch" to os.arch,
                "availableProcessors" to os.availableProcessors,
                "systemLoadAverage" to os.systemLoadAverage.takeIf { it >= 0 },
                "loadPerProcessor" to loadPerProcessor,
            ),
            "memory" to linkedMapOf(
                "heap" to memoryUsage(heap.init, heap.used, heap.committed, heap.max),
                "nonHeap" to memoryUsage(nonHeap.init, nonHeap.used, nonHeap.committed, nonHeap.max),
                "garbageCollectors" to ManagementFactory.getGarbageCollectorMXBeans().map { collector ->
                    linkedMapOf(
                        "name" to collector.name,
                        "collections" to collector.collectionCount,
                        "collectionTimeMs" to collector.collectionTime,
                    )
                },
            ),
            "threads" to linkedMapOf(
                "count" to threads.threadCount,
                "peakCount" to threads.peakThreadCount,
                "daemonCount" to threads.daemonThreadCount,
                "deadlockedCount" to deadlockedIds.size,
                "deadlockedThreadIds" to deadlockedIds,
            ),
            "storage" to File.listRoots().map { root ->
                linkedMapOf(
                    "root" to root.path,
                    "totalBytes" to root.totalSpace,
                    "usableBytes" to root.usableSpace,
                )
            },
            "health" to linkedMapOf(
                "status" to if (signals.isEmpty()) "healthy" else "attention",
                "signals" to signals,
            ),
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
