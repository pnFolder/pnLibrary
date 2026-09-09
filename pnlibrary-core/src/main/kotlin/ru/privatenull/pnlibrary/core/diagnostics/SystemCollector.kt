package ru.privatenull.pnlibrary.core.diagnostics

import java.io.File
import java.lang.management.GarbageCollectorMXBean
import java.lang.management.ManagementFactory
import java.lang.management.MemoryPoolMXBean
import java.net.NetworkInterface
import java.time.Instant

/**
 * Cross-platform JVM, OS, system resource, and environment collector.
 */
class SystemCollector {

    private val redactor = DiagnosticRedactor()

    fun collect(includeNetworkAddresses: Boolean = false): Map<String, Any?> {
        val runtimeMx = ManagementFactory.getRuntimeMXBean()
        val osMx = ManagementFactory.getOperatingSystemMXBean()
        val memoryMx = ManagementFactory.getMemoryMXBean()
        val threadMx = ManagementFactory.getThreadMXBean()
        val classMx = ManagementFactory.getClassLoadingMXBean()

        val data = linkedMapOf<String, Any?>()

        // ── Java & JVM ───────────────────────────────────────────────────────
        data["java"] = linkedMapOf(
            "version" to System.getProperty("java.version", "unknown"),
            "vendor" to System.getProperty("java.vendor", "unknown"),
            "runtimeName" to runtimeMx.vmName,
            "runtimeVersion" to runtimeMx.vmVersion,
            "jvmVendor" to runtimeMx.specVendor,
            "pid" to extractPid(runtimeMx.name),
            "uptimeSeconds" to runtimeMx.uptime / 1000,
            "startTimeUtc" to Instant.ofEpochMilli(runtimeMx.startTime).toString(),
            "inputArguments" to sanitizeJvmArgs(runtimeMx.inputArguments),
        )

        // ── OS & Hardware ────────────────────────────────────────────────────
        data["os"] = linkedMapOf(
            "name" to osMx.name,
            "version" to osMx.version,
            "arch" to osMx.arch,
            "availableProcessors" to osMx.availableProcessors,
            "systemLoadAverage" to osMx.systemLoadAverage,
        )

        // ── Memory ───────────────────────────────────────────────────────────
        val heap = memoryMx.heapMemoryUsage
        val nonHeap = memoryMx.nonHeapMemoryUsage
        data["memory"] = linkedMapOf(
            "heap" to linkedMapOf(
                "usedBytes" to heap.used,
                "maxBytes" to heap.max,
                "committedBytes" to heap.committed,
                "usedMb" to (heap.used / (1024 * 1024)),
                "maxMb" to (heap.max / (1024 * 1024)),
            ),
            "nonHeap" to linkedMapOf(
                "usedBytes" to nonHeap.used,
                "maxBytes" to nonHeap.max,
                "committedBytes" to nonHeap.committed,
            ),
            "pools" to collectMemoryPools(),
            "garbageCollectors" to collectGarbageCollectors(),
        )

        // ── Threads ──────────────────────────────────────────────────────────
        val deadlocked = threadMx.findDeadlockedThreads()
        data["threads"] = linkedMapOf(
            "count" to threadMx.threadCount,
            "peakCount" to threadMx.peakThreadCount,
            "daemonCount" to threadMx.daemonThreadCount,
            "totalStartedCount" to threadMx.totalStartedThreadCount,
            "deadlockedCount" to (deadlocked?.size ?: 0),
            "deadlockedThreadIds" to (deadlocked?.toList() ?: emptyList<Long>()),
        )

        // ── Classes ──────────────────────────────────────────────────────────
        data["classes"] = linkedMapOf(
            "loadedCount" to classMx.loadedClassCount,
            "totalLoadedCount" to classMx.totalLoadedClassCount,
            "unloadedCount" to classMx.unloadedClassCount,
        )

        // ── Storage / FileSystems ───────────────────────────────────────────
        data["fileSystems"] = collectFileSystems()

        // ── Environment Variables (NAMES ONLY!) ──────────────────────────────
        data["environmentVariableNames"] = System.getenv().keys.sorted()

        // ── Network Interfaces (ONLY when encrypted!) ────────────────────────
        if (includeNetworkAddresses) {
            data["networkInterfaces"] = collectNetworkInterfaces()
        } else {
            data["networkInterfaces"] = "[REDACTED: available in encrypted report only]"
        }

        return data
    }

    /** Complete JVM thread dump kept as a separate text entry in the archive. */
    fun threadDump(): String {
        val bean = ManagementFactory.getThreadMXBean()
        return buildString {
            bean.dumpAllThreads(true, true).forEach { info ->
                append('"').append(info.threadName).append("\" id=").append(info.threadId)
                    .append(" state=").append(info.threadState).append('\n')
                info.lockName?.let { append("  waiting on ").append(it).append('\n') }
                info.stackTrace.forEach { append("    at ").append(it).append('\n') }
                append('\n')
            }
        }
    }

    private fun extractPid(runtimeName: String): String {
        val idx = runtimeName.indexOf('@')
        return if (idx > 0) runtimeName.substring(0, idx) else runtimeName
    }

    private fun sanitizeJvmArgs(args: List<String>): List<String> {
        return args.map { arg ->
            if (arg.contains("password") || arg.contains("secret") || arg.contains("key") || arg.contains("token")) {
                val idx = arg.indexOf('=')
                if (idx > 0) arg.substring(0, idx + 1) + "[REDACTED]" else "[REDACTED]"
            } else {
                redactor.redact(arg)
            }
        }
    }

    private fun collectMemoryPools(): List<Map<String, Any?>> {
        val pools = ManagementFactory.getMemoryPoolMXBeans()
        return pools.map { pool: MemoryPoolMXBean ->
            val usage = pool.usage
            linkedMapOf(
                "name" to pool.name,
                "type" to pool.type.toString(),
                "usedMb" to (usage?.used ?: 0L) / (1024 * 1024),
                "maxMb" to (usage?.max ?: 0L) / (1024 * 1024),
            )
        }
    }

    private fun collectGarbageCollectors(): List<Map<String, Any?>> {
        val gcs = ManagementFactory.getGarbageCollectorMXBeans()
        return gcs.map { gc: GarbageCollectorMXBean ->
            linkedMapOf(
                "name" to gc.name,
                "collectionCount" to gc.collectionCount,
                "collectionTimeMs" to gc.collectionTime,
            )
        }
    }

    private fun collectFileSystems(): List<Map<String, Any?>> {
        val roots = File.listRoots() ?: return emptyList()
        return roots.map { root ->
            linkedMapOf(
                "path" to root.absolutePath,
                "totalSpaceBytes" to root.totalSpace,
                "freeSpaceBytes" to root.freeSpace,
                "usableSpaceBytes" to root.usableSpace,
                "freeSpaceMb" to (root.freeSpace / (1024 * 1024)),
            )
        }
    }

    private fun collectNetworkInterfaces(): List<Map<String, Any?>> {
        val result = mutableListOf<Map<String, Any?>>()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return emptyList()
            while (interfaces.hasMoreElements()) {
                val nif = interfaces.nextElement()
                if (nif.isLoopback || !nif.isUp) continue
                val addrs = mutableListOf<String>()
                val enumAddresses = nif.inetAddresses
                while (enumAddresses.hasMoreElements()) {
                    val addr = enumAddresses.nextElement()
                    addrs.add(addr.hostAddress)
                }
                result.add(linkedMapOf(
                    "name" to nif.name,
                    "displayName" to nif.displayName,
                    "addresses" to addrs,
                ))
            }
        } catch (_: Exception) { }
        return result
    }
}
