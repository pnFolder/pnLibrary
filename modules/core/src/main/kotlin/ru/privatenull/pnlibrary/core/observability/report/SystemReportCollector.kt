package ru.privatenull.pnlibrary.core.observability.report

import ru.privatenull.pnlibrary.core.diagnostics.DiagnosticRedactor

import java.io.File
import java.lang.management.GarbageCollectorMXBean
import java.lang.management.ManagementFactory
import java.lang.management.MemoryPoolMXBean
import java.lang.management.BufferPoolMXBean
import java.lang.management.CompilationMXBean
import java.net.NetworkInterface
import java.nio.file.Files
import java.nio.file.Paths
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
    private val collectionHistory = ArrayDeque<MutableMap<String, Any?>>()

    /**
     * Captures JVM, operating-system, memory, thread, class-loading, and storage data.
     *
     * @param includeNetworkAddresses whether active non-loopback interface addresses
     * may be included; callers should enable this only inside encrypted reports
     */
    fun collect(includeNetworkAddresses: Boolean = false): Map<String, Any?> {
        val startedNanos = System.nanoTime()
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
            "argumentAnalytics" to collectJvmArgumentAnalytics(runtimeMx.inputArguments),
            "classPathEntryCount" to pathEntryCount("java.class.path"),
            "modulePathEntryCount" to pathEntryCount("jdk.module.path"),
            "bootClassPathSupported" to runCatching { runtimeMx.isBootClassPathSupported }.getOrDefault(false),
            "systemProperties" to safeSystemProperties(),
            "systemPropertyCount" to System.getProperties().size,
            "systemPropertyNames" to System.getProperties().stringPropertyNames().sorted(),
            "bootModuleCount" to ModuleLayer.boot().modules().size,
            "classpathAnalytics" to collectClasspathAnalytics(),
            "modulePathAnalytics" to collectPathAnalytics("jdk.module.path"),
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
            "distribution" to collectOsDistribution(),
            "kernelRelease" to readTextFile("/proc/sys/kernel/osrelease"),
            "availableProcessors" to osMx.availableProcessors,
            "systemLoadAverage" to osMx.systemLoadAverage,
            "cpu" to collectCpuDetails(osMx),
            "cpuTopology" to collectCpuTopology(),
            "fileDescriptors" to collectFileDescriptorDetails(osMx),
        )
        data["runtimeEnvironment"] = collectRuntimeEnvironment()
        data["processIo"] = collectProcessIo()
        data["processNetwork"] = collectProcessNetwork()
        data["processStatus"] = collectProcessStatus()
        data["processAffinity"] = collectProcessAffinity()
        data["processSecurity"] = collectProcessSecurity()
        data["processNamespaces"] = collectProcessNamespaces()
        data["processMemoryMaps"] = collectProcessMemoryMaps()
        data["processFileDescriptors"] = collectProcessFileDescriptors()
        data["processLimits"] = collectProcessLimits()
        data["kernelLimits"] = collectKernelLimits()
        data["containerIo"] = collectContainerIo()
        data["processScheduling"] = collectProcessScheduling()
        data["systemScheduling"] = collectSystemScheduling()
        data["loadAverage"] = collectLoadAverage()
        data["pressureStall"] = collectPressureStall()
        data["systemMemory"] = collectSystemMemory()

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
            "bufferPools" to collectBufferPools(),
        )
        data["jitCompilation"] = collectJitCompilation()

        // ── Threads ──────────────────────────────────────────────────────────
        val deadlocked = threadMx.findDeadlockedThreads()
        data["threads"] = linkedMapOf(
            "count" to threadMx.threadCount,
            "peakCount" to threadMx.peakThreadCount,
            "daemonCount" to threadMx.daemonThreadCount,
            "totalStartedCount" to threadMx.totalStartedThreadCount,
            "deadlockedCount" to (deadlocked?.size ?: 0),
            "deadlockedThreadIds" to (deadlocked?.toList() ?: emptyList<Long>()),
            "deadlockedThreads" to deadlockedThreadDetails(threadMx, deadlocked),
            "stateCounts" to threadStateCounts(threadMx),
            "contention" to threadContention(threadMx),
            "topCpuThreads" to topCpuThreads(threadMx),
            "analytics" to collectThreadAnalytics(threadMx),
        )
        data["capabilities"] = linkedMapOf(
            "threadCpuTimeSupported" to threadMx.isThreadCpuTimeSupported,
            "threadCpuTimeEnabled" to threadMx.isThreadCpuTimeEnabled,
            "threadContentionSupported" to threadMx.isThreadContentionMonitoringSupported,
            "threadContentionEnabled" to threadMx.isThreadContentionMonitoringEnabled,
            "threadMonitorUsageSupported" to threadMx.isObjectMonitorUsageSupported,
            "threadSynchronizerUsageSupported" to threadMx.isSynchronizerUsageSupported,
            "classLoadingSupported" to true,
            "classLoadingVerbose" to classMx.isVerbose,
            "bootClassPathSupported" to runtimeMx.isBootClassPathSupported,
        )

        // ── Classes ──────────────────────────────────────────────────────────
        data["classes"] = linkedMapOf(
            "loadedCount" to classMx.loadedClassCount,
            "totalLoadedCount" to classMx.totalLoadedClassCount,
            "unloadedCount" to classMx.unloadedClassCount,
        )

        // ── Storage / FileSystems ───────────────────────────────────────────
        data["fileSystems"] = collectFileSystems()
        data["mounts"] = collectMountAnalytics()
        data["health"] = collectHealth(memoryMx, osMx)

        // ── Environment Variables (NAMES ONLY!) ──────────────────────────────
        data["environmentVariableNames"] = System.getenv().keys.sorted()
        data["environmentVariableAnalytics"] = collectEnvironmentVariableAnalytics()

        // ── Network Interfaces (ONLY when encrypted!) ────────────────────────
        if (includeNetworkAddresses) {
            data["networkInterfaces"] = collectNetworkInterfaces()
        } else {
            data["networkInterfaces"] = "[REDACTED: available in encrypted report only]"
        }
        data["networkSummary"] = collectNetworkSummary()
        data["networkAnalytics"] = collectNetworkAnalytics()
        val durationMs = (System.nanoTime() - startedNanos) / 1_000_000
        data["collection"] = linkedMapOf(
            "durationMs" to durationMs,
            "includeNetworkAddresses" to includeNetworkAddresses,
            "thread" to Thread.currentThread().name,
        )
        collectionHistory.addLast(
            linkedMapOf(
                "completedUtc" to Instant.now().toString(),
                "durationMs" to durationMs,
                "networkAddressesIncluded" to includeNetworkAddresses,
                "status" to "healthy",
                "heapUsedRatio" to (data["health"] as? Map<*, *>)?.get("heapUsedRatio"),
                "heapUsedBytes" to ((data["memory"] as? Map<*, *>)?.get("heap") as? Map<*, *>)?.get("usedBytes"),
                "nonHeapUsedBytes" to ((data["memory"] as? Map<*, *>)?.get("nonHeap") as? Map<*, *>)?.get("usedBytes"),
                "highestMemoryPoolRatio" to memoryPoolMaxRatio(data),
                "pressuredMemoryPoolCount" to memoryPoolPressureCount(data),
                "processCpuLoad" to (data["health"] as? Map<*, *>)?.get("processCpuLoad"),
                "systemCpuLoad" to (data["health"] as? Map<*, *>)?.get("systemCpuLoad"),
                "highestDiskUsedRatio" to (data["health"] as? Map<*, *>)?.get("highestDiskUsedRatio"),
                "loadedClassCount" to (data["classes"] as? Map<*, *>)?.get("loadedCount"),
                "totalLoadedClassCount" to (data["classes"] as? Map<*, *>)?.get("totalLoadedCount"),
                "unloadedClassCount" to (data["classes"] as? Map<*, *>)?.get("unloadedCount"),
                "readBytes" to (data["processIo"] as? Map<*, *>)?.get("read_bytes"),
                "writeBytes" to (data["processIo"] as? Map<*, *>)?.get("write_bytes"),
                "tcpEstablished" to (data["processNetwork"] as? Map<*, *>)?.get("tcpEstablished"),
                "tcpListening" to (data["processNetwork"] as? Map<*, *>)?.get("tcpListening"),
                "udpSockets" to (data["processNetwork"] as? Map<*, *>)?.get("udpSockets"),
                "unixSockets" to (data["processNetwork"] as? Map<*, *>)?.get("unixSockets"),
                "processPssBytes" to (data["processMemoryMaps"] as? Map<*, *>)?.get("Pss"),
                "openFileDescriptorCount" to (data["processFileDescriptors"] as? Map<*, *>)?.get("total"),
                "allowedCpuCount" to (data["processAffinity"] as? Map<*, *>)?.get("allowedCpuCount"),
                "kernelFileHandlesAllocated" to (data["kernelLimits"] as? Map<*, *>)?.get("fileHandlesAllocated"),
                "kernelFileHandlesUsedRatio" to ((data["kernelLimits"] as? Map<*, *>)?.get("fileHandlesUsedRatio")),
                "kernelPidMaximum" to ((data["kernelLimits"] as? Map<*, *>)?.get("pidMaximum")),
                "kernelThreadsMaximum" to ((data["kernelLimits"] as? Map<*, *>)?.get("threadsMaximum")),
                "systemContextSwitches" to (data["systemScheduling"] as? Map<*, *>)?.get("contextSwitches"),
                "processSecurity" to data["processSecurity"],
                "processNamespaces" to data["processNamespaces"],
                "containerIoReadBytes" to (data["containerIo"] as? Map<*, *>)?.get("readBytes"),
                "containerEffectiveCpuCount" to (data["runtimeEnvironment"] as? Map<*, *>)?.get("effectiveCpuCount"),
                "cpuPressureAvg10" to ((data["pressureStall"] as? Map<*, *>)?.get("cpu") as? Map<*, *>)?.get("someAvg10"),
                "memoryPressureAvg10" to ((data["pressureStall"] as? Map<*, *>)?.get("memory") as? Map<*, *>)?.get("someAvg10"),
                "ioPressureAvg10" to ((data["pressureStall"] as? Map<*, *>)?.get("io") as? Map<*, *>)?.get("someAvg10"),
                "pressureStall" to data["pressureStall"],
                "systemMemoryAvailableBytes" to (data["systemMemory"] as? Map<*, *>)?.get("MemAvailable"),
                "networkReceiveBytes" to (data["networkAnalytics"] as? Map<*, *>)?.get("totalReceiveBytes"),
                "networkTransmitBytes" to (data["networkAnalytics"] as? Map<*, *>)?.get("totalTransmitBytes"),
                "networkReceiveErrors" to (data["networkAnalytics"] as? Map<*, *>)?.get("totalReceiveErrors"),
                "networkTransmitErrors" to (data["networkAnalytics"] as? Map<*, *>)?.get("totalTransmitErrors"),
                "networkReceiveDrops" to (data["networkAnalytics"] as? Map<*, *>)?.get("totalReceiveDrops"),
                "networkTransmitDrops" to (data["networkAnalytics"] as? Map<*, *>)?.get("totalTransmitDrops"),
                "bufferPoolUsedBytes" to ((data["memory"] as? Map<*, *>)?.get("bufferPools") as? Collection<*>)?.sumOf {
                    ((it as? Map<*, *>)?.get("usedBytes") as? Number)?.toLong() ?: 0L
                },
                "jitCompilationTimeMs" to (data["jitCompilation"] as? Map<*, *>)?.get("totalCompilationTimeMs"),
                "containerMemoryCurrent" to (data["runtimeEnvironment"] as? Map<*, *>)?.get("memoryCurrentBytes"),
                "containerCpuThrottled" to (data["runtimeEnvironment"] as? Map<*, *>)?.get("cpuThrottledMicros"),
                "containerMemoryHighEvents" to (data["runtimeEnvironment"] as? Map<*, *>)?.get("memoryHighEvents"),
                "containerMemoryMaxEvents" to (data["runtimeEnvironment"] as? Map<*, *>)?.get("memoryMaxEvents"),
                "containerMemoryOomEvents" to (data["runtimeEnvironment"] as? Map<*, *>)?.get("memoryOomEvents"),
                "containerMemoryOomKillEvents" to (data["runtimeEnvironment"] as? Map<*, *>)?.get("memoryOomKillEvents"),
                "processRss" to (data["processStatus"] as? Map<*, *>)?.get("VmRSS"),
                "processPeakRss" to (data["processStatus"] as? Map<*, *>)?.get("VmPeak"),
                "voluntaryContextSwitches" to (data["processStatus"] as? Map<*, *>)?.get("voluntary_ctxt_switches"),
                "nonVoluntaryContextSwitches" to (data["processStatus"] as? Map<*, *>)?.get("nonvoluntary_ctxt_switches"),
                "runnableThreads" to threadStateCount(data, "RUNNABLE"),
                "blockedThreads" to threadStateCount(data, "BLOCKED"),
                "waitingThreads" to threadStateCount(data, "WAITING"),
                "timedWaitingThreads" to threadStateCount(data, "TIMED_WAITING"),
                "blockedTimeMs" to ((data["threads"] as? Map<*, *>)?.get("contention") as? Map<*, *>)?.get("blockedTimeMs"),
                "waitedTimeMs" to ((data["threads"] as? Map<*, *>)?.get("contention") as? Map<*, *>)?.get("waitedTimeMs"),
                "blockedCount" to ((data["threads"] as? Map<*, *>)?.get("contention") as? Map<*, *>)?.get("blockedCount"),
                "waitedCount" to ((data["threads"] as? Map<*, *>)?.get("contention") as? Map<*, *>)?.get("waitedCount"),
                "gcCollectionCount" to gcTotal(data, "collectionCount"),
                "gcCollectionTimeMs" to gcTotal(data, "collectionTimeMs"),
                "dominantGcCollector" to dominantGcCollector(data)?.get("name"),
                "dominantGcTimeMs" to dominantGcCollector(data)?.get("collectionTimeMs"),
                "threadCount" to (data["threads"] as? Map<*, *>)?.get("count"),
                "daemonThreadCount" to (data["threads"] as? Map<*, *>)?.get("daemonCount"),
                "peakThreadCount" to (data["threads"] as? Map<*, *>)?.get("peakCount"),
                "freePhysicalMemoryBytes" to ((data["os"] as? Map<*, *>)?.get("cpu") as? Map<*, *>)?.get("freePhysicalMemoryBytes"),
                "freeSwapBytes" to ((data["os"] as? Map<*, *>)?.get("cpu") as? Map<*, *>)?.get("freeSwapBytes"),
                "hostUptimeSeconds" to (data["runtimeEnvironment"] as? Map<*, *>)?.get("hostUptimeSeconds"),
                "fileDescriptorOpen" to ((data["os"] as? Map<*, *>)?.get("fileDescriptors") as? Map<*, *>)?.get("open"),
                "processUserCpuTicks" to (data["processScheduling"] as? Map<*, *>)?.get("userCpuTicks"),
                "processSystemCpuTicks" to (data["processScheduling"] as? Map<*, *>)?.get("systemCpuTicks"),
            ),
        )
        while (collectionHistory.size > 32) collectionHistory.removeFirst()
        val analytics = collectAnalytics(data)
        collectionHistory.lastOrNull()?.let { sample ->
            sample["status"] = analytics["status"]
            val signals = analytics["signals"] as? Collection<*> ?: emptyList<Any?>()
            sample["signalCount"] = signals.size
            sample["signalCodes"] = signals.mapNotNull { (it as? Map<*, *>)?.get("code")?.toString() }.distinct()
        }
        data["analytics"] = analytics + ("collectionHistory" to collectionAnalytics())

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
        val nonHeap = memory?.get("nonHeap") as? Map<*, *>
        val health = snapshot["health"] as? Map<*, *>
        val threads = snapshot["threads"] as? Map<*, *>
        val gc = memory?.get("garbageCollectors") as? Collection<*>
        val pools = memory?.get("pools") as? Collection<*>
        val fileSystems = snapshot["fileSystems"] as? Collection<*>
        val threadStates = threads?.get("stateCounts") as? Map<*, *>
        val topCpuThreads = threads?.get("topCpuThreads") as? Collection<*>
        val classes = snapshot["classes"] as? Map<*, *>
        val java = snapshot["java"] as? Map<*, *>
        val os = snapshot["os"] as? Map<*, *>
        val cpu = os?.get("cpu") as? Map<*, *>
        val fileDescriptors = os?.get("fileDescriptors") as? Map<*, *>

        val signals = buildList {
            addPressureSignal(this, "heap", health?.get("heapPressure"))
            addPressureSignal(this, "processCpu", health?.get("processCpuPressure"))
            addPressureSignal(this, "systemCpu", health?.get("systemCpuPressure"))
            addPressureSignal(this, "disk", health?.get("diskPressure"))
            addPressureSignal(this, "physicalMemory", health?.get("physicalMemoryPressure"))
            addPressureSignal(this, "swap", health?.get("swapPressure"))
            addPressureSignal(this, "fileDescriptor", health?.get("fileDescriptorPressure"))
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
            if (descriptorRatio(fileDescriptors)?.let { it >= 0.90 } == true) {
                add(linkedMapOf("code" to "fileDescriptorPressure", "severity" to "elevated"))
            }
            if (processDescriptorRatio(snapshot)?.let { it >= 0.90 } == true) {
                add(linkedMapOf("code" to "processFileDescriptorPressure", "severity" to "elevated"))
            }
            val runtimeEnvironment = snapshot["runtimeEnvironment"] as? Map<*, *>
            if ((runtimeEnvironment?.get("memoryOomKillEvents") as? Number)?.toLong()?.let { it > 0L } == true) {
                add(linkedMapOf("code" to "containerOomKill", "severity" to "critical"))
            } else if ((runtimeEnvironment?.get("memoryHighEvents") as? Number)?.toLong()?.let { it > 0L } == true ||
                (runtimeEnvironment?.get("memoryMaxEvents") as? Number)?.toLong()?.let { it > 0L } == true
            ) {
                add(linkedMapOf("code" to "containerMemoryPressure", "severity" to "elevated"))
            }
        }

        val classLoading = snapshot["classes"] as? Map<*, *>
        return linkedMapOf(
            "generatedUtc" to Instant.now().toString(),
            "collection" to linkedMapOf(
                "sampleCount" to collectionHistory.size,
                "lastDurationMs" to collectionHistory.lastOrNull()?.get("durationMs"),
                "averageDurationMs" to collectionHistory.mapNotNull { (it["durationMs"] as? Number)?.toLong() }
                    .average().takeIf { collectionHistory.isNotEmpty() },
                "maxDurationMs" to collectionHistory.mapNotNull { (it["durationMs"] as? Number)?.toLong() }.maxOrNull(),
                "recent" to collectionHistory.toList(),
            ),
            "status" to when {
                signals.any { it["severity"] == "critical" } -> "critical"
                signals.isNotEmpty() -> "attention"
                else -> "healthy"
            },
            "signals" to signals,
            "signalSummary" to linkedMapOf(
                "total" to signals.size,
                "critical" to signals.count { it["severity"] == "critical" },
                "elevated" to signals.count { it["severity"] == "elevated" },
                "codes" to signals.mapNotNull { it["code"] as? String }
                    .distinct()
                    .sorted(),
            ),
            "coverage" to linkedMapOf(
                "memory" to (memory != null),
                "threads" to (threads != null),
                "garbageCollectors" to (gc != null),
                "fileSystems" to (fileSystems != null),
                "network" to (snapshot["networkAnalytics"] != null),
                "javaRuntime" to (java != null),
                "processIo" to hasData(snapshot["processIo"]),
                "processNetwork" to hasData(snapshot["processNetwork"]),
            "processStatus" to hasData(snapshot["processStatus"]),
                "processMemoryMaps" to hasData(snapshot["processMemoryMaps"]),
                "processFileDescriptors" to hasData(snapshot["processFileDescriptors"]),
                "processLimits" to hasData(snapshot["processLimits"]),
                "processScheduling" to hasData(snapshot["processScheduling"]),
                "loadAverage" to hasData(snapshot["loadAverage"]),
            ),
            "unavailableSections" to listOf(
                "processIo" to snapshot["processIo"],
                "processNetwork" to snapshot["processNetwork"],
                "processStatus" to snapshot["processStatus"],
                "processLimits" to snapshot["processLimits"],
                "processScheduling" to snapshot["processScheduling"],
                "loadAverage" to snapshot["loadAverage"],
            ).filter { !hasData(it.second) }.map { it.first },
            "containerLimits" to linkedMapOf(
                "memoryLimitBytes" to (snapshot["runtimeEnvironment"] as? Map<*, *>)?.get("memoryLimitBytes"),
                "memoryCurrentBytes" to (snapshot["runtimeEnvironment"] as? Map<*, *>)?.get("memoryCurrentBytes"),
                "memoryUsedRatio" to containerMemoryRatio(snapshot),
                "cpuQuotaMicros" to (snapshot["runtimeEnvironment"] as? Map<*, *>)?.get("cpuQuotaMicros"),
                "cpuPeriodMicros" to (snapshot["runtimeEnvironment"] as? Map<*, *>)?.get("cpuPeriodMicros"),
                "cpuQuotaCores" to containerCpuQuota(snapshot),
                "cpuUsageMicros" to (snapshot["runtimeEnvironment"] as? Map<*, *>)?.get("cpuUsageMicros"),
                "cpuThrottledMicros" to (snapshot["runtimeEnvironment"] as? Map<*, *>)?.get("cpuThrottledMicros"),
                "cpuThrottleEvents" to (snapshot["runtimeEnvironment"] as? Map<*, *>)?.get("cpuThrottleEvents"),
                "memoryHighEvents" to (snapshot["runtimeEnvironment"] as? Map<*, *>)?.get("memoryHighEvents"),
                "memoryMaxEvents" to (snapshot["runtimeEnvironment"] as? Map<*, *>)?.get("memoryMaxEvents"),
                "memoryOomEvents" to (snapshot["runtimeEnvironment"] as? Map<*, *>)?.get("memoryOomEvents"),
                "memoryOomKillEvents" to (snapshot["runtimeEnvironment"] as? Map<*, *>)?.get("memoryOomKillEvents"),
            ),
            "classpathAnalytics" to (java?.get("classpathAnalytics") ?: emptyMap<String, Any>()),
            "modulePathAnalytics" to (java?.get("modulePathAnalytics") ?: emptyMap<String, Any>()),
            "processIo" to snapshot["processIo"],
            "processNetwork" to snapshot["processNetwork"],
            "processStatus" to snapshot["processStatus"],
            "processLimits" to snapshot["processLimits"],
            "processScheduling" to snapshot["processScheduling"],
            "capabilities" to snapshot["capabilities"],
            "loadAverage" to snapshot["loadAverage"],
            "environmentVariables" to snapshot["environmentVariableAnalytics"],
            "hostDistribution" to linkedMapOf(
                "availableProcessors" to os?.get("availableProcessors"),
                "distribution" to os?.get("distribution"),
                "kernelRelease" to os?.get("kernelRelease"),
                "systemLoadAverage" to os?.get("systemLoadAverage"),
                "processCpuTimeNanos" to cpu?.get("processCpuTimeNanos"),
                "hostUptimeSeconds" to (snapshot["runtimeEnvironment"] as? Map<*, *>)?.get("hostUptimeSeconds"),
            "committedVirtualMemoryBytes" to cpu?.get("committedVirtualMemoryBytes"),
                "totalPhysicalMemoryBytes" to cpu?.get("totalPhysicalMemoryBytes"),
                "freePhysicalMemoryBytes" to cpu?.get("freePhysicalMemoryBytes"),
                "totalSwapBytes" to cpu?.get("totalSwapBytes"),
                "freeSwapBytes" to cpu?.get("freeSwapBytes"),
                "swapUsedBytes" to swapUsedBytes(cpu),
                "swapUsedRatio" to swapUsedRatio(cpu),
                "fileDescriptorOpen" to fileDescriptors?.get("open"),
                "fileDescriptorMax" to fileDescriptors?.get("max"),
                "fileDescriptorUsedRatio" to descriptorRatio(fileDescriptors),
                "loadedClassCount" to classes?.get("loadedCount"),
                "unloadedClassCount" to classes?.get("unloadedCount"),
            ),
            "resources" to linkedMapOf(
                "heapUsedBytes" to heap?.get("usedBytes"),
                "heapMaxBytes" to heap?.get("maxBytes"),
                "heapUsedRatio" to health?.get("heapUsedRatio"),
                "nonHeapUsedBytes" to nonHeap?.get("usedBytes"),
                "nonHeapCommittedBytes" to nonHeap?.get("committedBytes"),
                "processCpuLoad" to health?.get("processCpuLoad"),
                "systemCpuLoad" to health?.get("systemCpuLoad"),
                "swapUsedBytes" to swapUsedBytes(cpu),
                "swapUsedRatio" to swapUsedRatio(cpu),
                "highestDiskUsedRatio" to health?.get("highestDiskUsedRatio"),
                "gcTimeRatio" to gc?.mapNotNull { entry ->
                    (entry as? Map<*, *>)?.get("timeRatio") as? Number
                }?.map(Number::toDouble)?.maxOrNull(),
                "memoryPoolPressureCount" to pools?.count { pool ->
                    ((pool as? Map<*, *>)?.get("usedRatio") as? Number)?.toDouble()
                        ?.let { it >= MEMORY_POOL_PRESSURE_THRESHOLD } == true
                },
                "memoryPoolPeakUsedBytes" to pools?.mapNotNull {
                    ((it as? Map<*, *>)?.get("peakUsedBytes") as? Number)?.toLong()
                }?.maxOrNull(),
                "memoryPoolCollectionUsedBytes" to pools?.sumOf {
                    ((it as? Map<*, *>)?.get("collectionUsedBytes") as? Number)?.toLong() ?: 0L
                },
                "bufferPoolUsedBytes" to ((memory?.get("bufferPools") as? Collection<*>)?.sumOf {
                    ((it as? Map<*, *>)?.get("usedBytes") as? Number)?.toLong() ?: 0L
                }),
                "bufferPoolCapacityBytes" to ((memory?.get("bufferPools") as? Collection<*>)?.sumOf {
                    ((it as? Map<*, *>)?.get("totalCapacityBytes") as? Number)?.toLong() ?: 0L
                }),
                "jitCompilationTimeMs" to (snapshot["jitCompilation"] as? Map<*, *>)?.get("totalCompilationTimeMs"),
                "classpathMissingEntries" to ((java?.get("classpathAnalytics") as? Map<*, *>)?.get("missingEntryCount")),
                "classpathExistingBytes" to ((java?.get("classpathAnalytics") as? Map<*, *>)?.get("totalExistingBytes")),
                "modulePathMissingEntries" to ((java?.get("modulePathAnalytics") as? Map<*, *>)?.get("missingEntryCount")),
                "allowedCpuCount" to ((snapshot["processAffinity"] as? Map<*, *>)?.get("allowedCpuCount")),
                "largestFileSystem" to fileSystems.orEmpty()
                    .maxByOrNull { ((it as? Map<*, *>)?.get("totalSpaceBytes") as? Number)?.toLong() ?: 0L },
                "networkInterfacesUp" to ((snapshot["networkAnalytics"] as? Map<*, *>)?.get("upCount")),
                "networkAddresses" to ((snapshot["networkAnalytics"] as? Map<*, *>)?.get("totalAddressCount")),
                "networkReceiveBytes" to ((snapshot["networkAnalytics"] as? Map<*, *>)?.get("totalReceiveBytes")),
                "networkTransmitBytes" to ((snapshot["networkAnalytics"] as? Map<*, *>)?.get("totalTransmitBytes")),
                "networkReceiveErrors" to ((snapshot["networkAnalytics"] as? Map<*, *>)?.get("totalReceiveErrors")),
                "networkTransmitErrors" to ((snapshot["networkAnalytics"] as? Map<*, *>)?.get("totalTransmitErrors")),
                "networkReceiveDrops" to ((snapshot["networkAnalytics"] as? Map<*, *>)?.get("totalReceiveDrops")),
                "networkTransmitDrops" to ((snapshot["networkAnalytics"] as? Map<*, *>)?.get("totalTransmitDrops")),
                "networkDownInterfaceCount" to ((snapshot["networkAnalytics"] as? Map<*, *>)?.get("operStateDownCount")),
                "networkMaximumLinkSpeedMbps" to ((snapshot["networkAnalytics"] as? Map<*, *>)?.get("maximumLinkSpeedMbps")),
                "networkAverageLinkSpeedMbps" to ((snapshot["networkAnalytics"] as? Map<*, *>)?.get("averageLinkSpeedMbps")),
                "pressureStall" to snapshot["pressureStall"],
                "kernelLimits" to snapshot["kernelLimits"],
                "systemScheduling" to snapshot["systemScheduling"],
                "systemMemory" to snapshot["systemMemory"],
                "containerIo" to snapshot["containerIo"],
                "processSecurity" to snapshot["processSecurity"],
                "processNamespaces" to snapshot["processNamespaces"],
                "containerEffectiveCpuSet" to ((snapshot["runtimeEnvironment"] as? Map<*, *>)?.get("effectiveCpuSet")),
                "containerEffectiveCpuCount" to ((snapshot["runtimeEnvironment"] as? Map<*, *>)?.get("effectiveCpuCount")),
                "containerMemorySwapLimitBytes" to ((snapshot["runtimeEnvironment"] as? Map<*, *>)?.get("memorySwapLimitBytes")),
                "containerMemorySwapCurrentBytes" to ((snapshot["runtimeEnvironment"] as? Map<*, *>)?.get("memorySwapCurrentBytes")),
                "containerCpuWeight" to ((snapshot["runtimeEnvironment"] as? Map<*, *>)?.get("cpuWeight")),
                "containerCpuMaxPolicy" to ((snapshot["runtimeEnvironment"] as? Map<*, *>)?.get("cpuMaxPolicy")),
                "containerPidsCurrent" to ((snapshot["runtimeEnvironment"] as? Map<*, *>)?.get("pidsCurrent")),
                "containerPidsMaximum" to ((snapshot["runtimeEnvironment"] as? Map<*, *>)?.get("pidsMaximum")),
                "processReadBytes" to ((snapshot["processIo"] as? Map<*, *>)?.get("read_bytes")),
                "processWriteBytes" to ((snapshot["processIo"] as? Map<*, *>)?.get("write_bytes")),
                "processTcpEstablished" to ((snapshot["processNetwork"] as? Map<*, *>)?.get("tcpEstablished")),
                "processTcpListening" to ((snapshot["processNetwork"] as? Map<*, *>)?.get("tcpListening")),
                "processUdpSockets" to ((snapshot["processNetwork"] as? Map<*, *>)?.get("udpSockets")),
                "processUnixSockets" to ((snapshot["processNetwork"] as? Map<*, *>)?.get("unixSockets")),
                "processTcpStateCounts" to ((snapshot["processNetwork"] as? Map<*, *>)?.get("tcpStateCounts")),
                "processUdpStateCounts" to ((snapshot["processNetwork"] as? Map<*, *>)?.get("udpStateCounts")),
                "processPssBytes" to ((snapshot["processMemoryMaps"] as? Map<*, *>)?.get("Pss")),
                "processPrivateCleanBytes" to ((snapshot["processMemoryMaps"] as? Map<*, *>)?.get("Private_Clean")),
                "processPrivateDirtyBytes" to ((snapshot["processMemoryMaps"] as? Map<*, *>)?.get("Private_Dirty")),
                "processSharedCleanBytes" to ((snapshot["processMemoryMaps"] as? Map<*, *>)?.get("Shared_Clean")),
                "processSharedDirtyBytes" to ((snapshot["processMemoryMaps"] as? Map<*, *>)?.get("Shared_Dirty")),
                "openFileDescriptorCount" to ((snapshot["processFileDescriptors"] as? Map<*, *>)?.get("total")),
                "openFileDescriptorCategories" to ((snapshot["processFileDescriptors"] as? Map<*, *>)?.get("categories")),
                "processFileDescriptorUsedRatio" to processDescriptorRatio(snapshot),
                "runnableThreads" to threadStateCount(snapshot, "RUNNABLE"),
                "blockedThreads" to threadStateCount(snapshot, "BLOCKED"),
                "waitingThreads" to threadStateCount(snapshot, "WAITING"),
                "timedWaitingThreads" to threadStateCount(snapshot, "TIMED_WAITING"),
                "daemonThreadCountObserved" to ((threads?.get("analytics") as? Map<*, *>)?.get("daemonCount")),
                "nonDaemonThreadCountObserved" to ((threads?.get("analytics") as? Map<*, *>)?.get("nonDaemonCount")),
                "threadGroupCounts" to ((threads?.get("analytics") as? Map<*, *>)?.get("threadGroupCounts")),
                "longWaitingThreadCount" to (((threads?.get("analytics") as? Map<*, *>)?.get("longWaitingThreads") as? Collection<*>)?.size),
                "blockedTimeMs" to ((snapshot["threads"] as? Map<*, *>)?.get("contention") as? Map<*, *>)?.get("blockedTimeMs"),
                "waitedTimeMs" to ((snapshot["threads"] as? Map<*, *>)?.get("contention") as? Map<*, *>)?.get("waitedTimeMs"),
                "loadAverage" to snapshot["loadAverage"],
                "processRss" to (snapshot["processStatus"] as? Map<*, *>)?.get("VmRSS"),
                "processPeakRss" to (snapshot["processStatus"] as? Map<*, *>)?.get("VmPeak"),
                "processVirtualMemory" to (snapshot["processStatus"] as? Map<*, *>)?.get("VmSize"),
                "processDataMemory" to (snapshot["processStatus"] as? Map<*, *>)?.get("VmData"),
                "processSwap" to (snapshot["processStatus"] as? Map<*, *>)?.get("VmSwap"),
                "processRssBytes" to processStatusBytes(snapshot, "VmRSS"),
                "processPeakRssBytes" to processStatusBytes(snapshot, "VmPeak"),
                "processVirtualMemoryBytes" to processStatusBytes(snapshot, "VmSize"),
                "processDataMemoryBytes" to processStatusBytes(snapshot, "VmData"),
                "processSwapBytes" to processStatusBytes(snapshot, "VmSwap"),
                "processNativeThreadCount" to (snapshot["processStatus"] as? Map<*, *>)?.get("Threads"),
                "voluntaryContextSwitches" to (snapshot["processStatus"] as? Map<*, *>)?.get("voluntary_ctxt_switches"),
                "nonVoluntaryContextSwitches" to (snapshot["processStatus"] as? Map<*, *>)?.get("nonvoluntary_ctxt_switches"),
                "fileSystemCount" to fileSystems?.size,
                "writableFileSystemCount" to fileSystems?.count { (it as? Map<*, *>)?.get("writable") == true },
                "readOnlyFileSystemCount" to fileSystems?.count { (it as? Map<*, *>)?.get("readOnly") == true },
                "freeSpaceBytes" to fileSystems?.sumOf {
                    ((it as? Map<*, *>)?.get("freeSpaceBytes") as? Number)?.toLong() ?: 0L
                },
                "fileSystemTypeCounts" to fileSystems.orEmpty()
                    .mapNotNull { (it as? Map<*, *>)?.get("fileSystemType")?.toString() }
                    .groupingBy { it }
                    .eachCount()
                    .toSortedMap(),
                "readOnlyFileSystemTypes" to fileSystems.orEmpty()
                    .filter { (it as? Map<*, *>)?.get("readOnly") == true }
                    .mapNotNull { (it as? Map<*, *>)?.get("fileSystemType")?.toString() }
                    .distinct()
                    .sorted(),
                "mountCount" to ((snapshot["mounts"] as? Map<*, *>)?.get("total")),
                "readOnlyMountCount" to ((snapshot["mounts"] as? Map<*, *>)?.get("readOnlyCount")),
                "mountTypeCounts" to ((snapshot["mounts"] as? Map<*, *>)?.get("typeCounts")),
                "totalSpaceBytes" to fileSystems?.sumOf {
                    ((it as? Map<*, *>)?.get("totalSpaceBytes") as? Number)?.toLong() ?: 0L
                },
                "usedSpaceBytes" to fileSystems?.sumOf {
                    val filesystem = it as? Map<*, *>
                    val total = (filesystem?.get("totalSpaceBytes") as? Number)?.toLong() ?: 0L
                    val free = (filesystem?.get("freeSpaceBytes") as? Number)?.toLong() ?: 0L
                    (total - free).coerceAtLeast(0L)
                },
                "highestDiskUsedRatio" to (snapshot["health"] as? Map<*, *>)?.get("highestDiskUsedRatio"),
                "maxOpenFiles" to processLimit(snapshot, "maxOpenFiles", "hard"),
                "maxProcesses" to processLimit(snapshot, "maxProcesses", "hard"),
                "processPriority" to (snapshot["processScheduling"] as? Map<*, *>)?.get("priority"),
                "processNice" to (snapshot["processScheduling"] as? Map<*, *>)?.get("nice"),
                "processCpuNumber" to (snapshot["processScheduling"] as? Map<*, *>)?.get("processor"),
            ),
            "runtimeDistribution" to linkedMapOf(
                "uptimeSeconds" to java?.get("uptimeSeconds"),
                "classPathEntryCount" to java?.get("classPathEntryCount"),
                "modulePathEntryCount" to java?.get("modulePathEntryCount"),
                "bootModuleCount" to java?.get("bootModuleCount"),
                "systemPropertyCount" to java?.get("systemPropertyCount"),
                "environmentVariableNameCount" to (snapshot["environmentVariableNames"] as? Collection<*>)?.size,
            ),
            "distributions" to linkedMapOf(
                "threadStates" to threadStates.orEmpty(),
                "cpuPressure" to linkedMapOf(
                    "process" to pressureBucket(health?.get("processCpuLoad")),
                    "system" to pressureBucket(health?.get("systemCpuLoad")),
                ),
                "memory" to linkedMapOf(
                    "heap" to pressureBucket(health?.get("heapUsedRatio")),
                    "nonHeap" to nonHeapPressure(nonHeap),
                ),
                "fileSystems" to fileSystems.orEmpty().mapNotNull { entry ->
                    val data = entry as? Map<*, *> ?: return@mapNotNull null
                    linkedMapOf(
                        "path" to data["path"],
                        "usedRatio" to data["usedRatio"],
                        "pressure" to pressureBucket(data["usedRatio"]),
                        "writable" to data["writable"],
                        "readOnly" to data["readOnly"],
                        "fileSystemType" to data["fileSystemType"],
                    )
                },
                "garbageCollectors" to gc.orEmpty().mapNotNull { entry ->
                    val data = entry as? Map<*, *> ?: return@mapNotNull null
                    linkedMapOf(
                        "name" to data["name"],
                        "collections" to data["collectionCount"],
                        "collectionTimeMs" to data["collectionTimeMs"],
                        "timeRatio" to data["timeRatio"],
                        "pressure" to pressureBucket(data["timeRatio"]),
                    )
                },
                "topCpuThreads" to topCpuThreads.orEmpty().take(20),
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
                "topCpuThreads" to topCpuThreads?.size,
                "classLoadEvents" to classes?.get("totalLoadedCount"),
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

    private fun pressureBucket(value: Any?): String {
        val number = (value as? Number)?.toDouble() ?: return "unknown"
        return when {
            number <= 0.70 -> "normal"
            number <= 0.90 -> "elevated"
            else -> "critical"
        }
    }

    private fun nonHeapPressure(value: Map<*, *>?): String {
        val used = (value?.get("usedBytes") as? Number)?.toDouble() ?: return "unknown"
        val committed = (value?.get("committedBytes") as? Number)?.toDouble() ?: return "unknown"
        return pressureBucket((used / committed.coerceAtLeast(1.0)).coerceIn(0.0, 1.0))
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
            val peak = pool.peakUsage
            val collection = pool.collectionUsage
            linkedMapOf(
                "name" to pool.name,
                "type" to pool.type.toString(),
                "usedMb" to (usage?.used ?: 0L) / (1024 * 1024),
                "maxMb" to (usage?.max ?: 0L) / (1024 * 1024),
                "peakUsedBytes" to peak?.used,
                "peakMaxBytes" to peak?.max,
                "collectionUsedBytes" to collection?.used,
                "collectionMaxBytes" to collection?.max,
                "usedRatio" to usage?.let { current ->
                    current.max.takeIf { it > 0L }?.let { current.used.toDouble() / it }
                },
            )
        }
    }

    private fun collectBufferPools(): List<Map<String, Any?>> = runCatching {
        ManagementFactory.getPlatformMXBeans(BufferPoolMXBean::class.java).map { pool ->
            linkedMapOf(
                "name" to pool.name,
                "count" to pool.count,
                "totalCapacityBytes" to pool.totalCapacity,
                "usedBytes" to pool.memoryUsed,
            )
        }
    }.getOrDefault(emptyList())

    private fun collectJitCompilation(): Map<String, Any?> = runCatching {
        val bean: CompilationMXBean = ManagementFactory.getCompilationMXBean()
            ?: return@runCatching emptyMap()
        linkedMapOf(
            "name" to bean.name,
            "totalCompilationTimeMs" to bean.totalCompilationTime,
            "monitoringSupported" to bean.isCompilationTimeMonitoringSupported,
        )
    }.getOrDefault(emptyMap())

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
            val store = runCatching { Files.getFileStore(Paths.get(root.absolutePath)) }.getOrNull()
            linkedMapOf(
                "path" to root.absolutePath,
                "totalSpaceBytes" to root.totalSpace,
                "freeSpaceBytes" to root.freeSpace,
                "usableSpaceBytes" to root.usableSpace,
                "freeSpaceMb" to (root.freeSpace / (1024 * 1024)),
                "usedRatio" to root.totalSpace.takeIf { it > 0L }?.let {
                    (it - root.freeSpace).toDouble() / it
                },
                "readOnly" to store?.isReadOnly,
                "fileSystemType" to runCatching { store?.type() }.getOrNull(),
                "writable" to runCatching { root.canWrite() }.getOrDefault(false),
            )
        }
    }

    private fun collectMountAnalytics(): Map<String, Any?> = runCatching {
        val mounts = File("/proc/self/mountinfo").takeIf(File::isFile)?.readLines().orEmpty()
        val details = mounts.mapNotNull { line ->
            val separator = line.indexOf(" - ")
            if (separator <= 0) return@mapNotNull null
            val left = line.substring(0, separator).split(' ')
            val right = line.substring(separator + 3).split(' ')
            val mountPoint = left.getOrNull(4)?.replace("\\040", " ") ?: return@mapNotNull null
            val options = left.getOrNull(5)?.split(',').orEmpty()
            linkedMapOf<String, Any?>(
                "path" to mountPoint,
                "readOnly" to options.contains("ro"),
                "type" to right.firstOrNull(),
                "source" to right.getOrNull(1),
            )
        }
        linkedMapOf(
            "total" to details.size,
            "readOnlyCount" to details.count { it["readOnly"] == true },
            "typeCounts" to details.mapNotNull { it["type"]?.toString() }
                .groupingBy { it }.eachCount().toSortedMap(),
            "overlayCount" to details.count { it["type"] == "overlay" },
            "details" to details.take(MAX_MOUNT_DETAILS),
        )
    }.getOrDefault(emptyMap())

    private fun collectHealth(
        memory: java.lang.management.MemoryMXBean,
        operatingSystem: java.lang.management.OperatingSystemMXBean,
    ): Map<String, Any?> {
        val heap = memory.heapMemoryUsage
        val heapRatio = heap.max.takeIf { it > 0L }?.let { heap.used.toDouble() / it }
        val processCpu = readDouble(operatingSystem, "getProcessCpuLoad")
        val systemCpu = readDouble(operatingSystem, "getCpuLoad")
        val totalPhysical = readLong(operatingSystem, "getTotalMemorySize")
        val freePhysical = readLong(operatingSystem, "getFreeMemorySize")
        val physicalUsedRatio = if (totalPhysical != null && freePhysical != null && totalPhysical > 0L) {
            ((totalPhysical - freePhysical).coerceAtLeast(0L)).toDouble() / totalPhysical
        } else null
        val totalSwap = readLong(operatingSystem, "getTotalSwapSpaceSize")
        val freeSwap = readLong(operatingSystem, "getFreeSwapSpaceSize")
        val swapUsedRatio = if (totalSwap != null && freeSwap != null && totalSwap > 0L) {
            ((totalSwap - freeSwap).coerceAtLeast(0L)).toDouble() / totalSwap
        } else null
        val openDescriptors = readLong(operatingSystem, "getOpenFileDescriptorCount")
        val maxDescriptors = readLong(operatingSystem, "getMaxFileDescriptorCount")
        val descriptorUsedRatio = if (openDescriptors != null && maxDescriptors != null && maxDescriptors > 0L) {
            openDescriptors.toDouble() / maxDescriptors
        } else null
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
            "physicalMemoryUsedRatio" to physicalUsedRatio,
            "swapUsedRatio" to swapUsedRatio,
            "fileDescriptorUsedRatio" to descriptorUsedRatio,
            "highestDiskUsedRatio" to diskRatios.maxOrNull(),
            "heapPressure" to pressure(heapRatio),
            "processCpuPressure" to pressure(processCpu),
            "systemCpuPressure" to pressure(systemCpu),
            "physicalMemoryPressure" to pressure(physicalUsedRatio),
            "swapPressure" to pressure(swapUsedRatio),
            "fileDescriptorPressure" to pressure(descriptorUsedRatio),
            "diskPressure" to pressure(diskRatios.maxOrNull()),
        )
    }

    private fun collectionAnalytics(): Map<String, Any?> = linkedMapOf(
        "sampleCount" to collectionHistory.size,
        "healthySamples" to collectionHistory.count { it["status"] == "healthy" },
        "attentionSamples" to collectionHistory.count { it["status"] == "attention" },
        "criticalSamples" to collectionHistory.count { it["status"] == "critical" },
        "samplesWithSignals" to collectionHistory.count {
            ((it["signalCount"] as? Number)?.toInt() ?: 0) > 0
        },
        "signalCodeFrequency" to collectionHistory
            .flatMap { (it["signalCodes"] as? Collection<*>)?.mapNotNull(Any?::toString).orEmpty() }
            .groupingBy { it }
            .eachCount()
            .toSortedMap(),
        "averageDurationMs" to collectionHistory.mapNotNull {
            (it["durationMs"] as? Number)?.toLong()
        }.average().takeIf { collectionHistory.isNotEmpty() },
        "trend" to linkedMapOf(
            "heapUsedRatioDelta" to numericDelta("heapUsedRatio"),
            "heapUsedBytesDelta" to numericDelta("heapUsedBytes"),
            "nonHeapUsedBytesDelta" to numericDelta("nonHeapUsedBytes"),
            "highestMemoryPoolRatioDelta" to numericDelta("highestMemoryPoolRatio"),
            "pressuredMemoryPoolCountDelta" to numericDelta("pressuredMemoryPoolCount"),
            "processCpuLoadDelta" to numericDelta("processCpuLoad"),
            "systemCpuLoadDelta" to numericDelta("systemCpuLoad"),
            "highestDiskUsedRatioDelta" to numericDelta("highestDiskUsedRatio"),
            "loadedClassCountDelta" to numericDelta("loadedClassCount"),
            "totalLoadedClassCountDelta" to numericDelta("totalLoadedClassCount"),
            "unloadedClassCountDelta" to numericDelta("unloadedClassCount"),
            "readBytesDelta" to numericDelta("readBytes"),
            "writeBytesDelta" to numericDelta("writeBytes"),
            "tcpEstablishedDelta" to numericDelta("tcpEstablished"),
            "tcpListeningDelta" to numericDelta("tcpListening"),
            "udpSocketsDelta" to numericDelta("udpSockets"),
            "unixSocketsDelta" to numericDelta("unixSockets"),
            "processPssBytesDelta" to numericDelta("processPssBytes"),
            "openFileDescriptorCountDelta" to numericDelta("openFileDescriptorCount"),
            "containerMemoryCurrentDelta" to numericDelta("containerMemoryCurrent"),
            "containerCpuThrottledDelta" to numericDelta("containerCpuThrottled"),
            "containerMemoryHighEventsDelta" to numericDelta("containerMemoryHighEvents"),
            "containerMemoryMaxEventsDelta" to numericDelta("containerMemoryMaxEvents"),
            "containerMemoryOomEventsDelta" to numericDelta("containerMemoryOomEvents"),
            "containerMemoryOomKillEventsDelta" to numericDelta("containerMemoryOomKillEvents"),
            "processRssDelta" to numericDelta("processRss"),
            "processPeakRssDelta" to numericDelta("processPeakRss"),
            "voluntaryContextSwitchesDelta" to numericDelta("voluntaryContextSwitches"),
            "nonVoluntaryContextSwitchesDelta" to numericDelta("nonVoluntaryContextSwitches"),
            "runnableThreadsDelta" to numericDelta("runnableThreads"),
            "blockedThreadsDelta" to numericDelta("blockedThreads"),
            "waitingThreadsDelta" to numericDelta("waitingThreads"),
            "timedWaitingThreadsDelta" to numericDelta("timedWaitingThreads"),
            "blockedTimeMsDelta" to numericDelta("blockedTimeMs"),
            "waitedTimeMsDelta" to numericDelta("waitedTimeMs"),
            "blockedCountDelta" to numericDelta("blockedCount"),
            "waitedCountDelta" to numericDelta("waitedCount"),
            "gcCollectionCountDelta" to numericDelta("gcCollectionCount"),
            "gcCollectionTimeMsDelta" to numericDelta("gcCollectionTimeMs"),
            "threadCountDelta" to numericDelta("threadCount"),
            "daemonThreadCountDelta" to numericDelta("daemonThreadCount"),
            "peakThreadCountDelta" to numericDelta("peakThreadCount"),
            "freePhysicalMemoryBytesDelta" to numericDelta("freePhysicalMemoryBytes"),
            "freeSwapBytesDelta" to numericDelta("freeSwapBytes"),
            "hostUptimeSecondsDelta" to numericDelta("hostUptimeSeconds"),
            "fileDescriptorOpenDelta" to numericDelta("fileDescriptorOpen"),
            "processUserCpuTicksDelta" to numericDelta("processUserCpuTicks"),
            "processSystemCpuTicksDelta" to numericDelta("processSystemCpuTicks"),
            "durationMsDelta" to numericDelta("durationMs"),
            "bufferPoolUsedBytesDelta" to numericDelta("bufferPoolUsedBytes"),
            "jitCompilationTimeMsDelta" to numericDelta("jitCompilationTimeMs"),
            "networkReceiveBytesDelta" to numericDelta("networkReceiveBytes"),
            "networkTransmitBytesDelta" to numericDelta("networkTransmitBytes"),
            "networkReceiveErrorsDelta" to numericDelta("networkReceiveErrors"),
            "networkTransmitErrorsDelta" to numericDelta("networkTransmitErrors"),
            "networkReceiveDropsDelta" to numericDelta("networkReceiveDrops"),
            "networkTransmitDropsDelta" to numericDelta("networkTransmitDrops"),
        ),
    )

    private fun numericDelta(key: String): Double? {
        val values = collectionHistory.mapNotNull { (it[key] as? Number)?.toDouble() }
        if (values.size < 2) return null
        return values.last() - values.first()
    }

    private fun gcTotal(snapshot: Map<String, Any?>, key: String): Long =
        ((snapshot["memory"] as? Map<*, *>)?.get("garbageCollectors") as? Collection<*>)
            ?.sumOf { ((it as? Map<*, *>)?.get(key) as? Number)?.toLong() ?: 0L }
            ?: 0L

    private fun threadStateCount(snapshot: Map<String, Any?>, state: String): Int =
        (((snapshot["threads"] as? Map<*, *>)?.get("stateCounts") as? Map<*, *>)?.get(state) as? Number)
            ?.toInt() ?: 0

    private fun memoryPoolRatios(snapshot: Map<String, Any?>): List<Double> =
        ((snapshot["memory"] as? Map<*, *>)?.get("pools") as? Collection<*>)
            ?.mapNotNull { ((it as? Map<*, *>)?.get("usedRatio") as? Number)?.toDouble() }
            .orEmpty()

    private fun memoryPoolMaxRatio(snapshot: Map<String, Any?>): Double? = memoryPoolRatios(snapshot).maxOrNull()

    private fun memoryPoolPressureCount(snapshot: Map<String, Any?>): Int =
        memoryPoolRatios(snapshot).count { it >= MEMORY_POOL_PRESSURE_THRESHOLD }

    private fun containerMemoryRatio(snapshot: Map<String, Any?>): Double? {
        val environment = snapshot["runtimeEnvironment"] as? Map<*, *> ?: return null
        val current = (environment["memoryCurrentBytes"] as? Number)?.toDouble() ?: return null
        val limit = (environment["memoryLimitBytes"] as? Number)?.toDouble() ?: return null
        return limit.takeIf { it > 0.0 }?.let { current / it }
    }

    private fun containerCpuQuota(snapshot: Map<String, Any?>): Double? {
        val environment = snapshot["runtimeEnvironment"] as? Map<*, *> ?: return null
        val quota = (environment["cpuQuotaMicros"] as? Number)?.toDouble() ?: return null
        val period = (environment["cpuPeriodMicros"] as? Number)?.toDouble() ?: return null
        return period.takeIf { it > 0.0 }?.let { quota / it }
    }

    private fun dominantGcCollector(snapshot: Map<String, Any?>): Map<*, *>? =
        ((snapshot["memory"] as? Map<*, *>)?.get("garbageCollectors") as? Collection<*>)
            ?.filterIsInstance<Map<*, *>>()
            ?.maxByOrNull { (it["collectionTimeMs"] as? Number)?.toLong() ?: 0L }

    private fun pressure(value: Double?): String? = value?.let {
        when {
            it >= 0.90 -> "critical"
            it >= 0.75 -> "elevated"
            else -> "normal"
        }
    }

    private fun descriptorRatio(descriptors: Map<*, *>?): Double? {
        val open = (descriptors?.get("open") as? Number)?.toDouble() ?: return null
        val max = (descriptors["max"] as? Number)?.toDouble() ?: return null
        return max.takeIf { it > 0.0 }?.let { open / it }
    }

    private fun processDescriptorRatio(snapshot: Map<String, Any?>): Double? {
        val open = ((snapshot["processFileDescriptors"] as? Map<*, *>)?.get("total") as? Number)
            ?.toDouble() ?: return null
        val limit = processLimit(snapshot, "maxOpenFiles", "soft") as? Number ?: return null
        return limit.toDouble().takeIf { it > 0.0 }?.let { open / it }
    }

    private fun hasData(value: Any?): Boolean = when (value) {
        is Map<*, *> -> value.isNotEmpty()
        is Collection<*> -> value.isNotEmpty()
        null -> false
        else -> true
    }

    private fun swapUsedBytes(cpu: Map<*, *>?): Long? {
        val total = (cpu?.get("totalSwapBytes") as? Number)?.toLong() ?: return null
        val free = (cpu["freeSwapBytes"] as? Number)?.toLong() ?: return null
        return (total - free).coerceAtLeast(0L)
    }

    private fun swapUsedRatio(cpu: Map<*, *>?): Double? {
        val total = (cpu?.get("totalSwapBytes") as? Number)?.toDouble() ?: return null
        val free = (cpu["freeSwapBytes"] as? Number)?.toDouble() ?: return null
        return total.takeIf { it > 0.0 }?.let { ((it - free).coerceAtLeast(0.0)) / it }
    }

    private fun threadStateCounts(bean: java.lang.management.ThreadMXBean): Map<String, Int> {
        val counts = linkedMapOf<String, Int>()
        bean.getThreadInfo(bean.allThreadIds)?.forEach { info ->
            if (info != null) counts.merge(info.threadState.name, 1, Int::plus)
        }
        return counts
    }

    private fun deadlockedThreadDetails(
        bean: java.lang.management.ThreadMXBean,
        ids: LongArray?,
    ): List<Map<String, Any?>> = ids?.let { deadlockedIds ->
        bean.getThreadInfo(deadlockedIds)?.mapNotNull { info ->
            info?.let {
                linkedMapOf<String, Any?>(
                    "id" to it.threadId,
                    "name" to it.threadName.take(256),
                    "state" to it.threadState.name,
                    "lockName" to it.lockName,
                    "lockOwnerId" to it.lockOwnerId.takeIf { ownerId -> ownerId >= 0L },
                    "lockOwnerName" to it.lockOwnerName,
                )
            }
        }.orEmpty()
    }.orEmpty()

    private fun threadContention(bean: java.lang.management.ThreadMXBean): Map<String, Any?> {
        val infos = bean.getThreadInfo(bean.allThreadIds)?.filterNotNull().orEmpty()
        return linkedMapOf(
            "supported" to bean.isThreadContentionMonitoringSupported,
            "enabled" to bean.isThreadContentionMonitoringEnabled,
            "blockedThreadCount" to infos.count { it.blockedCount > 0 },
            "waitingThreadCount" to infos.count { it.waitedCount > 0 },
            "blockedCount" to infos.sumOf { it.blockedCount },
            "waitedCount" to infos.sumOf { it.waitedCount },
            "blockedTimeMs" to infos.map { it.blockedTime }.filter { it >= 0L }.sum(),
            "waitedTimeMs" to infos.map { it.waitedTime }.filter { it >= 0L }.sum(),
        )
    }

    private fun collectThreadAnalytics(bean: java.lang.management.ThreadMXBean): Map<String, Any?> = runCatching {
        val infos = bean.getThreadInfo(bean.allThreadIds)?.filterNotNull().orEmpty()
        val liveThreads = Thread.getAllStackTraces().keys.associateBy { it.id }
        val stateCounts = infos.groupingBy { it.threadState.name }.eachCount().toSortedMap()
        val groupCounts = infos.groupingBy { liveThreads[it.threadId]?.threadGroup?.name ?: "unknown" }
            .eachCount().toSortedMap()
        val longWaiting = infos.asSequence()
            .filter { it.threadState == Thread.State.WAITING || it.threadState == Thread.State.TIMED_WAITING }
            .map { info ->
                linkedMapOf<String, Any?>(
                    "id" to info.threadId,
                    "name" to info.threadName.take(256),
                    "state" to info.threadState.name,
                    "daemon" to (liveThreads[info.threadId]?.isDaemon ?: false),
                    "waitedCount" to info.waitedCount,
                    "waitedTimeMs" to info.waitedTime.takeIf { it >= 0L },
                )
            }
            .sortedByDescending { (it["waitedTimeMs"] as? Number)?.toLong() ?: 0L }
            .take(MAX_LONG_WAITING_THREADS)
            .toList()
        linkedMapOf(
            "daemonCount" to liveThreads.values.count { it.isDaemon },
            "nonDaemonCount" to liveThreads.values.count { !it.isDaemon },
            "stateCounts" to stateCounts,
            "threadGroupCounts" to groupCounts,
            "longWaitingThreads" to longWaiting,
        )
    }.getOrDefault(emptyMap())

    private fun topCpuThreads(bean: java.lang.management.ThreadMXBean): List<Map<String, Any?>> {
        if (!bean.isThreadCpuTimeSupported) return emptyList()
        val ids = bean.allThreadIds
        val samples = ids.asSequence().mapNotNull { id: Long ->
            val cpuNanos = runCatching { bean.getThreadCpuTime(id) }.getOrDefault(-1L)
            if (cpuNanos < 0L) return@mapNotNull null
            val info = bean.getThreadInfo(id) ?: return@mapNotNull null
            linkedMapOf<String, Any?>(
                "id" to id,
                "name" to info.threadName.take(256),
                "state" to info.threadState.name,
                "cpuTimeNanos" to cpuNanos,
            )
        }.toList()
        val totalCpuNanos = samples.sumOf { (it["cpuTimeNanos"] as Number).toLong() }
        return samples.map { sample ->
            val cpuNanos = (sample["cpuTimeNanos"] as Number).toLong()
            sample + ("cpuShare" to cpuNanos.toDouble() / totalCpuNanos.coerceAtLeast(1L))
        }.sortedByDescending { (it["cpuTimeNanos"] as Number).toLong() }
            .take(MAX_CPU_THREADS)
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
        readLong(osMx, "getTotalMemorySize")?.let { result["totalPhysicalMemoryBytes"] = it }
        readLong(osMx, "getFreeMemorySize")?.let { result["freePhysicalMemoryBytes"] = it }
        readLong(osMx, "getTotalSwapSpaceSize")?.let { result["totalSwapBytes"] = it }
        readLong(osMx, "getFreeSwapSpaceSize")?.let { result["freeSwapBytes"] = it }
        return result
    }

    private fun collectOsDistribution(): Map<String, String> = runCatching {
        val properties = File("/etc/os-release").takeIf(File::isFile)?.readLines().orEmpty()
            .mapNotNull { line ->
                val separator = line.indexOf('=')
                if (separator <= 0) return@mapNotNull null
                val key = line.substring(0, separator)
                val value = line.substring(separator + 1).trim().trim('"')
                key to value.take(256)
            }.toMap()
        linkedMapOf<String, String>().apply {
            properties["ID"]?.let { put("id", it) }
            properties["VERSION_ID"]?.let { put("versionId", it) }
            properties["PRETTY_NAME"]?.let { put("name", it) }
        }
    }.getOrDefault(emptyMap())

    private fun readTextFile(path: String): String? = runCatching {
        File(path).takeIf(File::isFile)?.readText()?.trim()?.takeIf(String::isNotBlank)?.take(256)
    }.getOrNull()

    private fun collectCpuTopology(): Map<String, Any?> = runCatching {
        val lines = File("/proc/cpuinfo").takeIf(File::isFile)?.readLines().orEmpty()
        val model = lines.firstOrNull { it.startsWith("model name") }
            ?.substringAfter(':')?.trim()
        val frequency = lines.firstOrNull { it.startsWith("cpu MHz") }
            ?.substringAfter(':')?.trim()?.toDoubleOrNull()
        val flags = lines.firstOrNull { it.startsWith("flags") || it.startsWith("Features") }
            ?.substringAfter(':')?.trim()?.split(Regex("\\s+"))?.filter(String::isNotBlank).orEmpty()
        val physicalCores = lines.filter { it.startsWith("physical id") }
            .mapNotNull { it.substringAfter(':').trim().toIntOrNull() }
            .zip(lines.filter { it.startsWith("core id") }
                .mapNotNull { it.substringAfter(':').trim().toIntOrNull() })
            .toSet()
        val socketCount = lines.filter { it.startsWith("physical id") }
            .mapNotNull { it.substringAfter(':').trim().toIntOrNull() }
            .toSet()
        linkedMapOf(
            "model" to model,
            "logicalProcessorCount" to lines.count { it.startsWith("processor") },
            "physicalCoreCount" to physicalCores.size.takeIf { it > 0 },
            "socketCount" to socketCount.size.takeIf { it > 0 },
            "reportedFrequencyMHz" to frequency,
            "flagCount" to flags.distinct().size,
        )
    }.getOrDefault(emptyMap())

    private fun collectClasspathAnalytics(): Map<String, Any?> {
        return collectPathAnalytics("java.class.path")
    }

    private fun collectJvmArgumentAnalytics(arguments: List<String>): Map<String, Any?> {
        fun count(predicate: (String) -> Boolean) = arguments.count(predicate)
        return linkedMapOf(
            "total" to arguments.size,
            "memoryOptions" to count { it.startsWith("-Xmx") || it.startsWith("-Xms") || it.startsWith("-XX:MaxRAM") },
            "garbageCollectorOptions" to count { it.contains("GC", ignoreCase = true) || it.contains("UseG1", ignoreCase = true) },
            "agentOptions" to count { it.startsWith("-javaagent:") || it.startsWith("-agentlib:") },
            "systemProperties" to count { it.startsWith("-D") },
            "debugOptions" to count { it.startsWith("-agentlib:jdwp") || it.contains("debug", ignoreCase = true) },
            "previewOrModuleOptions" to count { it.contains("--enable-preview") || it.startsWith("--add-") },
        )
    }

    private fun collectPathAnalytics(property: String): Map<String, Any?> {
        val entries = System.getProperty(property)
            ?.split(File.pathSeparatorChar)
            ?.filter(String::isNotBlank)
            .orEmpty()
        val names = entries.map { File(it).name }.filter(String::isNotBlank)
        val paths = entries.map(::File)
        val duplicateNames = names.groupingBy { it }.eachCount()
            .filterValues { it > 1 }
            .keys
            .sorted()
            .take(64)
        return linkedMapOf(
            "entryCount" to entries.size,
            "uniqueNameCount" to names.distinct().size,
            "duplicateNameCount" to duplicateNames.size,
            "duplicateNames" to duplicateNames,
            "existingEntryCount" to paths.count { it.exists() },
            "missingEntryCount" to paths.count { !it.exists() },
            "directoryEntryCount" to paths.count { it.isDirectory },
            "fileEntryCount" to paths.count { it.isFile },
            "totalExistingBytes" to paths.filter(File::isFile).sumOf { it.length() },
            "extensionCounts" to names.map { name -> name.substringAfterLast('.', "none").lowercase() }
                .groupingBy { it }
                .eachCount()
                .toSortedMap(),
        )
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
        "hostUptimeSeconds" to readHostUptimeSeconds(),
        "memoryLimitBytes" to readCgroupLong("/sys/fs/cgroup/memory.max"),
        "memoryCurrentBytes" to readCgroupLong("/sys/fs/cgroup/memory.current"),
        "memorySwapLimitBytes" to readCgroupLong("/sys/fs/cgroup/memory.swap.max"),
        "memorySwapCurrentBytes" to readCgroupLong("/sys/fs/cgroup/memory.swap.current"),
        "cpuWeight" to readCgroupLong("/sys/fs/cgroup/cpu.weight"),
        "cpuMaxPolicy" to readCgroupText("/sys/fs/cgroup/cpu.max"),
        "pidsCurrent" to readCgroupLong("/sys/fs/cgroup/pids.current"),
        "pidsMaximum" to readCgroupLong("/sys/fs/cgroup/pids.max"),
        "cpuQuotaMicros" to readCgroupLong("/sys/fs/cgroup/cpu.max", 0),
        "cpuPeriodMicros" to readCgroupLong("/sys/fs/cgroup/cpu.max", 1),
        "cpuUsageMicros" to readCgroupKey("/sys/fs/cgroup/cpu.stat", "usage_usec"),
        "cpuThrottledMicros" to readCgroupKey("/sys/fs/cgroup/cpu.stat", "throttled_usec"),
        "cpuThrottleEvents" to readCgroupKey("/sys/fs/cgroup/cpu.stat", "nr_throttled"),
        "effectiveCpuSet" to readCgroupText("/sys/fs/cgroup/cpuset.cpus.effective"),
        "effectiveCpuCount" to readCgroupText("/sys/fs/cgroup/cpuset.cpus.effective")?.let(::countCpuSet),
        "memoryHighEvents" to readCgroupKey("/sys/fs/cgroup/memory.events", "high"),
        "memoryMaxEvents" to readCgroupKey("/sys/fs/cgroup/memory.events", "max"),
        "memoryOomEvents" to readCgroupKey("/sys/fs/cgroup/memory.events", "oom"),
        "memoryOomKillEvents" to readCgroupKey("/sys/fs/cgroup/memory.events", "oom_kill"),
    )

    private fun collectProcessIo(): Map<String, Long> = runCatching {
        val file = File("/proc/self/io")
        if (!file.isFile) return@runCatching emptyMap()
        file.readLines().mapNotNull { line ->
            val separator = line.indexOf(':')
            if (separator <= 0) return@mapNotNull null
            val key = line.substring(0, separator).trim()
            val value = line.substring(separator + 1).trim().split(' ').firstOrNull()?.toLongOrNull()
            value?.let { key to it }
        }.toMap()
    }.getOrDefault(emptyMap())

    private fun readHostUptimeSeconds(): Double? = runCatching {
        File("/proc/uptime").takeIf(File::isFile)?.readText()?.trim()
            ?.substringBefore(' ')?.toDoubleOrNull()
    }.getOrNull()

    private fun collectLoadAverage(): Map<String, Any?> = runCatching {
        val values = File("/proc/loadavg").takeIf(File::isFile)?.readText()?.trim()
            ?.split(Regex("\\s+")) ?: return@runCatching emptyMap()
        val runnable = values.getOrNull(3)?.substringBefore('/')?.toIntOrNull()
        val processes = values.getOrNull(3)?.substringAfter('/')?.toIntOrNull()
        val processors = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        val oneMinute = values.getOrNull(0)?.toDoubleOrNull()
        val fiveMinutes = values.getOrNull(1)?.toDoubleOrNull()
        val fifteenMinutes = values.getOrNull(2)?.toDoubleOrNull()
        linkedMapOf(
            "oneMinute" to oneMinute,
            "fiveMinutes" to fiveMinutes,
            "fifteenMinutes" to fifteenMinutes,
            "processorCount" to processors,
            "oneMinutePerProcessor" to oneMinute?.div(processors),
            "fiveMinutesPerProcessor" to fiveMinutes?.div(processors),
            "fifteenMinutesPerProcessor" to fifteenMinutes?.div(processors),
            "runnableProcesses" to runnable,
            "totalProcesses" to processes,
        )
    }.getOrDefault(emptyMap())

    private fun collectPressureStall(): Map<String, Any?> = runCatching {
        linkedMapOf<String, Any?>().apply {
            listOf("cpu", "memory", "io").forEach { resource ->
                val lines = File("/proc/pressure/$resource").takeIf(File::isFile)?.readLines().orEmpty()
                if (lines.isEmpty()) return@forEach
                val values = linkedMapOf<String, Any?>()
                lines.forEach { line ->
                    val parts = line.trim().split(Regex("\\s+"))
                    val category = parts.firstOrNull() ?: return@forEach
                    parts.drop(1).forEach { part ->
                        val key = part.substringBefore('=')
                        val value = part.substringAfter('=', "").toDoubleOrNull()
                        if (value != null) values["${category}${key.replaceFirstChar(Char::uppercase)}"] = value
                    }
                }
                put(resource, values)
            }
        }
    }.getOrDefault(emptyMap())

    private fun collectSystemMemory(): Map<String, Long> = runCatching {
        val keys = setOf(
            "MemTotal", "MemFree", "MemAvailable", "Buffers", "Cached", "SReclaimable",
            "Shmem", "Slab", "PageTables", "CommitLimit", "Committed_AS", "SwapTotal", "SwapFree",
            "AnonPages", "Mapped", "Unevictable", "HugePages_Total", "HugePages_Free",
        )
        File("/proc/meminfo").takeIf(File::isFile)?.readLines().orEmpty().mapNotNull { line ->
            val separator = line.indexOf(':')
            if (separator <= 0) return@mapNotNull null
            val key = line.substring(0, separator)
            if (key !in keys) return@mapNotNull null
            val value = line.substring(separator + 1).trim().split(' ').firstOrNull()?.toLongOrNull()
            value?.let { key to it * 1024L }
        }.toMap()
    }.getOrDefault(emptyMap())

    private fun collectEnvironmentVariableAnalytics(): Map<String, Any?> {
        val names = System.getenv().keys
        val categories = linkedMapOf(
            "java" to names.count { it.contains("JAVA", ignoreCase = true) },
            "path" to names.count { it == "PATH" || it.endsWith("_PATH") },
            "proxy" to names.count { it.contains("PROXY", ignoreCase = true) },
            "cloud" to names.count { it.contains("CLOUD", ignoreCase = true) || it.contains("KUBERNETES", ignoreCase = true) },
            "ci" to names.count { it.contains("CI", ignoreCase = true) || it.contains("BUILD", ignoreCase = true) },
        )
        return linkedMapOf(
            "totalNames" to names.size,
            "categories" to categories,
            "secretLikeNames" to names.count { SECRET_ENV_NAME.matches(it) },
        )
    }

    private fun collectProcessNetwork(): Map<String, Any?> = runCatching {
        val tcp = listOf("/proc/self/net/tcp", "/proc/self/net/tcp6")
            .flatMap { path -> readProcSocketStates(path) }
        val udp = listOf("/proc/self/net/udp", "/proc/self/net/udp6")
            .flatMap { path -> readProcSocketStates(path) }
        val unixSockets = File("/proc/self/net/unix").takeIf(File::isFile)
            ?.readLines()?.drop(1)?.count() ?: 0
        linkedMapOf(
            "tcpSockets" to tcp.size,
            "tcpStateCounts" to tcp.groupingBy { it }.eachCount().toSortedMap(),
            "tcpEstablished" to tcp.count { it == "01" },
            "tcpListening" to tcp.count { it == "0A" },
            "tcpTimeWait" to tcp.count { it == "06" },
            "udpSockets" to udp.size,
            "udpStateCounts" to udp.groupingBy { it }.eachCount().toSortedMap(),
            "unixSockets" to unixSockets,
        )
    }.getOrDefault(emptyMap())

    private fun collectProcessStatus(): Map<String, Long> = runCatching {
        val file = File("/proc/self/status")
        if (!file.isFile) return@runCatching emptyMap()
        file.readLines().mapNotNull { line ->
            val separator = line.indexOf(':')
            if (separator <= 0) return@mapNotNull null
            val key = line.substring(0, separator)
            val value = line.substring(separator + 1).trim().split(' ').firstOrNull()?.toLongOrNull()
            value?.let { key to it }
        }.toMap()
    }.getOrDefault(emptyMap())

    private fun collectProcessAffinity(): Map<String, Any?> = runCatching {
        val lines = File("/proc/self/status").takeIf(File::isFile)?.readLines().orEmpty()
        val allowedList = lines.firstOrNull { it.startsWith("Cpus_allowed_list:") }
            ?.substringAfter(':')?.trim()
        val mask = lines.firstOrNull { it.startsWith("Cpus_allowed:") }
            ?.substringAfter(':')?.trim()
        val count = allowedList?.split(',').orEmpty().sumOf { range ->
            val bounds = range.trim().split('-').mapNotNull(String::toIntOrNull)
            when (bounds.size) {
                1 -> 1
                2 -> (bounds[1] - bounds[0] + 1).coerceAtLeast(0)
                else -> 0
            }
        }.takeIf { it > 0 }
        linkedMapOf(
            "allowedCpuList" to allowedList,
            "allowedCpuMask" to mask,
            "allowedCpuCount" to count,
            "restricted" to (count != null && count < Runtime.getRuntime().availableProcessors()),
        )
    }.getOrDefault(emptyMap())

    private fun collectProcessSecurity(): Map<String, Any?> = runCatching {
        val lines = File("/proc/self/status").takeIf(File::isFile)?.readLines().orEmpty()
        fun value(key: String): String? = lines.firstOrNull { it.startsWith("$key:") }
            ?.substringAfter(':')?.trim()
        linkedMapOf(
            "uid" to value("Uid")?.split(Regex("\\s+"))?.firstOrNull()?.toLongOrNull(),
            "gid" to value("Gid")?.split(Regex("\\s+"))?.firstOrNull()?.toLongOrNull(),
            "effectiveCapabilities" to value("CapEff"),
            "permittedCapabilities" to value("CapPrm"),
            "boundingCapabilities" to value("CapBnd"),
            "noNewPrivileges" to (value("NoNewPrivs") == "1"),
            "seccompMode" to value("Seccomp")?.toIntOrNull(),
            "dumpable" to value("Dumpable")?.toIntOrNull(),
        )
    }.getOrDefault(emptyMap())

    private fun collectProcessNamespaces(): Map<String, String> = runCatching {
        listOf("cgroup", "ipc", "mnt", "net", "pid", "time", "user", "uts")
            .mapNotNull { namespace ->
                val link = File("/proc/self/ns/$namespace").takeIf { it.exists() } ?: return@mapNotNull null
                val target = Files.readSymbolicLink(link.toPath()).toString()
                namespace to target
            }.toMap()
    }.getOrDefault(emptyMap())

    private fun collectProcessMemoryMaps(): Map<String, Long> = runCatching {
        val file = File("/proc/self/smaps_rollup")
        if (!file.isFile) return@runCatching emptyMap()
        file.readLines().mapNotNull { line ->
            val separator = line.indexOf(':')
            if (separator <= 0) return@mapNotNull null
            val key = line.substring(0, separator).trim()
            val value = line.substring(separator + 1).trim().split(' ').firstOrNull()?.toLongOrNull()
            value?.let { key to it * 1024L }
        }.toMap()
    }.getOrDefault(emptyMap())

    private fun collectProcessFileDescriptors(): Map<String, Any?> = runCatching {
        val directory = File("/proc/self/fd")
        if (!directory.isDirectory) return@runCatching emptyMap()
        val categories = directory.listFiles().orEmpty().mapNotNull { descriptor ->
            runCatching { Files.readSymbolicLink(descriptor.toPath()).toString() }.getOrNull()
        }.map { target ->
            when {
                target.startsWith("socket:") -> "socket"
                target.startsWith("pipe:") -> "pipe"
                target.startsWith("anon_inode:") -> "anonInode"
                target.startsWith("/dev/") -> "device"
                else -> "file"
            }
        }
        linkedMapOf(
            "total" to categories.size,
            "categories" to categories.groupingBy { it }.eachCount().toSortedMap(),
        )
    }.getOrDefault(emptyMap())

    private fun collectProcessLimits(): Map<String, Map<String, Any?>> = runCatching {
        val file = File("/proc/self/limits")
        if (!file.isFile) return@runCatching emptyMap()
        file.readLines().drop(1).mapNotNull { line ->
            val match = Regex("^(.+?)\\s+([^\\s]+)\\s+([^\\s]+)\\s*(.*)$").matchEntire(line.trim())
                ?: return@mapNotNull null
            val name = when (match.groupValues[1].trim()) {
                "Max open files" -> "maxOpenFiles"
                "Max processes" -> "maxProcesses"
                "Max address space" -> "maxAddressSpace"
                "Max locked memory" -> "maxLockedMemory"
                "Max stack size" -> "maxStackSize"
                "Max pending signals" -> "maxPendingSignals"
                else -> return@mapNotNull null
            }
            name to linkedMapOf<String, Any?>(
                "soft" to parseLimitValue(match.groupValues[2]),
                "hard" to parseLimitValue(match.groupValues[3]),
                "unit" to match.groupValues[4].trim().ifBlank { null },
            )
        }.toMap()
    }.getOrDefault(emptyMap())

    private fun collectKernelLimits(): Map<String, Any?> = runCatching {
        val fileTable = File("/proc/sys/fs/file-nr").takeIf(File::isFile)?.readText()
            ?.trim()?.split(Regex("\\s+"))?.mapNotNull(String::toLongOrNull).orEmpty()
        linkedMapOf(
            "fileHandlesAllocated" to fileTable.getOrNull(0),
            "fileHandlesUnused" to fileTable.getOrNull(1),
            "fileHandlesMaximum" to fileTable.getOrNull(2),
            "fileHandlesUsedRatio" to fileTable.getOrNull(2)?.takeIf { it > 0L }
                ?.let { max -> (fileTable.getOrNull(0) ?: 0L).toDouble() / max },
            "pidMaximum" to readLongFile("/proc/sys/kernel/pid_max"),
            "threadsMaximum" to readLongFile("/proc/sys/kernel/threads-max"),
            "inotifyUserWatchesMaximum" to readLongFile("/proc/sys/fs/inotify/max_user_watches"),
            "inotifyUserInstancesMaximum" to readLongFile("/proc/sys/fs/inotify/max_user_instances"),
        )
    }.getOrDefault(emptyMap())

    private fun readLongFile(path: String): Long? = runCatching {
        File(path).takeIf(File::isFile)?.readText()?.trim()?.toLongOrNull()
    }.getOrNull()

    private fun parseLimitValue(value: String): Any? = value.toLongOrNull() ?: value

    private fun processLimit(snapshot: Map<String, Any?>, name: String, bound: String): Any? =
        ((snapshot["processLimits"] as? Map<*, *>)?.get(name) as? Map<*, *>)?.get(bound)

    private fun processStatusBytes(snapshot: Map<String, Any?>, key: String): Long? =
        ((snapshot["processStatus"] as? Map<*, *>)?.get(key) as? Number)?.toLong()?.times(1024L)

    private fun collectProcessScheduling(): Map<String, Any?> = runCatching {
        val line = File("/proc/self/stat").takeIf(File::isFile)?.readText()?.trim()
            ?: return@runCatching emptyMap()
        val fields = line.substringAfterLast(") ").split(' ')
        linkedMapOf(
            "state" to fields.getOrNull(0),
            "userCpuTicks" to fields.getOrNull(11)?.toLongOrNull(),
            "systemCpuTicks" to fields.getOrNull(12)?.toLongOrNull(),
            "priority" to fields.getOrNull(15)?.toLongOrNull(),
            "nice" to fields.getOrNull(16)?.toLongOrNull(),
            "threadCount" to fields.getOrNull(17)?.toLongOrNull(),
            "processor" to fields.getOrNull(36)?.toLongOrNull(),
        )
    }.getOrDefault(emptyMap())

    private fun collectSystemScheduling(): Map<String, Long> = runCatching {
        val lines = File("/proc/stat").takeIf(File::isFile)?.readLines().orEmpty()
        val values = linkedMapOf<String, Long>()
        lines.forEach { line ->
            val parts = line.trim().split(Regex("\\s+"))
            val key = parts.firstOrNull() ?: return@forEach
            val value = parts.getOrNull(1)?.toLongOrNull() ?: return@forEach
            when (key) {
                "ctxt" -> values["contextSwitches"] = value
                "intr" -> values["interrupts"] = value
                "processes" -> values["forks"] = value
                "procs_running" -> values["runnableProcesses"] = value
                "procs_blocked" -> values["blockedProcesses"] = value
            }
        }
        values
    }.getOrDefault(emptyMap())

    private fun readProcSocketStates(path: String): List<String> = runCatching {
        File(path).takeIf(File::isFile)?.readLines()?.drop(1)?.mapNotNull { line ->
            line.trim().split(Regex("\\s+")).getOrNull(3)
        }.orEmpty()
    }.getOrDefault(emptyList())

    private fun readCgroupLong(path: String, tokenIndex: Int? = null): Long? = runCatching {
        val value = File(path).takeIf(File::isFile)?.readText()?.trim() ?: return@runCatching null
        val token = tokenIndex?.let { value.split(Regex("\\s+")).getOrNull(it) } ?: value
        token.takeUnless { it == "max" }?.toLongOrNull()?.takeIf { it >= 0L }
    }.getOrNull()

    private fun readCgroupKey(path: String, key: String): Long? = runCatching {
        File(path).takeIf(File::isFile)?.useLines { lines ->
            lines.firstOrNull { it.startsWith("$key ") }
                ?.substringAfter(' ')
                ?.trim()
                ?.toLongOrNull()
        }
    }.getOrNull()

    private fun readCgroupText(path: String): String? = runCatching {
        File(path).takeIf(File::isFile)?.readText()?.trim()?.takeIf(String::isNotBlank)
    }.getOrNull()

    private fun collectContainerIo(): Map<String, Long> = runCatching {
        val keys = setOf("rbytes", "wbytes", "rios", "wios", "dbytes", "dios")
        val totals = linkedMapOf<String, Long>()
        File("/sys/fs/cgroup/io.stat").takeIf(File::isFile)?.readLines().orEmpty().forEach { line ->
            line.split(Regex("\\s+")).drop(1).forEach { entry ->
                val key = entry.substringBefore('=')
                val value = entry.substringAfter('=', "").toLongOrNull() ?: return@forEach
                if (key in keys) totals[key] = (totals[key] ?: 0L) + value
            }
        }
        linkedMapOf(
            "readBytes" to (totals["rbytes"] ?: 0L),
            "writeBytes" to (totals["wbytes"] ?: 0L),
            "readOperations" to (totals["rios"] ?: 0L),
            "writeOperations" to (totals["wios"] ?: 0L),
            "discardBytes" to (totals["dbytes"] ?: 0L),
            "discardOperations" to (totals["dios"] ?: 0L),
        )
    }.getOrDefault(emptyMap())

    private fun countCpuSet(value: String): Int = value.split(',').sumOf { item ->
        val bounds = item.trim().split('-').mapNotNull(String::toIntOrNull)
        when (bounds.size) {
            1 -> 1
            2 -> (bounds[1] - bounds[0] + 1).coerceAtLeast(0)
            else -> 0
        }
    }

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

    private fun readNetworkInterfaceCounters(name: String): Map<String, Long> = runCatching {
        val line = File("/proc/net/dev").takeIf(File::isFile)?.readLines()
            ?.firstOrNull { it.substringBefore(':').trim() == name } ?: return@runCatching emptyMap()
        val values = line.substringAfter(':').trim().split(Regex("\\s+"))
            .mapNotNull(String::toLongOrNull)
        if (values.size < 16) return@runCatching emptyMap()
        linkedMapOf(
            "receiveBytes" to values[0], "receivePackets" to values[1],
            "receiveErrors" to values[2], "receiveDrops" to values[3],
            "transmitBytes" to values[8], "transmitPackets" to values[9],
            "transmitErrors" to values[10], "transmitDrops" to values[11],
        )
    }.getOrDefault(emptyMap())

    private fun readNetworkInterfaceText(name: String, file: String): String? = runCatching {
        File("/sys/class/net/$name/$file").takeIf(File::isFile)?.readText()?.trim()?.takeIf(String::isNotBlank)
    }.getOrNull()

    private fun readNetworkInterfaceLong(name: String, file: String): Long? =
        readNetworkInterfaceText(name, file)?.toLongOrNull()?.takeIf { it >= 0L }

    private fun collectNetworkAnalytics(): Map<String, Any?> = runCatching {
        val interfaces = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
        val details = interfaces.map { nif ->
            val addresses = nif.inetAddresses.toList()
            val counters = readNetworkInterfaceCounters(nif.name)
            linkedMapOf<String, Any?>(
                "name" to nif.name,
                "up" to nif.isUp,
                "loopback" to nif.isLoopback,
                "virtual" to nif.isVirtual,
                "pointToPoint" to nif.isPointToPoint,
                "supportsMulticast" to nif.supportsMulticast(),
                "mtu" to runCatching { nif.mtu }.getOrNull(),
                "operState" to readNetworkInterfaceText(nif.name, "operstate"),
                "linkSpeedMbps" to readNetworkInterfaceLong(nif.name, "speed"),
                "addressCount" to addresses.size,
                "ipv4Count" to addresses.count { it.address.size == 4 },
                "ipv6Count" to addresses.count { it.address.size == 16 },
                "receiveBytes" to counters["receiveBytes"],
                "receivePackets" to counters["receivePackets"],
                "receiveErrors" to counters["receiveErrors"],
                "receiveDrops" to counters["receiveDrops"],
                "transmitBytes" to counters["transmitBytes"],
                "transmitPackets" to counters["transmitPackets"],
                "transmitErrors" to counters["transmitErrors"],
                "transmitDrops" to counters["transmitDrops"],
            )
        }
        linkedMapOf(
            "interfaces" to details,
            "upCount" to details.count { it["up"] == true },
                "downCount" to details.count { it["up"] == false },
                "operStateDownCount" to details.count { it["operState"] == "down" },
            "virtualCount" to details.count { it["virtual"] == true },
            "pointToPointCount" to details.count { it["pointToPoint"] == true },
            "multicastCapableCount" to details.count { it["supportsMulticast"] == true },
            "totalAddressCount" to details.sumOf { (it["addressCount"] as? Number)?.toInt() ?: 0 },
            "ipv4AddressCount" to details.sumOf { (it["ipv4Count"] as? Number)?.toInt() ?: 0 },
            "ipv6AddressCount" to details.sumOf { (it["ipv6Count"] as? Number)?.toInt() ?: 0 },
                "maximumMtu" to details.mapNotNull { (it["mtu"] as? Number)?.toInt() }.maxOrNull(),
                "maximumLinkSpeedMbps" to details.mapNotNull { (it["linkSpeedMbps"] as? Number)?.toLong() }.maxOrNull(),
                "averageLinkSpeedMbps" to details.mapNotNull { (it["linkSpeedMbps"] as? Number)?.toDouble() }
                    .takeIf { it.isNotEmpty() }?.average(),
                "linkStates" to details.groupingBy { it["operState"]?.toString() ?: "unknown" }.eachCount().toSortedMap(),
                "totalReceiveBytes" to details.sumOf { (it["receiveBytes"] as? Number)?.toLong() ?: 0L },
                "totalTransmitBytes" to details.sumOf { (it["transmitBytes"] as? Number)?.toLong() ?: 0L },
                "totalReceiveErrors" to details.sumOf { (it["receiveErrors"] as? Number)?.toLong() ?: 0L },
                "totalTransmitErrors" to details.sumOf { (it["transmitErrors"] as? Number)?.toLong() ?: 0L },
                "totalReceiveDrops" to details.sumOf { (it["receiveDrops"] as? Number)?.toLong() ?: 0L },
                "totalTransmitDrops" to details.sumOf { (it["transmitDrops"] as? Number)?.toLong() ?: 0L },
            )
    }.getOrDefault(emptyMap())

    private companion object {
        const val MAX_JVM_ARGUMENTS = 128
        const val MAX_JVM_ARGUMENT_LENGTH = 1_024
        const val MAX_NETWORK_INTERFACES = 64
        const val MAX_ADDRESSES_PER_INTERFACE = 32
        const val MAX_CPU_THREADS = 20
        const val MAX_LONG_WAITING_THREADS = 20
        const val MAX_MOUNT_DETAILS = 128
        const val GC_PRESSURE_THRESHOLD = 0.25
        const val MEMORY_POOL_PRESSURE_THRESHOLD = 0.90
        val SECRET_JVM_ARGUMENT = Regex(
            "(?i)(?:^|[._-])(?:password|passwd|pwd|secret|token|api[-_]?key|authorization|credential)(?:[._=-]|$)",
        )
        val SECRET_ENV_NAME = Regex(
            "(?i).*(password|passwd|pwd|secret|token|api[-_]?key|authorization|credential).*",
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
