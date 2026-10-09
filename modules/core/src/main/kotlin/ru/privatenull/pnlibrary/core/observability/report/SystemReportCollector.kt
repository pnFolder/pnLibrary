package ru.privatenull.pnlibrary.core.observability.report

import ru.privatenull.pnlibrary.core.diagnostics.DiagnosticRedactor

import java.io.File
import java.lang.management.GarbageCollectorMXBean
import java.lang.management.ManagementFactory
import java.lang.management.MemoryPoolMXBean
import java.net.NetworkInterface
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/**
 * Collects a bounded cross-platform snapshot of JVM and host state.
 *
 * Environment variable values are never read. Network addresses are omitted
 * unless [collect] is explicitly called for an encrypted report. JVM arguments
 * pass through credential and free-text redaction before entering the snapshot.
 */
internal class SystemReportCollector {

    private val redactor = DiagnosticRedactor()

    /**
     * Captures JVM, operating-system, memory, thread, class-loading, and storage data.
     *
     * @param includeNetworkAddresses whether active non-loopback interface addresses
     * may be included; callers should enable this only inside encrypted reports
     */
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
            "classPathEntryCount" to pathEntryCount("java.class.path"),
            "modulePathEntryCount" to pathEntryCount("jdk.module.path"),
            "bootClassPathSupported" to runCatching { runtimeMx.isBootClassPathSupported }.getOrDefault(false),
            "systemProperties" to safeSystemProperties(),
            "systemPropertyCount" to System.getProperties().size,
            "systemPropertyNames" to System.getProperties().stringPropertyNames().sorted(),
            "bootModuleCount" to ModuleLayer.boot().modules().size,
            "environment" to linkedMapOf(
                "defaultCharset" to java.nio.charset.Charset.defaultCharset().name(),
                "fileEncoding" to System.getProperty("file.encoding", "unknown"),
                "defaultLocale" to Locale.getDefault().toLanguageTag(),
                "defaultZone" to ZoneId.systemDefault().id,
                "lineSeparator" to System.lineSeparator().replace("\r", "\\r").replace("\n", "\\n"),
            ),
        )

        // ── OS & Hardware ────────────────────────────────────────────────────
        data["os"] = linkedMapOf(
            "name" to osMx.name,
            "version" to osMx.version,
            "arch" to osMx.arch,
            "availableProcessors" to osMx.availableProcessors,
            "systemLoadAverage" to osMx.systemLoadAverage,
            "cpu" to collectCpuDetails(osMx),
            "fileDescriptors" to collectFileDescriptorDetails(osMx),
        )
        data["runtimeEnvironment"] = collectRuntimeEnvironment()

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
            "garbageCollectors" to collectGarbageCollectors(runtimeMx.uptime),
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
            "stateCounts" to threadStateCounts(threadMx),
            "topCpuThreads" to topCpuThreads(threadMx),
        )

        // ── Classes ──────────────────────────────────────────────────────────
        data["classes"] = linkedMapOf(
            "loadedCount" to classMx.loadedClassCount,
            "totalLoadedCount" to classMx.totalLoadedClassCount,
            "unloadedCount" to classMx.unloadedClassCount,
        )

        // ── Storage / FileSystems ───────────────────────────────────────────
        data["fileSystems"] = collectFileSystems()
        data["health"] = collectHealth(memoryMx, osMx)

        // ── Environment Variables (NAMES ONLY!) ──────────────────────────────
        data["environmentVariableNames"] = System.getenv().keys.sorted()

        // ── Network Interfaces (ONLY when encrypted!) ────────────────────────
        if (includeNetworkAddresses) {
            data["networkInterfaces"] = collectNetworkInterfaces()
        } else {
            data["networkInterfaces"] = "[REDACTED: available in encrypted report only]"
        }
        data["networkSummary"] = collectNetworkSummary()
        data["networkAnalytics"] = collectNetworkAnalytics()
        data["analytics"] = collectAnalytics(data)

        return data
    }

    /**
     * Derives actionable health signals from the raw snapshot without collecting
     * any additional sensitive data. This keeps the report useful when a reader
     * only has the summary sections available.
     */
    private fun collectAnalytics(snapshot: Map<String, Any?>): Map<String, Any?> {
        val memory = snapshot["memory"] as? Map<*, *>
        val heap = memory?.get("heap") as? Map<*, *>
        val health = snapshot["health"] as? Map<*, *>
        val threads = snapshot["threads"] as? Map<*, *>
        val gc = memory?.get("garbageCollectors") as? Collection<*>
        val pools = memory?.get("pools") as? Collection<*>
        val fileSystems = snapshot["fileSystems"] as? Collection<*>
        val threadStates = threads?.get("stateCounts") as? Map<*, *>

        val signals = buildList {
            addPressureSignal(this, "heap", health?.get("heapPressure"))
            addPressureSignal(this, "processCpu", health?.get("processCpuPressure"))
            addPressureSignal(this, "systemCpu", health?.get("systemCpuPressure"))
            addPressureSignal(this, "disk", health?.get("diskPressure"))
            if ((threads?.get("deadlockedCount") as? Number)?.toInt()?.let { it > 0 } == true) {
                add(linkedMapOf("code" to "deadlock", "severity" to "critical"))
            }
            val gcRatio = gc.orEmpty().mapNotNull { entry ->
                (entry as? Map<*, *>)?.get("timeRatio") as? Number
            }.map(Number::toDouble).maxOrNull()
            if (gcRatio != null && gcRatio >= GC_PRESSURE_THRESHOLD) {
                add(linkedMapOf("code" to "gcPressure", "severity" to "elevated"))
            }
            if (pools.orEmpty()
                    .mapNotNull { (it as? Map<*, *>)?.get("usedRatio") as? Number }
                    .map(Number::toDouble)
                    .any { it >= MEMORY_POOL_PRESSURE_THRESHOLD }) {
                add(linkedMapOf("code" to "memoryPoolPressure", "severity" to "elevated"))
            }
        }

        val classLoading = snapshot["classes"] as? Map<*, *>
        return linkedMapOf(
            "generatedUtc" to Instant.now().toString(),
            "status" to when {
                signals.any { it["severity"] == "critical" } -> "critical"
                signals.isNotEmpty() -> "attention"
                else -> "healthy"
            },
            "signals" to signals,
            "resources" to linkedMapOf(
                "heapUsedBytes" to heap?.get("usedBytes"),
                "heapMaxBytes" to heap?.get("maxBytes"),
                "heapUsedRatio" to health?.get("heapUsedRatio"),
                "processCpuLoad" to health?.get("processCpuLoad"),
                "systemCpuLoad" to health?.get("systemCpuLoad"),
                "highestDiskUsedRatio" to health?.get("highestDiskUsedRatio"),
                "gcTimeRatio" to gc?.mapNotNull { entry ->
                    (entry as? Map<*, *>)?.get("timeRatio") as? Number
                }?.map(Number::toDouble)?.maxOrNull(),
                "memoryPoolPressureCount" to pools?.count { pool ->
                    ((pool as? Map<*, *>)?.get("usedRatio") as? Number)?.toDouble()
                        ?.let { it >= MEMORY_POOL_PRESSURE_THRESHOLD } == true
                },
                "largestFileSystem" to fileSystems.orEmpty()
                    .maxByOrNull { ((it as? Map<*, *>)?.get("totalSpaceBytes") as? Number)?.toLong() ?: 0L },
                "networkInterfacesUp" to ((snapshot["networkAnalytics"] as? Map<*, *>)?.get("upCount")),
                "networkAddresses" to ((snapshot["networkAnalytics"] as? Map<*, *>)?.get("totalAddressCount")),
            ),
            "distributions" to linkedMapOf(
                "threadStates" to threadStates.orEmpty(),
                "memoryPools" to pools.orEmpty()
                    .mapNotNull { pool ->
                        val data = pool as? Map<*, *> ?: return@mapNotNull null
                        linkedMapOf(
                            "name" to data["name"],
                            "usedRatio" to data["usedRatio"],
                            "pressure" to ((data["usedRatio"] as? Number)?.toDouble()
                                ?.let { if (it >= MEMORY_POOL_PRESSURE_THRESHOLD) "elevated" else "normal" }),
                        )
                    }
                    .sortedByDescending { (it["usedRatio"] as? Number)?.toDouble() ?: 0.0 },
            ),
            "counts" to linkedMapOf(
                "threads" to threads?.get("count"),
                "deadlockedThreads" to threads?.get("deadlockedCount"),
                "loadedClasses" to classLoading?.get("loadedCount"),
                "unloadedClasses" to classLoading?.get("unloadedCount"),
                "gcCollectors" to gc?.size,
                "fileSystems" to (snapshot["fileSystems"] as? Collection<*>)?.size,
                "threadStates" to threads?.get("stateCounts"),
            ),
        )
    }

    private fun MutableList<Map<String, Any?>>.addPressureSignal(
        signals: MutableList<Map<String, Any?>>,
        code: String,
        pressure: Any?,
    ) {
        when (pressure?.toString()) {
            "critical" -> signals.add(linkedMapOf("code" to code, "severity" to "critical"))
            "elevated" -> signals.add(linkedMapOf("code" to code, "severity" to "elevated"))
        }
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

    private fun sanitizeJvmArgs(args: List<String>): List<String> =
        args.take(MAX_JVM_ARGUMENTS).map { argument ->
            if (SECRET_JVM_ARGUMENT.containsMatchIn(argument)) {
                val separator = argument.indexOf('=')
                if (separator > 0) {
                    argument.substring(0, separator + 1) + "[REDACTED]"
                } else {
                    "[REDACTED]"
                }
            } else {
                redactor.redact(argument).take(MAX_JVM_ARGUMENT_LENGTH)
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
                "usedRatio" to usage?.let { current ->
                    current.max.takeIf { it > 0L }?.let { current.used.toDouble() / it }
                },
            )
        }
    }

    private fun collectGarbageCollectors(uptimeMs: Long): List<Map<String, Any?>> {
        val gcs = ManagementFactory.getGarbageCollectorMXBeans()
        return gcs.map { gc: GarbageCollectorMXBean ->
            linkedMapOf(
                "name" to gc.name,
                "collectionCount" to gc.collectionCount,
                "collectionTimeMs" to gc.collectionTime,
                "timeRatio" to gc.collectionTime.takeIf { it >= 0L }?.let { time ->
                    time.toDouble() / uptimeMs.coerceAtLeast(1L)
                },
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

    private fun collectHealth(
        memory: java.lang.management.MemoryMXBean,
        operatingSystem: java.lang.management.OperatingSystemMXBean,
    ): Map<String, Any?> {
        val heap = memory.heapMemoryUsage
        val heapRatio = heap.max.takeIf { it > 0L }?.let { heap.used.toDouble() / it }
        val processCpu = readDouble(operatingSystem, "getProcessCpuLoad")
        val systemCpu = readDouble(operatingSystem, "getCpuLoad")
        val disks = File.listRoots().orEmpty()
        val diskRatios = disks.mapNotNull { root ->
            root.totalSpace.takeIf { it > 0L }?.let {
                (it - root.freeSpace).toDouble() / it
            }
        }
        return linkedMapOf(
            "heapUsedRatio" to heapRatio,
            "processCpuLoad" to processCpu,
            "systemCpuLoad" to systemCpu,
            "highestDiskUsedRatio" to diskRatios.maxOrNull(),
            "heapPressure" to pressure(heapRatio),
            "processCpuPressure" to pressure(processCpu),
            "systemCpuPressure" to pressure(systemCpu),
            "diskPressure" to pressure(diskRatios.maxOrNull()),
        )
    }

    private fun pressure(value: Double?): String? = value?.let {
        when {
            it >= 0.90 -> "critical"
            it >= 0.75 -> "elevated"
            else -> "normal"
        }
    }

    private fun threadStateCounts(bean: java.lang.management.ThreadMXBean): Map<String, Int> {
        val counts = linkedMapOf<String, Int>()
        bean.getThreadInfo(bean.allThreadIds)?.forEach { info ->
            if (info != null) counts.merge(info.threadState.name, 1, Int::plus)
        }
        return counts
    }

    private fun topCpuThreads(bean: java.lang.management.ThreadMXBean): List<Map<String, Any?>> {
        if (!bean.isThreadCpuTimeSupported) return emptyList()
        val ids = bean.allThreadIds
        return ids.asSequence().mapNotNull { id: Long ->
            val cpuNanos = runCatching { bean.getThreadCpuTime(id) }.getOrDefault(-1L)
            if (cpuNanos < 0L) return@mapNotNull null
            val info = bean.getThreadInfo(id) ?: return@mapNotNull null
            linkedMapOf<String, Any?>(
                "id" to id,
                "name" to info.threadName.take(256),
                "state" to info.threadState.name,
                "cpuTimeNanos" to cpuNanos,
            )
        }.sortedByDescending { (it["cpuTimeNanos"] as Number).toLong() }
            .take(MAX_CPU_THREADS)
            .toList()
    }

    private fun pathEntryCount(property: String): Int =
        System.getProperty(property)?.split(File.pathSeparatorChar)?.count { it.isNotBlank() } ?: 0

    private fun safeSystemProperties(): Map<String, String> = linkedMapOf<String, String>().apply {
        SAFE_SYSTEM_PROPERTIES.forEach { key ->
            System.getProperty(key)?.takeIf(String::isNotBlank)?.let { put(key, it.take(256)) }
        }
    }

    private fun collectCpuDetails(osMx: java.lang.management.OperatingSystemMXBean): Map<String, Any?> {
        val result = linkedMapOf<String, Any?>()
        readDouble(osMx, "getProcessCpuLoad")?.let { result["processLoad"] = it }
        readDouble(osMx, "getCpuLoad")?.let { result["systemLoad"] = it }
        readLong(osMx, "getProcessCpuTime")?.let { result["processCpuTimeNanos"] = it }
        readLong(osMx, "getCommittedVirtualMemorySize")?.let { result["committedVirtualMemoryBytes"] = it }
        return result
    }

    private fun readDouble(target: Any, method: String): Double? = runCatching {
        target.javaClass.getMethod(method).invoke(target) as? Double
    }.getOrNull()?.takeIf { it >= 0.0 }

    private fun readLong(target: Any, method: String): Long? = runCatching {
        target.javaClass.getMethod(method).invoke(target) as? Long
    }.getOrNull()?.takeIf { it >= 0L }

    private fun collectFileDescriptorDetails(osMx: Any): Map<String, Long> {
        val result = linkedMapOf<String, Long>()
        readLong(osMx, "getOpenFileDescriptorCount")?.let { result["open"] = it }
        readLong(osMx, "getMaxFileDescriptorCount")?.let { result["max"] = it }
        return result
    }

    private fun collectRuntimeEnvironment(): Map<String, Any?> = linkedMapOf(
        "dockerMarker" to File("/.dockerenv").isFile,
        "containerEnvMarker" to File("/run/.containerenv").isFile,
        "cgroupContainerHint" to runCatching {
            File("/proc/1/cgroup").takeIf(File::isFile)?.readText()?.let { content ->
                listOf("docker", "containerd", "kubepods", "podman", "lxc")
                    .firstOrNull { marker -> content.contains(marker, ignoreCase = true) }
            }
        }.getOrNull(),
        "workingDirectoryPresent" to File(System.getProperty("user.dir", ".")).isDirectory,
    )

    private fun collectNetworkInterfaces(): List<Map<String, Any?>> {
        val result = mutableListOf<Map<String, Any?>>()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return emptyList()
            while (interfaces.hasMoreElements() && result.size < MAX_NETWORK_INTERFACES) {
                val nif = interfaces.nextElement()
                if (nif.isLoopback || !nif.isUp) continue
                val addrs = mutableListOf<String>()
                val enumAddresses = nif.inetAddresses
                while (enumAddresses.hasMoreElements() && addrs.size < MAX_ADDRESSES_PER_INTERFACE) {
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

    private fun collectNetworkSummary(): Map<String, Int> = runCatching {
        val interfaces = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
        linkedMapOf(
            "total" to interfaces.size,
            "up" to interfaces.count { it.isUp },
            "loopback" to interfaces.count { it.isLoopback },
            "nonLoopback" to interfaces.count { !it.isLoopback },
            "addressCount" to interfaces.sumOf { nif -> nif.inetAddresses.toList().size },
        )
    }.getOrDefault(emptyMap())

    private fun collectNetworkAnalytics(): Map<String, Any?> = runCatching {
        val interfaces = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
        val details = interfaces.map { nif ->
            linkedMapOf<String, Any?>(
                "name" to nif.name,
                "up" to nif.isUp,
                "loopback" to nif.isLoopback,
                "virtual" to nif.isVirtual,
                "mtu" to runCatching { nif.mtu }.getOrNull(),
                "addressCount" to nif.inetAddresses.toList().size,
            )
        }
        linkedMapOf(
            "interfaces" to details,
            "upCount" to details.count { it["up"] == true },
            "downCount" to details.count { it["up"] == false },
            "virtualCount" to details.count { it["virtual"] == true },
            "totalAddressCount" to details.sumOf { (it["addressCount"] as? Number)?.toInt() ?: 0 },
        )
    }.getOrDefault(emptyMap())

    private companion object {
        const val MAX_JVM_ARGUMENTS = 128
        const val MAX_JVM_ARGUMENT_LENGTH = 1_024
        const val MAX_NETWORK_INTERFACES = 64
        const val MAX_ADDRESSES_PER_INTERFACE = 32
        const val MAX_CPU_THREADS = 20
        const val GC_PRESSURE_THRESHOLD = 0.25
        const val MEMORY_POOL_PRESSURE_THRESHOLD = 0.90
        val SECRET_JVM_ARGUMENT = Regex(
            "(?i)(?:^|[._-])(?:password|passwd|pwd|secret|token|api[-_]?key|authorization|credential)(?:[._=-]|$)",
        )
        val SAFE_SYSTEM_PROPERTIES = listOf(
            "java.home",
            "java.vm.name",
            "java.vm.version",
            "java.runtime.name",
            "java.runtime.version",
            "os.name",
            "os.version",
            "os.arch",
            "user.language",
            "user.country",
        )
    }
}
