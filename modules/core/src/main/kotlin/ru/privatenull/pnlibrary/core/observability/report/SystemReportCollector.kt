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
import java.security.Security

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
            "bootModules" to ModuleLayer.boot().modules().map { it.name }.sorted().take(512),
            "classLoaderAnalytics" to collectClassLoaderAnalytics(),
            "classpathAnalytics" to collectClasspathAnalytics(),
            "modulePathAnalytics" to collectPathAnalytics("jdk.module.path"),
            "environment" to linkedMapOf(
                "defaultCharset" to java.nio.charset.Charset.defaultCharset().name(),
                "fileEncoding" to System.getProperty("file.encoding", "unknown"),
                "defaultLocale" to Locale.getDefault().toLanguageTag(),
                "defaultZone" to ZoneId.systemDefault().id,
                "lineSeparator" to System.lineSeparator().replace("\r", "\\r").replace("\n", "\\n"),
            ),
            "security" to collectJvmSecurity(),
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
            "cpuFrequency" to collectCpuFrequency(),
            "fileDescriptors" to collectFileDescriptorDetails(osMx),
        )
        data["runtimeEnvironment"] = collectRuntimeEnvironment()
        data["processIo"] = collectProcessIo()
        data["processNetwork"] = collectProcessNetwork()
        data["processStatus"] = collectProcessStatus()
        data["processSignals"] = collectProcessSignals()
        data["processAffinity"] = collectProcessAffinity()
        data["processSecurity"] = collectProcessSecurity()
        data["processNamespaces"] = collectProcessNamespaces()
        data["processOomPolicy"] = collectProcessOomPolicy()
        data["processMemoryMaps"] = collectProcessMemoryMaps()
        data["processAddressSpace"] = collectProcessAddressSpace()
        data["processFileDescriptors"] = collectProcessFileDescriptors()
        data["processLimits"] = collectProcessLimits()
        data["kernelLimits"] = collectKernelLimits()
        data["containerIo"] = collectContainerIo()
        data["processScheduling"] = collectProcessScheduling()
        data["processSchedulerDetails"] = collectProcessSchedulerDetails()
        data["processSchedStat"] = collectProcessSchedStat()
        data["systemScheduling"] = collectSystemScheduling()
        data["loadAverage"] = collectLoadAverage()
        data["pressureStall"] = collectPressureStall()
        data["containerPressureStall"] = collectContainerPressureStall()
        data["systemMemory"] = collectSystemMemory()
        data["thermalZones"] = collectThermalZones()
        data["interrupts"] = collectInterruptAnalytics()
        data["kernelVmStat"] = collectKernelVmStat()
        data["diskStats"] = collectDiskStats()
        data["kernelRuntime"] = collectKernelRuntime()
        data["softIrqs"] = collectSoftIrqAnalytics()
        data["securityRuntime"] = collectSecurityRuntime()
        data["hardwareSensors"] = collectHardwareSensors()
        data["numaMemory"] = collectNumaMemory()
        data["kernelVmPolicy"] = collectKernelVmPolicy()
        data["kernelSchedulerPolicy"] = collectKernelSchedulerPolicy()

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
        data["networkProtocolStats"] = collectNetworkProtocolStats()
        data["networkSoftnetStats"] = collectNetworkSoftnetStats()
        data["networkRoutes"] = collectNetworkRouteAnalytics()
        data["networkResolver"] = collectNetworkResolverAnalytics()
        data["networkNameService"] = collectNetworkNameServiceAnalytics()
        data["kernelNotificationPolicy"] = collectKernelNotificationPolicy()
        data["kernelSysctl"] = collectKernelSysctl()
        data["blockDevices"] = collectBlockDeviceAnalytics()
        data["kernelComponents"] = collectKernelComponentAnalytics()
        data["networkSocketQueues"] = collectNetworkSocketQueues()
        data["powerSupply"] = collectPowerSupplyAnalytics()
        data["firmware"] = collectFirmwareAnalytics()
        data["graphics"] = collectGraphicsAnalytics()
        data["hardwareBus"] = collectHardwareBusAnalytics()
        data["networkLinks"] = collectNetworkLinkAnalytics()
        data["virtualization"] = collectVirtualizationAnalytics()
        data["bootSecurity"] = collectBootSecurityAnalytics()
        data["kernelCrypto"] = collectKernelCryptoAnalytics()
        data["powerManagement"] = collectPowerManagementAnalytics()
        data["processCapabilities"] = collectProcessCapabilityAnalytics()
        data["initSystem"] = collectInitSystemAnalytics()
        data["kernelSandbox"] = collectKernelSandboxAnalytics()
        data["fileLocks"] = collectFileLockAnalytics()
        data["kernelDebugPolicy"] = collectKernelDebugPolicy()
        data["memoryZones"] = collectMemoryZoneAnalytics()
        data["kernelSlab"] = collectKernelSlabAnalytics()
        data["kernelWorkqueues"] = collectKernelWorkqueueAnalytics()
        data["ioUringPolicy"] = collectIoUringPolicy()
        data["cgroupTopology"] = collectCgroupTopologyAnalytics()
        data["networkSocketPressure"] = collectNetworkSocketPressure()
        data["buddyAllocator"] = collectBuddyAllocatorAnalytics()
        data["memoryCompaction"] = collectMemoryCompactionAnalytics()
        data["kernelFaultPolicy"] = collectKernelFaultPolicy()
        data["processFdTypes"] = collectProcessFdTypeAnalytics()
        data["threadStates"] = collectThreadStateAnalytics(threadMx)
        data["cgroupMembership"] = collectCgroupMembershipAnalytics()
        data["processTimers"] = collectProcessTimerAnalytics()
        data["kernelRcu"] = collectKernelRcuAnalytics()
        data["networkKernelPolicy"] = collectNetworkKernelPolicy()
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
                "processOomPolicy" to data["processOomPolicy"],
                "containerIoReadBytes" to (data["containerIo"] as? Map<*, *>)?.get("readBytes"),
                "containerEffectiveCpuCount" to (data["runtimeEnvironment"] as? Map<*, *>)?.get("effectiveCpuCount"),
                "cpuPressureAvg10" to ((data["pressureStall"] as? Map<*, *>)?.get("cpu") as? Map<*, *>)?.get("someAvg10"),
                "memoryPressureAvg10" to ((data["pressureStall"] as? Map<*, *>)?.get("memory") as? Map<*, *>)?.get("someAvg10"),
                "ioPressureAvg10" to ((data["pressureStall"] as? Map<*, *>)?.get("io") as? Map<*, *>)?.get("someAvg10"),
                "pressureStall" to data["pressureStall"],
                "systemMemoryAvailableBytes" to (data["systemMemory"] as? Map<*, *>)?.get("MemAvailable"),
                "containerPressureStall" to data["containerPressureStall"],
                "thermalZones" to data["thermalZones"],
                "interrupts" to data["interrupts"],
                "cpuPressureAvg10" to ((data["pressureStall"] as? Map<*, *>)?.get("cpu") as? Map<*, *>)?.get("someAvg10"),
                "memoryPressureAvg10" to ((data["pressureStall"] as? Map<*, *>)?.get("memory") as? Map<*, *>)?.get("someAvg10"),
                "ioPressureAvg10" to ((data["pressureStall"] as? Map<*, *>)?.get("io") as? Map<*, *>)?.get("someAvg10"),
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
                "maxTemperatureMilliC" to (data["thermalZones"] as? Map<*, *>)?.get("maxTemperatureMilliC"),
                "interruptTotal" to (data["interrupts"] as? Map<*, *>)?.get("total"),
                "pageFaults" to (data["kernelVmStat"] as? Map<*, *>)?.get("pageFaults"),
                "diskReadSectors" to (data["diskStats"] as? Map<*, *>)?.get("readSectors"),
                "diskWrittenSectors" to (data["diskStats"] as? Map<*, *>)?.get("writtenSectors"),
                "entropyAvailable" to (data["kernelRuntime"] as? Map<*, *>)?.get("entropyAvailable"),
                "cpuIdleSeconds" to (data["kernelRuntime"] as? Map<*, *>)?.get("cpuIdleSeconds"),
                "softIrqTotal" to (data["softIrqs"] as? Map<*, *>)?.get("total"),
                "securityEnforcement" to (data["securityRuntime"] as? Map<*, *>)?.get("enforcement"),
                "hardwareSensorCount" to (data["hardwareSensors"] as? Map<*, *>)?.get("sensorCount"),
                "numaNodeCount" to (data["numaMemory"] as? Map<*, *>)?.get("nodeCount"),
                "vmSwappiness" to (data["kernelVmPolicy"] as? Map<*, *>)?.get("swappiness"),
                "schedulerLatencyNs" to (data["kernelSchedulerPolicy"] as? Map<*, *>)?.get("latencyNs"),
                "tcpRetransmissions" to (data["networkProtocolStats"] as? Map<*, *>)?.get("tcpRetransmissions"),
                "softnetDropped" to (data["networkSoftnetStats"] as? Map<*, *>)?.get("dropped"),
                "defaultRouteCount" to (data["networkRoutes"] as? Map<*, *>)?.get("defaultRouteCount"),
                "dnsNameserverCount" to (data["networkResolver"] as? Map<*, *>)?.get("nameserverCount"),
                "nameServiceMethodCount" to (data["networkNameService"] as? Map<*, *>)?.get("methodCount"),
                "cpuIdleTicks" to (data["systemScheduling"] as? Map<*, *>)?.get("cpuIdleTicks"),
                "cpuIowaitTicks" to (data["systemScheduling"] as? Map<*, *>)?.get("cpuIowaitTicks"),
                "cpuStealTicks" to (data["systemScheduling"] as? Map<*, *>)?.get("cpuStealTicks"),
                "processSchedulerVoluntarySwitches" to (data["processSchedulerDetails"] as? Map<*, *>)?.get("voluntaryContextSwitches"),
                "processSchedulerMigrations" to (data["processSchedulerDetails"] as? Map<*, *>)?.get("migrations"),
                "processRunTimeNs" to (data["processSchedStat"] as? Map<*, *>)?.get("runTimeNs"),
                "processRunDelayNs" to (data["processSchedStat"] as? Map<*, *>)?.get("runDelayNs"),
                "pendingSignalCount" to (data["processSignals"] as? Map<*, *>)?.get("pendingCount"),
                "virtualMemoryBytes" to (data["processAddressSpace"] as? Map<*, *>)?.get("virtualBytes"),
                "tcpListenDrops" to (((data["networkProtocolStats"] as? Map<*, *>)?.get("tcpExt") as? Map<*, *>)?.get("ListenDrops")),
                "tcpListenOverflows" to (((data["networkProtocolStats"] as? Map<*, *>)?.get("tcpExt") as? Map<*, *>)?.get("ListenOverflows")),
                "dirtyMemoryBytes" to (data["systemMemory"] as? Map<*, *>)?.get("Dirty"),
                "writebackMemoryBytes" to (data["systemMemory"] as? Map<*, *>)?.get("Writeback"),
                "cgroupIoReadBytes" to (((data["runtimeEnvironment"] as? Map<*, *>)?.get("io") as? Map<*, *>)?.get("readBytes")),
                "cgroupIoWriteBytes" to (((data["runtimeEnvironment"] as? Map<*, *>)?.get("io") as? Map<*, *>)?.get("writeBytes")),
                "cpuFrequencyAverageKHz" to (data["os"] as? Map<*, *>)?.get("cpuFrequency")
                    ?.let { it as? Map<*, *> }?.get("currentAverageKHz"),
                "cpuFrequencyMinKHz" to (data["os"] as? Map<*, *>)?.get("cpuFrequency")
                    ?.let { it as? Map<*, *> }?.get("currentMinKHz"),
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
            val thermal = snapshot["thermalZones"] as? Map<*, *>
            val hottest = (thermal?.get("maxTemperatureMilliC") as? Number)?.toDouble()
            val critical = (thermal?.get("zones") as? Collection<*>)?.mapNotNull { zone ->
                (zone as? Map<*, *>)?.get("criticalTripMilliC") as? Number
            }?.map { it.toDouble() }?.maxOrNull()
            if (hottest != null && critical != null && hottest >= critical) {
                add(linkedMapOf("code" to "thermalCritical", "severity" to "critical"))
            } else if (hottest != null && critical != null && hottest >= critical * 0.9) {
                add(linkedMapOf("code" to "thermalHigh", "severity" to "elevated"))
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
                "thermal" to hasData(snapshot["thermalZones"]),
                "interrupts" to hasData(snapshot["interrupts"]),
                "kernelVmStat" to hasData(snapshot["kernelVmStat"]),
                "diskStats" to hasData(snapshot["diskStats"]),
                "kernelRuntime" to hasData(snapshot["kernelRuntime"]),
                "softIrqs" to hasData(snapshot["softIrqs"]),
                "securityRuntime" to hasData(snapshot["securityRuntime"]),
                "hardwareSensors" to hasData(snapshot["hardwareSensors"]),
                "numaMemory" to hasData(snapshot["numaMemory"]),
                "kernelVmPolicy" to hasData(snapshot["kernelVmPolicy"]),
                "kernelSchedulerPolicy" to hasData(snapshot["kernelSchedulerPolicy"]),
                "networkProtocolStats" to hasData(snapshot["networkProtocolStats"]),
                "networkSoftnetStats" to hasData(snapshot["networkSoftnetStats"]),
                "networkRoutes" to hasData(snapshot["networkRoutes"]),
                "networkResolver" to hasData(snapshot["networkResolver"]),
                "networkNameService" to hasData(snapshot["networkNameService"]),
                "kernelNotificationPolicy" to hasData(snapshot["kernelNotificationPolicy"]),
                "kernelSysctl" to hasData(snapshot["kernelSysctl"]),
                "blockDevices" to hasData(snapshot["blockDevices"]),
                "kernelComponents" to hasData(snapshot["kernelComponents"]),
                "networkSocketQueues" to hasData(snapshot["networkSocketQueues"]),
                "powerSupply" to hasData(snapshot["powerSupply"]),
                "firmware" to hasData(snapshot["firmware"]),
                "graphics" to hasData(snapshot["graphics"]),
                "hardwareBus" to hasData(snapshot["hardwareBus"]),
                "networkLinks" to hasData(snapshot["networkLinks"]),
                "virtualization" to hasData(snapshot["virtualization"]),
                "bootSecurity" to hasData(snapshot["bootSecurity"]),
                "kernelCrypto" to hasData(snapshot["kernelCrypto"]),
                "powerManagement" to hasData(snapshot["powerManagement"]),
                "processCapabilities" to hasData(snapshot["processCapabilities"]),
                "initSystem" to hasData(snapshot["initSystem"]),
                "kernelSandbox" to hasData(snapshot["kernelSandbox"]),
                "fileLocks" to hasData(snapshot["fileLocks"]),
                "kernelDebugPolicy" to hasData(snapshot["kernelDebugPolicy"]),
                "memoryZones" to hasData(snapshot["memoryZones"]),
                "kernelSlab" to hasData(snapshot["kernelSlab"]),
                "kernelWorkqueues" to hasData(snapshot["kernelWorkqueues"]),
                "ioUringPolicy" to hasData(snapshot["ioUringPolicy"]),
                "cgroupTopology" to hasData(snapshot["cgroupTopology"]),
                "networkSocketPressure" to hasData(snapshot["networkSocketPressure"]),
                "buddyAllocator" to hasData(snapshot["buddyAllocator"]),
                "memoryCompaction" to hasData(snapshot["memoryCompaction"]),
                "kernelFaultPolicy" to hasData(snapshot["kernelFaultPolicy"]),
                "processFdTypes" to hasData(snapshot["processFdTypes"]),
                "threadStates" to hasData(snapshot["threadStates"]),
                "cgroupMembership" to hasData(snapshot["cgroupMembership"]),
                "processTimers" to hasData(snapshot["processTimers"]),
                "kernelRcu" to hasData(snapshot["kernelRcu"]),
                "networkKernelPolicy" to hasData(snapshot["networkKernelPolicy"]),
                "processSchedulerDetails" to hasData(snapshot["processSchedulerDetails"]),
                "processSignals" to hasData(snapshot["processSignals"]),
                "processAddressSpace" to hasData(snapshot["processAddressSpace"]),
                "processSchedStat" to hasData(snapshot["processSchedStat"]),
            ),
            "unavailableSections" to listOf(
                "processIo" to snapshot["processIo"],
                "processNetwork" to snapshot["processNetwork"],
                "processStatus" to snapshot["processStatus"],
                "processLimits" to snapshot["processLimits"],
                "processScheduling" to snapshot["processScheduling"],
                "loadAverage" to snapshot["loadAverage"],
                "thermalZones" to snapshot["thermalZones"],
                "interrupts" to snapshot["interrupts"],
                "kernelVmStat" to snapshot["kernelVmStat"],
                "diskStats" to snapshot["diskStats"],
                "kernelRuntime" to snapshot["kernelRuntime"],
                "softIrqs" to snapshot["softIrqs"],
                "securityRuntime" to snapshot["securityRuntime"],
                "hardwareSensors" to snapshot["hardwareSensors"],
                "numaMemory" to snapshot["numaMemory"],
                "kernelVmPolicy" to snapshot["kernelVmPolicy"],
                "kernelSchedulerPolicy" to snapshot["kernelSchedulerPolicy"],
                "networkProtocolStats" to snapshot["networkProtocolStats"],
                "networkSoftnetStats" to snapshot["networkSoftnetStats"],
                "networkRoutes" to snapshot["networkRoutes"],
                "networkResolver" to snapshot["networkResolver"],
                "networkNameService" to snapshot["networkNameService"],
                "kernelNotificationPolicy" to snapshot["kernelNotificationPolicy"],
                "kernelSysctl" to snapshot["kernelSysctl"],
                "blockDevices" to snapshot["blockDevices"],
                "kernelComponents" to snapshot["kernelComponents"],
                "networkSocketQueues" to snapshot["networkSocketQueues"],
                "powerSupply" to snapshot["powerSupply"],
                "firmware" to snapshot["firmware"],
                "graphics" to snapshot["graphics"],
                "hardwareBus" to snapshot["hardwareBus"],
                "networkLinks" to snapshot["networkLinks"],
                "virtualization" to snapshot["virtualization"],
                "bootSecurity" to snapshot["bootSecurity"],
                "kernelCrypto" to snapshot["kernelCrypto"],
                "powerManagement" to snapshot["powerManagement"],
                "processCapabilities" to snapshot["processCapabilities"],
                "initSystem" to snapshot["initSystem"],
                "kernelSandbox" to snapshot["kernelSandbox"],
                "fileLocks" to snapshot["fileLocks"],
                "kernelDebugPolicy" to snapshot["kernelDebugPolicy"],
                "memoryZones" to snapshot["memoryZones"],
                "kernelSlab" to snapshot["kernelSlab"],
                "kernelWorkqueues" to snapshot["kernelWorkqueues"],
                "ioUringPolicy" to snapshot["ioUringPolicy"],
                "cgroupTopology" to snapshot["cgroupTopology"],
                "networkSocketPressure" to snapshot["networkSocketPressure"],
                "buddyAllocator" to snapshot["buddyAllocator"],
                "memoryCompaction" to snapshot["memoryCompaction"],
                "kernelFaultPolicy" to snapshot["kernelFaultPolicy"],
                "processFdTypes" to snapshot["processFdTypes"],
                "threadStates" to snapshot["threadStates"],
                "cgroupMembership" to snapshot["cgroupMembership"],
                "processTimers" to snapshot["processTimers"],
                "kernelRcu" to snapshot["kernelRcu"],
                "networkKernelPolicy" to snapshot["networkKernelPolicy"],
                "processSchedulerDetails" to snapshot["processSchedulerDetails"],
                "processSignals" to snapshot["processSignals"],
                "processAddressSpace" to snapshot["processAddressSpace"],
                "processSchedStat" to snapshot["processSchedStat"],
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
                "memoryLowEvents" to (snapshot["runtimeEnvironment"] as? Map<*, *>)?.get("memoryLowEvents"),
                "memoryOomGroupEvents" to (snapshot["runtimeEnvironment"] as? Map<*, *>)?.get("memoryOomGroupEvents"),
                "cgroupIo" to (snapshot["runtimeEnvironment"] as? Map<*, *>)?.get("io"),
                "cgroupMemoryStat" to (snapshot["runtimeEnvironment"] as? Map<*, *>)?.get("memoryStat"),
                "memoryEventsLocal" to (snapshot["runtimeEnvironment"] as? Map<*, *>)?.get("memoryEventsLocal"),
            ),
            "containerMemoryStat" to (snapshot["runtimeEnvironment"] as? Map<*, *>)?.get("memoryStat"),
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
            "kernelVmStat" to snapshot["kernelVmStat"],
            "diskStats" to snapshot["diskStats"],
            "kernelRuntime" to snapshot["kernelRuntime"],
            "softIrqs" to snapshot["softIrqs"],
            "securityRuntime" to snapshot["securityRuntime"],
            "hardwareSensors" to snapshot["hardwareSensors"],
            "numaMemory" to snapshot["numaMemory"],
            "kernelVmPolicy" to snapshot["kernelVmPolicy"],
            "kernelSchedulerPolicy" to snapshot["kernelSchedulerPolicy"],
            "networkProtocolStats" to snapshot["networkProtocolStats"],
            "networkSoftnetStats" to snapshot["networkSoftnetStats"],
            "networkRoutes" to snapshot["networkRoutes"],
            "networkResolver" to snapshot["networkResolver"],
            "networkNameService" to snapshot["networkNameService"],
            "kernelNotificationPolicy" to snapshot["kernelNotificationPolicy"],
            "kernelSysctl" to snapshot["kernelSysctl"],
            "blockDevices" to snapshot["blockDevices"],
            "kernelComponents" to snapshot["kernelComponents"],
            "networkSocketQueues" to snapshot["networkSocketQueues"],
            "powerSupply" to snapshot["powerSupply"],
            "firmware" to snapshot["firmware"],
            "graphics" to snapshot["graphics"],
            "hardwareBus" to snapshot["hardwareBus"],
            "networkLinks" to snapshot["networkLinks"],
            "virtualization" to snapshot["virtualization"],
            "bootSecurity" to snapshot["bootSecurity"],
            "kernelCrypto" to snapshot["kernelCrypto"],
            "powerManagement" to snapshot["powerManagement"],
            "processCapabilities" to snapshot["processCapabilities"],
            "initSystem" to snapshot["initSystem"],
            "kernelSandbox" to snapshot["kernelSandbox"],
            "fileLocks" to snapshot["fileLocks"],
            "kernelDebugPolicy" to snapshot["kernelDebugPolicy"],
            "memoryZones" to snapshot["memoryZones"],
            "kernelSlab" to snapshot["kernelSlab"],
            "kernelWorkqueues" to snapshot["kernelWorkqueues"],
            "ioUringPolicy" to snapshot["ioUringPolicy"],
            "cgroupTopology" to snapshot["cgroupTopology"],
            "networkSocketPressure" to snapshot["networkSocketPressure"],
            "buddyAllocator" to snapshot["buddyAllocator"],
            "memoryCompaction" to snapshot["memoryCompaction"],
            "kernelFaultPolicy" to snapshot["kernelFaultPolicy"],
            "processFdTypes" to snapshot["processFdTypes"],
            "threadStates" to snapshot["threadStates"],
            "cgroupMembership" to snapshot["cgroupMembership"],
            "processTimers" to snapshot["processTimers"],
            "kernelRcu" to snapshot["kernelRcu"],
            "networkKernelPolicy" to snapshot["networkKernelPolicy"],
            "processSchedulerDetails" to snapshot["processSchedulerDetails"],
            "processSignals" to snapshot["processSignals"],
            "processAddressSpace" to snapshot["processAddressSpace"],
            "processSchedStat" to snapshot["processSchedStat"],
            "hostDistribution" to linkedMapOf(
                "availableProcessors" to os?.get("availableProcessors"),
                "distribution" to os?.get("distribution"),
                "kernelRelease" to os?.get("kernelRelease"),
                "cpuFrequency" to os?.get("cpuFrequency"),
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
                "maxTemperatureMilliC" to (snapshot["thermalZones"] as? Map<*, *>)?.get("maxTemperatureMilliC"),
                "thermalZoneCount" to (snapshot["thermalZones"] as? Map<*, *>)?.get("zoneCount"),
                "interruptTotal" to (snapshot["interrupts"] as? Map<*, *>)?.get("total"),
                "interruptSourceCount" to (snapshot["interrupts"] as? Map<*, *>)?.get("sourceCount"),
                "pageFaults" to (snapshot["kernelVmStat"] as? Map<*, *>)?.get("pageFaults"),
                "swapIns" to (snapshot["kernelVmStat"] as? Map<*, *>)?.get("swapIns"),
                "swapOuts" to (snapshot["kernelVmStat"] as? Map<*, *>)?.get("swapOuts"),
                "diskReadSectors" to (snapshot["diskStats"] as? Map<*, *>)?.get("readSectors"),
                "diskWrittenSectors" to (snapshot["diskStats"] as? Map<*, *>)?.get("writtenSectors"),
                "entropyAvailable" to (snapshot["kernelRuntime"] as? Map<*, *>)?.get("entropyAvailable"),
                "cpuIdleSeconds" to (snapshot["kernelRuntime"] as? Map<*, *>)?.get("cpuIdleSeconds"),
                "softIrqTotal" to (snapshot["softIrqs"] as? Map<*, *>)?.get("total"),
                "dirtyMemoryBytes" to (snapshot["systemMemory"] as? Map<*, *>)?.get("Dirty"),
                "writebackMemoryBytes" to (snapshot["systemMemory"] as? Map<*, *>)?.get("Writeback"),
                "activeFileBytes" to (snapshot["systemMemory"] as? Map<*, *>)?.get("Active_file"),
                "inactiveFileBytes" to (snapshot["systemMemory"] as? Map<*, *>)?.get("Inactive_file"),
                "securityEnforcement" to (snapshot["securityRuntime"] as? Map<*, *>)?.get("enforcement"),
                "hardwareSensorCount" to (snapshot["hardwareSensors"] as? Map<*, *>)?.get("sensorCount"),
                "numaNodeCount" to (snapshot["numaMemory"] as? Map<*, *>)?.get("nodeCount"),
                "vmSwappiness" to (snapshot["kernelVmPolicy"] as? Map<*, *>)?.get("swappiness"),
                "schedulerLatencyNs" to (snapshot["kernelSchedulerPolicy"] as? Map<*, *>)?.get("latencyNs"),
                "tcpRetransmissions" to (snapshot["networkProtocolStats"] as? Map<*, *>)?.get("tcpRetransmissions"),
                "softnetDropped" to (snapshot["networkSoftnetStats"] as? Map<*, *>)?.get("dropped"),
                "defaultRouteCount" to (snapshot["networkRoutes"] as? Map<*, *>)?.get("defaultRouteCount"),
                "dnsNameserverCount" to (snapshot["networkResolver"] as? Map<*, *>)?.get("nameserverCount"),
                "nameServiceMethodCount" to (snapshot["networkNameService"] as? Map<*, *>)?.get("methodCount"),
                "tcpRetransmissions" to (snapshot["networkProtocolStats"] as? Map<*, *>)?.get("tcpRetransmissions"),
                "cpuIdleTicks" to (snapshot["systemScheduling"] as? Map<*, *>)?.get("cpuIdleTicks"),
                "cpuIowaitTicks" to (snapshot["systemScheduling"] as? Map<*, *>)?.get("cpuIowaitTicks"),
                "cpuStealTicks" to (snapshot["systemScheduling"] as? Map<*, *>)?.get("cpuStealTicks"),
                "processSchedulerVoluntarySwitches" to (snapshot["processSchedulerDetails"] as? Map<*, *>)?.get("voluntaryContextSwitches"),
                "processSchedulerMigrations" to (snapshot["processSchedulerDetails"] as? Map<*, *>)?.get("migrations"),
                "processRunTimeNs" to (snapshot["processSchedStat"] as? Map<*, *>)?.get("runTimeNs"),
                "processRunDelayNs" to (snapshot["processSchedStat"] as? Map<*, *>)?.get("runDelayNs"),
                "pendingSignalCount" to (snapshot["processSignals"] as? Map<*, *>)?.get("pendingCount"),
                "virtualMemoryBytes" to (snapshot["processAddressSpace"] as? Map<*, *>)?.get("virtualBytes"),
                "tcpListenDrops" to (((snapshot["networkProtocolStats"] as? Map<*, *>)?.get("tcpExt") as? Map<*, *>)?.get("ListenDrops")),
                "tcpListenOverflows" to (((snapshot["networkProtocolStats"] as? Map<*, *>)?.get("tcpExt") as? Map<*, *>)?.get("ListenOverflows")),
                "softnetDropped" to (snapshot["networkSoftnetStats"] as? Map<*, *>)?.get("dropped"),
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
                "containerPressureStall" to snapshot["containerPressureStall"],
                "processSecurity" to snapshot["processSecurity"],
                "processNamespaces" to snapshot["processNamespaces"],
                "processOomPolicy" to snapshot["processOomPolicy"],
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
                "fileStoreTotalBytes" to runCatching { store?.totalSpace }.getOrNull(),
                "fileStoreUsableBytes" to runCatching { store?.usableSpace }.getOrNull(),
                "fileStoreUnallocatedBytes" to runCatching { store?.unallocatedSpace }.getOrNull(),
                "fileStoreUsedRatio" to runCatching {
                    store?.totalSpace?.takeIf { it > 0L }?.let { total ->
                        (total - (store.unallocatedSpace)).toDouble() / total
                    }
                }.getOrNull(),
                "fileStoreReadOnly" to runCatching { store?.isReadOnly }.getOrNull(),
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
            val optional = left.drop(6).takeWhile { it != "-" }
            linkedMapOf<String, Any?>(
                "path" to mountPoint,
                "readOnly" to options.contains("ro"),
                "type" to right.firstOrNull(),
                "source" to right.getOrNull(1),
                "shared" to optional.any { it.startsWith("shared:") },
                "master" to optional.any { it.startsWith("master:") },
                "propagation" to when {
                    optional.any { it.startsWith("shared:") } -> "shared"
                    optional.any { it.startsWith("master:") } -> "slave"
                    else -> "private-or-unknown"
                },
                "optionalFieldCount" to optional.size,
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
            "maxTemperatureMilliCDelta" to numericDelta("maxTemperatureMilliC"),
            "interruptTotalDelta" to numericDelta("interruptTotal"),
            "cpuFrequencyAverageKHzDelta" to numericDelta("cpuFrequencyAverageKHz"),
            "cpuFrequencyMinKHzDelta" to numericDelta("cpuFrequencyMinKHz"),
            "entropyAvailableDelta" to numericDelta("entropyAvailable"),
            "cpuIdleSecondsDelta" to numericDelta("cpuIdleSeconds"),
            "softIrqTotalDelta" to numericDelta("softIrqTotal"),
            "dirtyMemoryBytesDelta" to numericDelta("dirtyMemoryBytes"),
            "writebackMemoryBytesDelta" to numericDelta("writebackMemoryBytes"),
            "cgroupIoReadBytesDelta" to numericDelta("cgroupIoReadBytes"),
            "cgroupIoWriteBytesDelta" to numericDelta("cgroupIoWriteBytes"),
            "tcpRetransmissionsDelta" to numericDelta("tcpRetransmissions"),
            "tcpListenDropsDelta" to numericDelta("tcpListenDrops"),
            "tcpListenOverflowsDelta" to numericDelta("tcpListenOverflows"),
            "softnetDroppedDelta" to numericDelta("softnetDropped"),
            "cpuIdleTicksDelta" to numericDelta("cpuIdleTicks"),
            "cpuIowaitTicksDelta" to numericDelta("cpuIowaitTicks"),
            "cpuStealTicksDelta" to numericDelta("cpuStealTicks"),
            "processSchedulerVoluntarySwitchesDelta" to numericDelta("processSchedulerVoluntarySwitches"),
            "processSchedulerMigrationsDelta" to numericDelta("processSchedulerMigrations"),
            "processRunTimeNsDelta" to numericDelta("processRunTimeNs"),
            "processRunDelayNsDelta" to numericDelta("processRunDelayNs"),
            "durationMsDelta" to numericDelta("durationMs"),
            "bufferPoolUsedBytesDelta" to numericDelta("bufferPoolUsedBytes"),
            "jitCompilationTimeMsDelta" to numericDelta("jitCompilationTimeMs"),
            "networkReceiveBytesDelta" to numericDelta("networkReceiveBytes"),
            "networkTransmitBytesDelta" to numericDelta("networkTransmitBytes"),
            "networkReceiveErrorsDelta" to numericDelta("networkReceiveErrors"),
            "networkTransmitErrorsDelta" to numericDelta("networkTransmitErrors"),
            "networkReceiveDropsDelta" to numericDelta("networkReceiveDrops"),
            "networkTransmitDropsDelta" to numericDelta("networkTransmitDrops"),
            "cpuPressureAvg10Delta" to numericDelta("cpuPressureAvg10"),
            "memoryPressureAvg10Delta" to numericDelta("memoryPressureAvg10"),
            "ioPressureAvg10Delta" to numericDelta("ioPressureAvg10"),
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

    private fun readKeyValueFile(path: String): Map<String, String> = runCatching {
        File(path).takeIf(File::isFile)?.readLines().orEmpty().mapNotNull { line ->
            val separator = line.indexOf(' ')
            if (separator <= 0) null else line.substring(0, separator) to line.substring(separator + 1).trim()
        }.toMap()
    }.getOrDefault(emptyMap())

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

    private fun collectCpuFrequency(): Map<String, Any?> = runCatching {
        val cpus = File("/sys/devices/system/cpu").listFiles().orEmpty()
            .filter { it.name.matches(Regex("cpu\\d+")) }
        val current = cpus.mapNotNull { readLongFile("${it.path}/cpufreq/scaling_cur_freq") }
        val minimum = cpus.mapNotNull { readLongFile("${it.path}/cpufreq/scaling_min_freq") }
        val maximum = cpus.mapNotNull { readLongFile("${it.path}/cpufreq/scaling_max_freq") }
        linkedMapOf(
            "sampledCpuCount" to current.size,
            "currentMinKHz" to current.minOrNull(),
            "currentMaxKHz" to current.maxOrNull(),
            "currentAverageKHz" to current.average().takeIf { current.isNotEmpty() },
            "configuredMinKHz" to minimum.minOrNull(),
            "configuredMaxKHz" to maximum.maxOrNull(),
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

    /** Lists installed JCA providers and non-secret runtime crypto properties. */
    private fun collectJvmSecurity(): Map<String, Any?> = runCatching {
        linkedMapOf(
            "providerCount" to Security.getProviders().size,
            "providers" to Security.getProviders().map { provider ->
                linkedMapOf<String, Any?>(
                    "name" to provider.name,
                    "version" to provider.version,
                    "info" to provider.info.take(160),
                )
            },
            "cryptoPolicy" to Security.getProperty("crypto.policy"),
            "disabledAlgorithmsConfigured" to listOf(
                "jdk.tls.disabledAlgorithms",
                "jdk.certpath.disabledAlgorithms",
                "jdk.jar.disabledAlgorithms",
            ).count { !Security.getProperty(it).isNullOrBlank() },
        )
    }.getOrDefault(emptyMap())

    private fun collectClassLoaderAnalytics(): Map<String, Any?> = runCatching {
        val classes = Thread.currentThread().contextClassLoader
            ?.let { loader -> generateSequence(loader) { it.parent }.toList() }
            .orEmpty()
        linkedMapOf(
            "contextChainLength" to classes.size,
            "contextChain" to classes.map { it.javaClass.name }.take(32),
            "platformLoader" to ClassLoader.getPlatformClassLoader().javaClass.name,
            "systemLoader" to ClassLoader.getSystemClassLoader().javaClass.name,
        )
    }.getOrDefault(emptyMap())

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
        "cpuPeriods" to readCgroupKey("/sys/fs/cgroup/cpu.stat", "nr_periods"),
        "cpuPressure" to parsePressureFile("/sys/fs/cgroup/cpu.pressure"),
        "effectiveCpuSet" to readCgroupText("/sys/fs/cgroup/cpuset.cpus.effective"),
        "effectiveCpuCount" to readCgroupText("/sys/fs/cgroup/cpuset.cpus.effective")?.let(::countCpuSet),
        "memoryHighEvents" to readCgroupKey("/sys/fs/cgroup/memory.events", "high"),
        "memoryLowEvents" to readCgroupKey("/sys/fs/cgroup/memory.events", "low"),
        "memoryMaxEvents" to readCgroupKey("/sys/fs/cgroup/memory.events", "max"),
        "memoryOomEvents" to readCgroupKey("/sys/fs/cgroup/memory.events", "oom"),
        "memoryOomKillEvents" to readCgroupKey("/sys/fs/cgroup/memory.events", "oom_kill"),
        "memoryOomGroupEvents" to readCgroupKey("/sys/fs/cgroup/memory.events", "oom_group"),
        "memoryEventsLocal" to collectCgroupMemoryEventsLocal(),
        "memoryStat" to collectCgroupMemoryStat(),
        "io" to collectCgroupIo(),
    )

    private fun collectCgroupMemoryStat(): Map<String, Any?> = runCatching {
        val keys = setOf("anon", "file", "kernel", "kernel_stack", "slab", "sock", "shmem", "file_mapped", "file_dirty", "file_writeback", "inactive_anon", "active_anon", "inactive_file", "active_file")
        val values = File("/sys/fs/cgroup/memory.stat").takeIf(File::isFile)?.readLines().orEmpty()
            .mapNotNull { line ->
                val parts = line.trim().split(Regex("\\s+"))
                if (parts.size != 2 || parts[0] !in keys) null else parts[0] to parts[1].toLongOrNull()
            }.toMap()
        linkedMapOf(
            "anonBytes" to values["anon"], "fileBytes" to values["file"], "kernelBytes" to values["kernel"],
            "kernelStackBytes" to values["kernel_stack"], "slabBytes" to values["slab"], "socketBytes" to values["sock"],
            "shmemBytes" to values["shmem"], "mappedFileBytes" to values["file_mapped"],
            "dirtyFileBytes" to values["file_dirty"], "writebackFileBytes" to values["file_writeback"],
            "activeAnonBytes" to values["active_anon"], "inactiveAnonBytes" to values["inactive_anon"],
            "activeFileBytes" to values["active_file"], "inactiveFileBytes" to values["inactive_file"],
        )
    }.getOrDefault(emptyMap())

    private fun collectCgroupMemoryEventsLocal(): Map<String, Long?> = runCatching {
        File("/sys/fs/cgroup/memory.events.local").takeIf(File::isFile)?.readLines().orEmpty()
            .mapNotNull { line ->
                val parts = line.trim().split(Regex("\\s+"))
                if (parts.size != 2) null else parts[0] to parts[1].toLongOrNull()
            }.toMap()
    }.getOrDefault(emptyMap())

    private fun collectCgroupIo(): Map<String, Any?> = runCatching {
        val rows = File("/sys/fs/cgroup/io.stat").takeIf(File::isFile)?.readLines().orEmpty()
        val devices = rows.mapNotNull { line ->
            val parts = line.trim().split(Regex("\\s+"))
            val device = parts.firstOrNull() ?: return@mapNotNull null
            val values = parts.drop(1).mapNotNull { token ->
                val separator = token.indexOf('=')
                if (separator <= 0) null else token.substring(0, separator) to token.substring(separator + 1).toLongOrNull()
            }.toMap()
            linkedMapOf<String, Any?>("device" to device, "readBytes" to values["rbytes"], "writeBytes" to values["wbytes"], "discardBytes" to values["dbytes"], "readIos" to values["rios"], "writeIos" to values["wios"])
        }
        linkedMapOf(
            "deviceCount" to devices.size,
            "readBytes" to devices.sumOf { (it["readBytes"] as? Number)?.toLong() ?: 0L },
            "writeBytes" to devices.sumOf { (it["writeBytes"] as? Number)?.toLong() ?: 0L },
            "discardBytes" to devices.sumOf { (it["discardBytes"] as? Number)?.toLong() ?: 0L },
            "devices" to devices.take(32),
        )
    }.getOrDefault(emptyMap())

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

    private fun collectContainerPressureStall(): Map<String, Any?> = runCatching {
        linkedMapOf<String, Any?>().apply {
            listOf("cpu", "memory", "io").forEach { resource ->
                parsePressureFile("/sys/fs/cgroup/$resource.pressure")?.let { put(resource, it) }
            }
        }
    }.getOrDefault(emptyMap())

    private fun parsePressureFile(path: String): Map<String, Double>? = runCatching {
        val lines = File(path).takeIf(File::isFile)?.readLines().orEmpty()
        if (lines.isEmpty()) return@runCatching null
        linkedMapOf<String, Double>().apply {
            lines.forEach { line ->
                val parts = line.trim().split(Regex("\\s+"))
                val category = parts.firstOrNull() ?: return@forEach
                parts.drop(1).forEach { part ->
                    val key = part.substringBefore('=')
                    part.substringAfter('=', "").toDoubleOrNull()
                        ?.let { put("${category}${key.replaceFirstChar(Char::uppercase)}", it) }
                }
            }
        }
    }.getOrNull()

    private fun collectSystemMemory(): Map<String, Long> = runCatching {
        val keys = setOf(
            "MemTotal", "MemFree", "MemAvailable", "Buffers", "Cached", "SReclaimable",
            "Shmem", "Slab", "PageTables", "CommitLimit", "Committed_AS", "SwapTotal", "SwapFree",
            "AnonPages", "Mapped", "Unevictable", "HugePages_Total", "HugePages_Free",
            "Dirty", "Writeback", "Active", "Inactive", "Active_anon", "Inactive_anon",
            "Active_file", "Inactive_file", "Unevictable", "SReclaimable", "KReclaimable",
            "VmallocTotal", "VmallocUsed", "VmallocChunk", "DirectMap4k", "DirectMap2M", "DirectMap1G",
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

    /** Reads Linux thermal-zone sensors without exposing device paths or raw files. */
    private fun collectThermalZones(): Map<String, Any?> = runCatching {
        val zones = File("/sys/class/thermal").listFiles()
            ?.filter { it.isDirectory && it.name.startsWith("thermal_zone") }
            ?.mapNotNull { zone ->
                val temperature = readTextFile(File(zone, "temp").path)?.toLongOrNull() ?: return@mapNotNull null
                linkedMapOf<String, Any?>(
                    "type" to readTextFile(File(zone, "type").path),
                    "temperatureMilliC" to temperature,
                    "criticalTripMilliC" to readTextFile(File(zone, "trip_point_0_temp").path)?.toLongOrNull(),
                )
            }.orEmpty()
        linkedMapOf(
            "zoneCount" to zones.size,
            "zones" to zones,
            "maxTemperatureMilliC" to zones.mapNotNull { (it["temperatureMilliC"] as? Number)?.toLong() }.maxOrNull(),
            "averageTemperatureMilliC" to zones.mapNotNull { (it["temperatureMilliC"] as? Number)?.toDouble() }
                .average().takeIf { zones.isNotEmpty() },
        )
    }.getOrDefault(emptyMap())

    /** Summarizes interrupt activity while keeping individual device names out of the report. */
    private fun collectInterruptAnalytics(): Map<String, Any?> = runCatching {
        val rows = File("/proc/interrupts").takeIf(File::isFile)?.readLines().orEmpty().drop(1)
        val totals = rows.mapNotNull { row ->
            val fields = row.trim().split(Regex("\\s+"))
            val source = fields.firstOrNull()?.removeSuffix(":") ?: return@mapNotNull null
            val count = fields.drop(1).takeWhile { it.all(Char::isDigit) }.sumOf { it.toLongOrNull() ?: 0L }
            source to count
        }
        linkedMapOf(
            "sourceCount" to totals.size,
            "total" to totals.sumOf { it.second },
            "topSources" to totals.sortedByDescending { it.second }.take(8).map { (source, count) ->
                linkedMapOf("source" to source, "count" to count)
            },
        )
    }.getOrDefault(emptyMap())

    /** Collects kernel VM counters while retaining only diagnostic aggregates. */
    private fun collectKernelVmStat(): Map<String, Any?> = runCatching {
        val values = File("/proc/vmstat").takeIf(File::isFile)?.readLines().orEmpty()
            .mapNotNull { line ->
                val parts = line.trim().split(Regex("\\s+"))
                if (parts.size != 2) null else parts[0] to parts[1].toLongOrNull()
            }.toMap()
        linkedMapOf(
            "pageFaults" to values["pgfault"],
            "majorPageFaults" to values["pgmajfault"],
            "swapIns" to values["pswpin"],
            "swapOuts" to values["pswpout"],
            "oomKills" to values["oom_kill"],
            "compactions" to values["compact_stall"],
        )
    }.getOrDefault(emptyMap())

    /** Captures non-sensitive VM policy knobs that explain reclaim and swap behavior. */
    private fun collectKernelVmPolicy(): Map<String, Any?> = linkedMapOf(
        "swappiness" to readLongFile("/proc/sys/vm/swappiness"),
        "dirtyRatio" to readLongFile("/proc/sys/vm/dirty_ratio"),
        "dirtyBackgroundRatio" to readLongFile("/proc/sys/vm/dirty_background_ratio"),
        "dirtyBytes" to readLongFile("/proc/sys/vm/dirty_bytes"),
        "dirtyBackgroundBytes" to readLongFile("/proc/sys/vm/dirty_background_bytes"),
        "overcommitMemory" to readLongFile("/proc/sys/vm/overcommit_memory"),
        "overcommitRatio" to readLongFile("/proc/sys/vm/overcommit_ratio"),
        "compactMemory" to readLongFile("/proc/sys/vm/compact_memory"),
        "maxMapCount" to readLongFile("/proc/sys/vm/max_map_count"),
    )

    /** Captures scheduler policy knobs used to interpret process run-queue latency. */
    private fun collectKernelSchedulerPolicy(): Map<String, Any?> = linkedMapOf(
        "latencyNs" to readLongFile("/proc/sys/kernel/sched_latency_ns"),
        "minGranularityNs" to readLongFile("/proc/sys/kernel/sched_min_granularity_ns"),
        "wakeupGranularityNs" to readLongFile("/proc/sys/kernel/sched_wakeup_granularity_ns"),
        "migrationCostNs" to readLongFile("/proc/sys/kernel/sched_migration_cost_ns"),
        "autogroupEnabled" to readLongFile("/proc/sys/kernel/sched_autogroup_enabled")?.let { it == 1L },
        "rrTimesliceMs" to readLongFile("/proc/sys/kernel/sched_rr_timeslice_ms"),
    )

    /** Aggregates block-device sectors and latency from /proc/diskstats. */
    private fun collectDiskStats(): Map<String, Any?> = runCatching {
        val devices = File("/proc/diskstats").takeIf(File::isFile)?.readLines().orEmpty().mapNotNull { line ->
            val fields = line.trim().split(Regex("\\s+"))
            if (fields.size < 14) return@mapNotNull null
            val name = fields[2]
            if (name.matches(Regex("(loop|ram|sr)\\d+")) || name.any { it.isDigit() } && name.endsWith("p")) {
                return@mapNotNull null
            }
            val readSectors = fields[5].toLongOrNull() ?: return@mapNotNull null
            val writtenSectors = fields[9].toLongOrNull() ?: return@mapNotNull null
            val ioTimeMs = fields[12].toLongOrNull() ?: 0L
            linkedMapOf<String, Any?>("device" to name, "readSectors" to readSectors, "writtenSectors" to writtenSectors, "ioTimeMs" to ioTimeMs)
        }
        linkedMapOf(
            "deviceCount" to devices.size,
            "readSectors" to devices.sumOf { (it["readSectors"] as Number).toLong() },
            "writtenSectors" to devices.sumOf { (it["writtenSectors"] as Number).toLong() },
            "ioTimeMs" to devices.sumOf { (it["ioTimeMs"] as Number).toLong() },
            "devices" to devices.sortedByDescending { (it["ioTimeMs"] as Number).toLong() }.take(8),
        )
    }.getOrDefault(emptyMap())

    /** Reads kernel-wide runtime counters that help explain startup and scheduling stalls. */
    private fun collectKernelRuntime(): Map<String, Any?> = runCatching {
        val uptime = readTextFile("/proc/uptime")?.trim()?.split(Regex("\\s+"))
        linkedMapOf(
            "entropyAvailable" to readTextFile("/proc/sys/kernel/random/entropy_avail")?.trim()?.toLongOrNull(),
            "cpuUptimeSeconds" to uptime?.getOrNull(0)?.toDoubleOrNull(),
            "cpuIdleSeconds" to uptime?.getOrNull(1)?.toDoubleOrNull(),
            "loadAverage" to readTextFile("/proc/loadavg")?.trim()?.split(Regex("\\s+"))?.take(3),
            "processCount" to readTextFile("/proc/loadavg")?.trim()?.split(Regex("\\s+"))?.getOrNull(3)
                ?.substringAfter('/')?.toLongOrNull(),
        )
    }.getOrDefault(emptyMap())

    /** Aggregates soft-interrupt work by kernel subsystem (network, timer, RCU, and scheduler). */
    private fun collectSoftIrqAnalytics(): Map<String, Any?> = runCatching {
        val entries = File("/proc/softirqs").takeIf(File::isFile)?.readLines().orEmpty().drop(1)
            .mapNotNull { line ->
                val separator = line.indexOf(':')
                if (separator <= 0) return@mapNotNull null
                val name = line.substring(0, separator).trim()
                val total = line.substring(separator + 1).trim().split(Regex("\\s+"))
                    .sumOf { it.toLongOrNull() ?: 0L }
                name to total
            }
        linkedMapOf(
            "sourceCount" to entries.size,
            "total" to entries.sumOf { it.second },
            "sources" to entries.sortedByDescending { it.second }.map { (name, total) ->
                linkedMapOf("name" to name, "count" to total)
            },
        )
    }.getOrDefault(emptyMap())

    /** Reports host security policy state without collecting policy contents or user data. */
    private fun collectSecurityRuntime(): Map<String, Any?> = runCatching {
        val selinux = readTextFile("/sys/fs/selinux/enforce")?.trim()?.let { if (it == "1") "enforcing" else "permissive" }
        val appArmor = File("/sys/kernel/security/apparmor/profiles").takeIf(File::isFile)
            ?.readLines()?.filter { it.isNotBlank() }
            ?.map { it.substringBefore(' ') }
            ?.distinct()?.sorted()
        val noNewPrivileges = File("/proc/self/status").takeIf(File::isFile)?.useLines { lines ->
            lines.firstOrNull { it.startsWith("NoNewPrivs:") }?.substringAfter(':')?.trim()?.toIntOrNull()
        }
        linkedMapOf(
            "selinux" to selinux,
            "apparmorProfileCount" to appArmor?.size,
            "apparmorProfiles" to appArmor?.take(16),
            "noNewPrivileges" to noNewPrivileges,
            "enforcement" to when {
                selinux == "enforcing" || !appArmor.isNullOrEmpty() || noNewPrivileges == 1 -> "restricted"
                selinux != null || appArmor != null -> "available"
                else -> "unknown"
            },
        )
    }.getOrDefault(emptyMap())

    /** Reads bounded hwmon sensor values and exposes only normalized labels and numbers. */
    private fun collectHardwareSensors(): Map<String, Any?> = runCatching {
        val sensors = File("/sys/class/hwmon").listFiles().orEmpty()
            .filter(File::isDirectory)
            .flatMap { chip ->
                val chipName = readTextFile(File(chip, "name").path)?.trim().orEmpty().ifBlank { chip.name }
                chip.listFiles().orEmpty().mapNotNull { file ->
                    val match = Regex("(temp|fan|in|power)(\\d+)_(input|label)").matchEntire(file.name)
                        ?: return@mapNotNull null
                    if (!file.name.endsWith("_input")) return@mapNotNull null
                    val value = readTextFile(file.path)?.trim()?.toLongOrNull() ?: return@mapNotNull null
                    linkedMapOf<String, Any?>(
                        "chip" to chipName.take(64),
                        "kind" to match.groupValues[1],
                        "index" to match.groupValues[2].toIntOrNull(),
                        "value" to value,
                    )
                }
            }.take(64)
        linkedMapOf(
            "sensorCount" to sensors.size,
            "temperatureCount" to sensors.count { it["kind"] == "temp" },
            "fanCount" to sensors.count { it["kind"] == "fan" },
            "voltageCount" to sensors.count { it["kind"] == "in" },
            "powerCount" to sensors.count { it["kind"] == "power" },
            "sensors" to sensors,
        )
    }.getOrDefault(emptyMap())

    /** Summarizes NUMA node memory without exposing topology paths or process mappings. */
    private fun collectNumaMemory(): Map<String, Any?> = runCatching {
        val nodes = File("/sys/devices/system/node").listFiles().orEmpty()
            .filter { it.isDirectory && it.name.matches(Regex("node\\d+")) }
            .mapNotNull { node ->
                val values = File(node, "meminfo").takeIf(File::isFile)?.readLines().orEmpty()
                    .mapNotNull { line ->
                        val parts = line.trim().split(Regex("\\s+"))
                        if (parts.size < 2) null else parts[0].removeSuffix(":") to parts[1].toLongOrNull()
                    }.toMap()
                linkedMapOf<String, Any?>(
                    "node" to node.name.removePrefix("node").toIntOrNull(),
                    "totalBytes" to values["MemTotal"]?.times(1024L),
                    "freeBytes" to values["MemFree"]?.times(1024L),
                    "usedBytes" to values["MemTotal"]?.minus(values["MemFree"] ?: 0L)?.times(1024L),
                )
            }
        linkedMapOf(
            "nodeCount" to nodes.size,
            "totalBytes" to nodes.sumOf { (it["totalBytes"] as? Number)?.toLong() ?: 0L },
            "freeBytes" to nodes.sumOf { (it["freeBytes"] as? Number)?.toLong() ?: 0L },
            "nodes" to nodes,
        )
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

    /** Aggregates protocol counters from procfs without exposing addresses or connection tuples. */
    private fun collectNetworkProtocolStats(): Map<String, Any?> = runCatching {
        val lines = File("/proc/net/snmp").takeIf(File::isFile)?.readLines().orEmpty()
        val sections = linkedMapOf<String, Map<String, Long>>()
        var index = 0
        while (index + 1 < lines.size) {
            val header = lines[index].trim().split(Regex("\\s+"))
            val values = lines[index + 1].trim().split(Regex("\\s+"))
            if (header.isNotEmpty() && header.first().endsWith(":" ) && header.first() == values.firstOrNull()) {
                sections[header.first().removeSuffix(":")] = header.drop(1).zip(values.drop(1))
                    .mapNotNull { (key, value) -> value.toLongOrNull()?.let { key to it } }.toMap()
                index += 2
            } else index++
        }
        val tcp = sections["Tcp"].orEmpty()
        val udp = sections["Udp"].orEmpty()
        val ip = sections["Ip"].orEmpty()
        val extended = collectNetstatExtended()
        linkedMapOf(
            "tcpRetransmissions" to tcp["RetransSegs"],
            "tcpInErrors" to tcp["InErrs"],
            "tcpOutResets" to tcp["OutRsts"],
            "tcpActiveOpens" to tcp["ActiveOpens"],
            "tcpPassiveOpens" to tcp["PassiveOpens"],
            "udpInErrors" to udp["InErrors"],
            "udpNoPorts" to udp["NoPorts"],
            "ipInErrors" to ip["InHdrErrors"],
            "ipInDiscards" to ip["InDiscards"],
            "ipForwarding" to ip["Forwarding"],
            "tcpExt" to extended,
        )
    }.getOrDefault(emptyMap())

    private fun collectNetstatExtended(): Map<String, Long?> = runCatching {
        val lines = File("/proc/net/netstat").takeIf(File::isFile)?.readLines().orEmpty()
        val result = linkedMapOf<String, Long?>()
        var index = 0
        while (index + 1 < lines.size) {
            val header = lines[index].trim().split(Regex("\\s+"))
            val values = lines[index + 1].trim().split(Regex("\\s+"))
            if (header.firstOrNull() == values.firstOrNull() && header.firstOrNull() == "TcpExt:") {
                header.drop(1).zip(values.drop(1)).forEach { (key, value) ->
                    if (key in setOf("ListenOverflows", "ListenDrops", "SyncookiesSent", "SyncookiesRecv", "TCPTimeouts", "TW", "TCPBacklogDrop")) {
                        result[key] = value.toLongOrNull()
                    }
                }
            }
            index += 2
        }
        result
    }.getOrDefault(emptyMap())

    /** Aggregates per-CPU Linux network backlog drops and time-squeeze events. */
    private fun collectNetworkSoftnetStats(): Map<String, Any?> = runCatching {
        val rows = File("/proc/net/softnet_stat").takeIf(File::isFile)?.readLines().orEmpty()
        val parsed = rows.mapNotNull { line ->
            val fields = line.trim().split(Regex("\\s+"))
            if (fields.size < 3) return@mapNotNull null
            val processed = fields[0].toLongOrNull(16) ?: return@mapNotNull null
            val dropped = fields[1].toLongOrNull(16) ?: return@mapNotNull null
            val squeezed = fields[2].toLongOrNull(16) ?: return@mapNotNull null
            linkedMapOf<String, Long>("processed" to processed, "dropped" to dropped, "timeSqueeze" to squeezed)
        }
        linkedMapOf(
            "cpuCount" to parsed.size,
            "processed" to parsed.sumOf { it["processed"] ?: 0L },
            "dropped" to parsed.sumOf { it["dropped"] ?: 0L },
            "timeSqueeze" to parsed.sumOf { it["timeSqueeze"] ?: 0L },
        )
    }.getOrDefault(emptyMap())

    /** Summarizes route availability without exposing destination or gateway addresses. */
    private fun collectNetworkRouteAnalytics(): Map<String, Any?> = runCatching {
        val rows = File("/proc/net/route").takeIf(File::isFile)?.readLines().orEmpty().drop(1)
        val routes = rows.mapNotNull { line ->
            val fields = line.trim().split(Regex("\\s+"))
            if (fields.size < 11) null else linkedMapOf<String, Any?>(
                "interfacePresent" to fields[0].isNotBlank(),
                "isDefault" to (fields[1] == "00000000"),
                "up" to (((fields[3].toLongOrNull(16) ?: 0L) and 1L) != 0L),
                "gatewayConfigured" to (fields[2] != "00000000"),
                "metric" to fields[6].toLongOrNull(),
            )
        }
        linkedMapOf(
            "routeCount" to routes.size,
            "defaultRouteCount" to routes.count { it["isDefault"] == true && it["up"] == true },
            "upRouteCount" to routes.count { it["up"] == true },
            "gatewayRouteCount" to routes.count { it["gatewayConfigured"] == true },
        )
    }.getOrDefault(emptyMap())

    /** Summarizes resolver configuration without exposing nameserver addresses or domain names. */
    private fun collectNetworkResolverAnalytics(): Map<String, Any?> = runCatching {
        val lines = File("/etc/resolv.conf").takeIf(File::isFile)?.readLines().orEmpty()
        val nameservers = lines.count { it.trimStart().startsWith("nameserver ") }
        val searchDomains = lines.firstOrNull { it.trimStart().startsWith("search ") }
            ?.trim()?.split(Regex("\\s+")).orEmpty().drop(1).size
        val options = lines.firstOrNull { it.trimStart().startsWith("options ") }
            ?.trim()?.split(Regex("\\s+")).orEmpty().drop(1)
        linkedMapOf(
            "configured" to lines.isNotEmpty(),
            "nameserverCount" to nameservers,
            "searchDomainCount" to searchDomains,
            "optionCount" to options.size,
            "options" to options.take(16),
        )
    }.getOrDefault(emptyMap())

    /** Reports NSS lookup order while omitting configured databases and domain values. */
    private fun collectNetworkNameServiceAnalytics(): Map<String, Any?> = runCatching {
        val line = File("/etc/nsswitch.conf").takeIf(File::isFile)?.readLines()
            ?.firstOrNull { it.trimStart().startsWith("hosts:") }
        val methods = line?.substringAfter(':')?.trim()?.split(Regex("\\s+"))
            ?.map { it.substringBefore('[') }?.filter(String::isNotBlank).orEmpty()
        linkedMapOf(
            "configured" to (line != null),
            "methodCount" to methods.size,
            "methods" to methods.take(16),
        )
    }.getOrDefault(emptyMap())

    /** Reports kernel notification and asynchronous I/O capacity limits. */
    private fun collectKernelNotificationPolicy(): Map<String, Any?> = runCatching {
        val paths = linkedMapOf(
            "inotifyMaxUserWatches" to "/proc/sys/fs/inotify/max_user_watches",
            "inotifyMaxUserInstances" to "/proc/sys/fs/inotify/max_user_instances",
            "inotifyMaxQueuedEvents" to "/proc/sys/fs/inotify/max_queued_events",
            "aioMax" to "/proc/sys/fs/aio-max-nr",
            "aioCurrent" to "/proc/sys/fs/aio-nr",
        )
        val values = paths.mapValues { (_, path) ->
            File(path).takeIf(File::isFile)?.readText()?.trim()?.toLongOrNull()
        }
        linkedMapOf(
            "configured" to values.values.any { it != null },
            "limits" to values,
            "aioUsageRatio" to ((values["aioCurrent"] as? Long)?.let { current ->
                (values["aioMax"] as? Long)?.takeIf { it > 0 }?.let { max -> current.toDouble() / max }
            }),
        )
    }.getOrDefault(emptyMap())

    /** Reports non-sensitive kernel tunables that explain process and IPC behavior. */
    private fun collectKernelSysctl(): Map<String, Any?> = runCatching {
        val paths = linkedMapOf(
            "pidMax" to "/proc/sys/kernel/pid_max",
            "threadsMax" to "/proc/sys/kernel/threads-max",
            "maxMapCount" to "/proc/sys/vm/max_map_count",
            "overcommitMemory" to "/proc/sys/vm/overcommit_memory",
            "overcommitRatio" to "/proc/sys/vm/overcommit_ratio",
            "dirtyRatio" to "/proc/sys/vm/dirty_ratio",
            "dirtyBackgroundRatio" to "/proc/sys/vm/dirty_background_ratio",
            "fileMax" to "/proc/sys/fs/file-max",
            "pipeMaxSize" to "/proc/sys/fs/pipe-max-size",
            "corePatternConfigured" to "/proc/sys/kernel/core_pattern",
        )
        val values = paths.mapValues { (_, path) -> readTextFile(path) }
        linkedMapOf(
            "configured" to values.values.any { it != null },
            "values" to values,
        )
    }.getOrDefault(emptyMap())

    /** Reports block-device queue, discard, and rotational characteristics. */
    private fun collectBlockDeviceAnalytics(): Map<String, Any?> = runCatching {
        val devices = File("/sys/block").takeIf(File::isDirectory)?.listFiles().orEmpty()
            .filter { it.isDirectory && !it.name.startsWith("loop") && !it.name.startsWith("ram") }
            .sortedBy { it.name }
            .take(64)
            .map { device ->
                val queue = File(device, "queue")
                linkedMapOf<String, Any?>(
                    "name" to device.name,
                    "sizeSectors" to readLongFile("${device.path}/size"),
                    "readOnly" to readTextFile("${device.path}/ro")?.toIntOrNull()?.let { it == 1 },
                    "removable" to readTextFile("${device.path}/removable")?.toIntOrNull()?.let { it == 1 },
                    "rotational" to readTextFile("${queue.path}/rotational")?.toIntOrNull()?.let { it == 1 },
                    "logicalBlockSize" to readLongFile("${queue.path}/logical_block_size"),
                    "physicalBlockSize" to readLongFile("${queue.path}/physical_block_size"),
                    "maxSectorsKb" to readLongFile("${queue.path}/max_sectors_kb"),
                    "scheduler" to readTextFile("${queue.path}/scheduler"),
                )
            }
        linkedMapOf(
            "configured" to devices.isNotEmpty(),
            "deviceCount" to devices.size,
            "rotationalDeviceCount" to devices.count { it["rotational"] == true },
            "devices" to devices,
        )
    }.getOrDefault(emptyMap())

    /** Reports available kernel modules and filesystem drivers without module parameters. */
    private fun collectKernelComponentAnalytics(): Map<String, Any?> = runCatching {
        val modules = File("/proc/modules").takeIf(File::isFile)?.readLines().orEmpty()
            .mapNotNull { it.trim().split(Regex("\\s+")).firstOrNull()?.takeIf(String::isNotBlank) }
            .distinct().sorted().take(512)
        val fileSystems = File("/proc/filesystems").takeIf(File::isFile)?.readLines().orEmpty()
            .mapNotNull { it.trim().split(Regex("\\s+")).lastOrNull()?.takeIf(String::isNotBlank) }
            .distinct().sorted().take(256)
        linkedMapOf(
            "configured" to (modules.isNotEmpty() || fileSystems.isNotEmpty()),
            "moduleCount" to modules.size,
            "modules" to modules,
            "fileSystemDriverCount" to fileSystems.size,
            "fileSystemDrivers" to fileSystems,
        )
    }.getOrDefault(emptyMap())

    /** Reports aggregate transmit/receive queue pressure from procfs socket tables. */
    private fun collectNetworkSocketQueues(): Map<String, Any?> = runCatching {
        val tables = listOf("/proc/net/tcp", "/proc/net/tcp6", "/proc/net/udp", "/proc/net/udp6")
        var socketCount = 0
        var txBytes = 0L
        var rxBytes = 0L
        var queuedSockets = 0
        tables.forEach { path ->
            File(path).takeIf(File::isFile)?.readLines()?.drop(1).orEmpty().forEach { line ->
                val fields = line.trim().split(Regex("\\s+"))
                val queue = fields.getOrNull(4)?.split(':') ?: return@forEach
                if (queue.size != 2) return@forEach
                val tx = queue[0].toLongOrNull(16) ?: return@forEach
                val rx = queue[1].toLongOrNull(16) ?: return@forEach
                socketCount++
                txBytes += tx
                rxBytes += rx
                if (tx > 0 || rx > 0) queuedSockets++
            }
        }
        linkedMapOf(
            "configured" to (socketCount > 0),
            "socketCount" to socketCount,
            "queuedSocketCount" to queuedSockets,
            "txQueueBytes" to txBytes,
            "rxQueueBytes" to rxBytes,
        )
    }.getOrDefault(emptyMap())

    /** Reports power-source state while excluding model and serial identifiers. */
    private fun collectPowerSupplyAnalytics(): Map<String, Any?> = runCatching {
        val supplies = File("/sys/class/power_supply").takeIf(File::isDirectory)?.listFiles().orEmpty()
            .sortedBy { it.name }.take(32).map { supply ->
                linkedMapOf<String, Any?>(
                    "name" to supply.name,
                    "type" to readTextFile("${supply.path}/type"),
                    "status" to readTextFile("${supply.path}/status"),
                    "capacityPercent" to readLongFile("${supply.path}/capacity"),
                    "online" to readLongFile("${supply.path}/online")?.let { it == 1L },
                    "voltageNowMicrovolts" to readLongFile("${supply.path}/voltage_now"),
                    "currentNowMicroamps" to readLongFile("${supply.path}/current_now"),
                    "powerNowMicrowatts" to readLongFile("${supply.path}/power_now"),
                )
            }
        linkedMapOf(
            "configured" to supplies.isNotEmpty(),
            "supplyCount" to supplies.size,
            "batteryCount" to supplies.count { it["type"] == "Battery" },
            "supplies" to supplies,
        )
    }.getOrDefault(emptyMap())

    /** Reports firmware and machine identity fields, deliberately omitting serials and UUIDs. */
    private fun collectFirmwareAnalytics(): Map<String, Any?> = runCatching {
        val fields = linkedMapOf(
            "biosVendor" to "/sys/class/dmi/id/bios_vendor",
            "biosVersion" to "/sys/class/dmi/id/bios_version",
            "biosDate" to "/sys/class/dmi/id/bios_date",
            "boardVendor" to "/sys/class/dmi/id/board_vendor",
            "boardName" to "/sys/class/dmi/id/board_name",
            "productName" to "/sys/class/dmi/id/product_name",
            "productVersion" to "/sys/class/dmi/id/product_version",
            "chassisType" to "/sys/class/dmi/id/chassis_type",
        ).mapValues { (_, path) -> readTextFile(path) }
        linkedMapOf(
            "configured" to fields.values.any { it != null },
            "fields" to fields,
        )
    }.getOrDefault(emptyMap())

    /** Reports DRM graphics devices without collecting device paths or serial identifiers. */
    private fun collectGraphicsAnalytics(): Map<String, Any?> = runCatching {
        val devices = File("/sys/class/drm").takeIf(File::isDirectory)?.listFiles().orEmpty()
            .filter { it.name.matches(Regex("card\\d+")) }
            .sortedBy { it.name }.take(32).map { card ->
                val device = File(card, "device")
                linkedMapOf<String, Any?>(
                    "name" to card.name,
                    "vendorId" to readTextFile("${device.path}/vendor"),
                    "deviceId" to readTextFile("${device.path}/device"),
                    "driver" to device.resolve("driver").canonicalFile.name.takeIf { it.isNotBlank() },
                    "bootVga" to readTextFile("${device.path}/boot_vga")?.toIntOrNull()?.let { it == 1 },
                    "renderNodePresent" to File("/dev/dri/renderD128").exists(),
                    "connectors" to File("/sys/class/drm").listFiles().orEmpty()
                        .filter { it.name.startsWith("${card.name}-") }
                        .mapNotNull { connector ->
                            linkedMapOf<String, Any?>(
                                "name" to connector.name.removePrefix("${card.name}-"),
                                "status" to readTextFile("${connector.path}/status"),
                                "enabled" to readTextFile("${connector.path}/enabled"),
                            )
                        }.take(32),
                )
            }
        linkedMapOf(
            "configured" to devices.isNotEmpty(),
            "deviceCount" to devices.size,
            "devices" to devices,
        )
    }.getOrDefault(emptyMap())

    /** Reports PCI and USB topology without exposing device serial numbers or physical paths. */
    private fun collectHardwareBusAnalytics(): Map<String, Any?> = runCatching {
        fun devices(root: String, limit: Int): List<Map<String, Any?>> = File(root)
            .takeIf(File::isDirectory)?.listFiles().orEmpty().sortedBy { it.name }.take(limit).map { device ->
                linkedMapOf(
                    "name" to device.name,
                    "vendorId" to readTextFile("${device.path}/vendor"),
                    "deviceId" to readTextFile("${device.path}/device"),
                    "classId" to readTextFile("${device.path}/class"),
                    "driver" to device.resolve("driver").canonicalFile.name.takeIf { it.isNotBlank() },
                    "ueventType" to readTextFile("${device.path}/uevent")?.lineSequence()
                        ?.firstOrNull { it.startsWith("DEVTYPE=") }?.substringAfter('='),
                )
            }
        val pci = devices("/sys/bus/pci/devices", 128)
        val usb = devices("/sys/bus/usb/devices", 128)
        linkedMapOf(
            "configured" to (pci.isNotEmpty() || usb.isNotEmpty()),
            "pciCount" to pci.size,
            "usbCount" to usb.size,
            "pci" to pci,
            "usb" to usb,
        )
    }.getOrDefault(emptyMap())

    /** Reports link state and non-sensitive interface counters without addresses. */
    private fun collectNetworkLinkAnalytics(): Map<String, Any?> = runCatching {
        val interfaces = File("/sys/class/net").takeIf(File::isDirectory)?.listFiles().orEmpty()
            .sortedBy { it.name }.take(128).map { networkInterface ->
                fun stat(name: String) = readLongFile("${networkInterface.path}/statistics/$name")
                linkedMapOf<String, Any?>(
                    "name" to networkInterface.name,
                    "operState" to readTextFile("${networkInterface.path}/operstate"),
                    "carrier" to readLongFile("${networkInterface.path}/carrier")?.let { it == 1L },
                    "mtu" to readLongFile("${networkInterface.path}/mtu"),
                    "speedMbps" to readLongFile("${networkInterface.path}/speed"),
                    "duplex" to readTextFile("${networkInterface.path}/duplex"),
                    "rxBytes" to stat("rx_bytes"),
                    "rxPackets" to stat("rx_packets"),
                    "rxErrors" to stat("rx_errors"),
                    "rxDrops" to stat("rx_dropped"),
                    "txBytes" to stat("tx_bytes"),
                    "txPackets" to stat("tx_packets"),
                    "txErrors" to stat("tx_errors"),
                    "txDrops" to stat("tx_dropped"),
                    "collisions" to stat("collisions"),
                )
            }
        linkedMapOf(
            "configured" to interfaces.isNotEmpty(),
            "interfaceCount" to interfaces.size,
            "upCount" to interfaces.count { it["operState"] == "up" },
            "interfaces" to interfaces,
        )
    }.getOrDefault(emptyMap())

    /** Reports hypervisor and kernel time-source capabilities without host identifiers. */
    private fun collectVirtualizationAnalytics(): Map<String, Any?> = runCatching {
        val cpuFlags = File("/proc/cpuinfo").takeIf(File::isFile)?.useLines { lines ->
            lines.firstOrNull { it.startsWith("flags") || it.startsWith("Features") }
                ?.substringAfter(':')?.trim()?.split(Regex("\\s+"))?.filter(String::isNotBlank).orEmpty()
        }.orEmpty()
        val clockSource = "/sys/devices/system/clocksource/clocksource0"
        val available = readTextFile("$clockSource/available_clocksource")
            ?.split(Regex("\\s+"))?.filter(String::isNotBlank).orEmpty()
        linkedMapOf(
            "hypervisorFlagPresent" to cpuFlags.contains("hypervisor"),
            "hypervisorVendor" to readTextFile("/sys/class/dmi/id/sys_vendor"),
            "currentClockSource" to readTextFile("$clockSource/current_clocksource"),
            "availableClockSources" to available.take(32),
            "tscAvailable" to (cpuFlags.contains("constant_tsc") || cpuFlags.contains("nonstop_tsc")),
            "paravirtualizedClockFlag" to cpuFlags.any { it in setOf("kvmclock", "xenclock", "hv_time") },
        )
    }.getOrDefault(emptyMap())

    /** Reports boot security posture and allow-listed kernel command-line flags. */
    private fun collectBootSecurityAnalytics(): Map<String, Any?> = runCatching {
        val allowedKeys = setOf(
            "audit", "apparmor", "ima_appraise", "ima_tcb", "init_on_alloc", "init_on_free",
            "iommu", "mitigations", "module.sig_enforce", "pti", "random.trust_cpu", "selinux",
            "slab_nomerge", "spec_store_bypass_disable", "spectre_v2", "tsx_async_abort",
        )
        val commandLine = readTextFile("/proc/cmdline")?.split(Regex("\\s+"))?.mapNotNull { token ->
            val key = token.substringBefore('=')
            key.takeIf { it in allowedKeys }?.let { it to token.substringAfter('=', "enabled").take(64) }
        }?.toMap().orEmpty()
        linkedMapOf(
            "configured" to (commandLine.isNotEmpty() || File("/sys/kernel/security/lockdown").isFile),
            "kernelFlags" to commandLine,
            "lockdown" to readTextFile("/sys/kernel/security/lockdown"),
            "secureBoot" to readTextFile("/sys/firmware/efi/efivars/SecureBoot-8be4df61-93ca-11d2-aa0d-00e098032b8c")
                ?.let { bytes -> bytes.endsWith("\\u0001") || bytes.endsWith("1") },
            "selinuxEnforce" to readTextFile("/sys/fs/selinux/enforce")?.toIntOrNull()?.let { it == 1 },
            "apparmorEnabled" to File("/sys/module/apparmor").isDirectory,
        )
    }.getOrDefault(emptyMap())

    /** Reports kernel entropy/random settings and crypto algorithm inventory without key material. */
    private fun collectKernelCryptoAnalytics(): Map<String, Any?> = runCatching {
        val algorithms = File("/proc/crypto").takeIf(File::isFile)?.readLines().orEmpty()
            .mapNotNull { line ->
                val key = line.substringBefore(':').trim()
                if (key == "name" || key == "driver" || key == "type") line.substringAfter(':').trim()
                    .takeIf(String::isNotBlank) else null
            }.distinct().sorted().take(512)
        linkedMapOf(
            "configured" to (algorithms.isNotEmpty() || File("/proc/sys/kernel/random").isDirectory),
            "entropyAvailableBits" to readLongFile("/proc/sys/kernel/random/entropy_avail"),
            "poolSizeBits" to readLongFile("/proc/sys/kernel/random/poolsize"),
            "urandomMinReseedSeconds" to readLongFile("/proc/sys/kernel/random/urandom_min_reseed_secs"),
            "algorithmCount" to algorithms.size,
            "algorithms" to algorithms,
        )
    }.getOrDefault(emptyMap())

    /** Reports CPU idle/governor state and RTC availability without reading wall-clock history. */
    private fun collectPowerManagementAnalytics(): Map<String, Any?> = runCatching {
        val cpuRoot = File("/sys/devices/system/cpu")
        val policies = cpuRoot.listFiles().orEmpty().filter { it.name.startsWith("cpufreq/policy") }
        val idleStates = cpuRoot.listFiles().orEmpty().filter { it.name.matches(Regex("cpu\\d+")) }
            .flatMap { cpu -> File(cpu, "cpuidle").listFiles().orEmpty().filter { it.name.startsWith("state") } }
            .mapNotNull { state ->
                linkedMapOf<String, Any?>(
                    "name" to readTextFile("${state.path}/name"),
                    "usage" to readLongFile("${state.path}/usage"),
                    "timeMicroseconds" to readLongFile("${state.path}/time"),
                    "latencyMicroseconds" to readLongFile("${state.path}/latency"),
                )
            }.distinctBy { it["name"] to it["latencyMicroseconds"] }.take(64)
        linkedMapOf(
            "configured" to (policies.isNotEmpty() || idleStates.isNotEmpty()),
            "governor" to readTextFile("${cpuRoot.path}/cpufreq/policy0/scaling_governor"),
            "availableGovernors" to readTextFile("${cpuRoot.path}/cpufreq/policy0/scaling_available_governors")
                ?.split(Regex("\\s+"))?.filter(String::isNotBlank).orEmpty(),
            "idleDriver" to readTextFile("${cpuRoot.path}/cpuidle/current_driver"),
            "idleStates" to idleStates,
            "rtcDevices" to File("/sys/class/rtc").listFiles().orEmpty().map { it.name }.sorted().take(16),
        )
    }.getOrDefault(emptyMap())

    /** Reports process capabilities and sandbox mode without resolving capability names or identities. */
    private fun collectProcessCapabilityAnalytics(): Map<String, Any?> = runCatching {
        val fields = File("/proc/self/status").takeIf(File::isFile)?.readLines().orEmpty()
            .mapNotNull { line ->
                val separator = line.indexOf(':')
                if (separator <= 0) null else line.substring(0, separator) to line.substring(separator + 1).trim()
            }.toMap()
        linkedMapOf(
            "configured" to fields.isNotEmpty(),
            "capInheritable" to fields["CapInh"],
            "capPermitted" to fields["CapPrm"],
            "capEffective" to fields["CapEff"],
            "capBnd" to fields["CapBnd"],
            "capAmbient" to fields["CapAmb"],
            "noNewPrivileges" to fields["NoNewPrivs"]?.toIntOrNull()?.let { it == 1 },
            "secureBits" to fields["Seccomp"]?.toIntOrNull(),
            "seccompMode" to fields["Seccomp"],
            "speculationStoreBypass" to fields["Speculation_Store_Bypass"],
            "speculationIndirectBranch" to fields["SpeculationIndirectBranch"],
        )
    }.getOrDefault(emptyMap())

    /** Reports PID 1 and service-manager capabilities without enumerating service names. */
    private fun collectInitSystemAnalytics(): Map<String, Any?> = runCatching {
        val pidOneComm = readTextFile("/proc/1/comm")
        val pidOneExe = File("/proc/1/exe").takeIf { it.exists() }?.canonicalFile?.name
        val systemdVersion = if (pidOneComm == "systemd" || pidOneExe == "systemd") {
            File("/run/systemd/system").takeIf(File::isDirectory)?.let { "systemd" }
        } else null
        linkedMapOf(
            "configured" to (pidOneComm != null || pidOneExe != null),
            "pidOneName" to pidOneComm,
            "pidOneExecutable" to pidOneExe,
            "serviceManager" to systemdVersion,
            "systemdSystemScope" to File("/run/systemd/system").isDirectory,
            "systemdUserScope" to File("/run/user").isDirectory,
            "containerManagerMarkers" to listOf(
                "/run/systemd/container", "/run/.containerenv", "/.dockerenv", "/run/kubernetes",
            ).filter { File(it).exists() }.map { it.substringAfterLast('/') },
        )
    }.getOrDefault(emptyMap())

    /** Reports BPF and user-namespace policy without enumerating programs or namespace identities. */
    private fun collectKernelSandboxAnalytics(): Map<String, Any?> = runCatching {
        linkedMapOf(
            "configured" to (File("/sys/fs/bpf").exists() || File("/proc/sys/kernel").isDirectory),
            "bpfFilesystemPresent" to File("/sys/fs/bpf").isDirectory,
            "unprivilegedBpfDisabled" to readLongFile("/proc/sys/kernel/unprivileged_bpf_disabled"),
            "unprivilegedUserNamespaces" to readLongFile("/proc/sys/kernel/unprivileged_userns_clone"),
            "maxUserNamespaces" to readLongFile("/proc/sys/user/max_user_namespaces"),
            "maxPidNamespaces" to readLongFile("/proc/sys/user/max_pid_namespaces"),
            "maxNetworkNamespaces" to readLongFile("/proc/sys/user/max_net_namespaces"),
            "cgroupNamespacePresent" to File("/proc/self/ns/cgroup").exists(),
            "mountNamespacePresent" to File("/proc/self/ns/mnt").exists(),
            "userNamespacePresent" to File("/proc/self/ns/user").exists(),
        )
    }.getOrDefault(emptyMap())

    /** Reports aggregate file-lock pressure without exposing paths, owners, or inode identifiers. */
    private fun collectFileLockAnalytics(): Map<String, Any?> = runCatching {
        val rows = File("/proc/locks").takeIf(File::isFile)?.readLines().orEmpty()
        val typeCounts = rows.mapNotNull { row ->
            val fields = row.trim().split(Regex("\\s+"))
            fields.getOrNull(1)?.let { type -> type to fields.getOrNull(3) }
        }.groupingBy { it.first }.eachCount().toSortedMap()
        val modeCounts = rows.mapNotNull { row ->
            row.trim().split(Regex("\\s+")).getOrNull(2)
        }.groupingBy { it }.eachCount().toSortedMap()
        linkedMapOf(
            "configured" to rows.isNotEmpty(),
            "total" to rows.size,
            "byType" to typeCounts,
            "byMode" to modeCounts,
        )
    }.getOrDefault(emptyMap())

    /** Reports kernel observability restrictions that affect diagnostics and profilers. */
    private fun collectKernelDebugPolicy(): Map<String, Any?> = runCatching {
        val values = linkedMapOf(
            "dmesgRestrict" to readLongFile("/proc/sys/kernel/dmesg_restrict"),
            "kptrRestrict" to readLongFile("/proc/sys/kernel/kptr_restrict"),
            "perfEventParanoid" to readLongFile("/proc/sys/kernel/perf_event_paranoid"),
            "ptraceScope" to readLongFile("/proc/sys/kernel/yama/ptrace_scope"),
            "printk" to readTextFile("/proc/sys/kernel/printk"),
            "kexecLoadDisabled" to readLongFile("/proc/sys/kernel/kexec_load_disabled"),
            "coreUsesPid" to readLongFile("/proc/sys/kernel/core_uses_pid"),
        )
        linkedMapOf(
            "configured" to values.values.any { it != null },
            "values" to values,
        )
    }.getOrDefault(emptyMap())

    /** Reports aggregate Linux memory-zone counters without exposing addresses or zone names. */
    private fun collectMemoryZoneAnalytics(): Map<String, Any?> = runCatching {
        val rows = File("/proc/zoneinfo").takeIf(File::isFile)?.readLines().orEmpty()
        fun sum(key: String): Long = rows.mapNotNull { line ->
            val trimmed = line.trim()
            if (!trimmed.startsWith("$key ")) null else trimmed.substringAfterLast(' ').toLongOrNull()
        }.sum()
        val zoneCount = rows.count { it.startsWith("Node ") && it.contains(", zone ") }
        linkedMapOf(
            "configured" to rows.isNotEmpty(),
            "zoneCount" to zoneCount,
            "freePages" to sum("pages free"),
            "managedPages" to sum("managed"),
            "presentPages" to sum("present"),
            "minWatermarkPages" to sum("min"),
            "lowWatermarkPages" to sum("low"),
            "highWatermarkPages" to sum("high"),
            "activeAnonPages" to sum("nr_active_anon"),
            "inactiveAnonPages" to sum("nr_inactive_anon"),
            "activeFilePages" to sum("nr_active_file"),
            "inactiveFilePages" to sum("nr_inactive_file"),
        )
    }.getOrDefault(emptyMap())

    /** Reports aggregate slab allocator usage without retaining cache names or object identities. */
    private fun collectKernelSlabAnalytics(): Map<String, Any?> = runCatching {
        val rows = File("/proc/slabinfo").takeIf(File::isFile)?.readLines().orEmpty()
            .dropWhile { !it.startsWith("slabinfo - version") }.drop(2)
        var activeObjects = 0L
        var totalObjects = 0L
        var activeBytes = 0L
        var totalBytes = 0L
        var cacheCount = 0
        rows.forEach { row ->
            val fields = row.trim().split(Regex("\\s+"))
            if (fields.size < 4) return@forEach
            val active = fields.getOrNull(1)?.toLongOrNull() ?: return@forEach
            val total = fields.getOrNull(2)?.toLongOrNull() ?: return@forEach
            val objectSize = fields.getOrNull(3)?.toLongOrNull() ?: return@forEach
            cacheCount++
            activeObjects += active
            totalObjects += total
            activeBytes += active * objectSize
            totalBytes += total * objectSize
        }
        linkedMapOf(
            "configured" to (cacheCount > 0),
            "cacheCount" to cacheCount,
            "activeObjects" to activeObjects,
            "totalObjects" to totalObjects,
            "activeBytes" to activeBytes,
            "totalBytes" to totalBytes,
        )
    }.getOrDefault(emptyMap())

    /** Reports workqueue policy and aggregate queue capabilities without queue names. */
    private fun collectKernelWorkqueueAnalytics(): Map<String, Any?> = runCatching {
        val root = File("/sys/devices/virtual/workqueue")
        val queues = root.takeIf(File::isDirectory)?.listFiles().orEmpty()
            .filter { it.isDirectory && !it.name.startsWith("cpumask") }
            .map { queue ->
                linkedMapOf<String, Any?>(
                    "maxActive" to readLongFile("${queue.path}/max_active"),
                    "cpumask" to readTextFile("${queue.path}/cpumask"),
                    "nice" to readLongFile("${queue.path}/nice"),
                    "perCpu" to File(queue, "per_cpu").isDirectory,
                )
            }.take(128)
        linkedMapOf(
            "configured" to (root.isDirectory || queues.isNotEmpty()),
            "queueCount" to queues.size,
            "powerEfficient" to readTextFile("${root.path}/power_efficient")?.toIntOrNull()?.let { it == 1 },
            "onlineCpuMask" to readTextFile("${root.path}/cpumask"),
            "queues" to queues,
        )
    }.getOrDefault(emptyMap())

    /** Reports io_uring/AIO policy and counters without inspecting submitted requests or file paths. */
    private fun collectIoUringPolicy(): Map<String, Any?> = runCatching {
        linkedMapOf(
            "configured" to File("/proc/sys/kernel").isDirectory,
            "ioUringDisabled" to readLongFile("/proc/sys/kernel/io_uring_disabled"),
            "ioUringGroup" to readLongFile("/proc/sys/kernel/io_uring_group"),
            "aioMaximum" to readLongFile("/proc/sys/fs/aio-max-nr"),
            "aioCurrent" to readLongFile("/proc/sys/fs/aio-nr"),
            "aioUsageRatio" to ((readLongFile("/proc/sys/fs/aio-nr") ?: 0L).toDouble() /
                (readLongFile("/proc/sys/fs/aio-max-nr") ?: 0L).coerceAtLeast(1L)),
            "ioUringProcfsPresent" to File("/proc/sys/kernel/io_uring_disabled").isFile,
        )
    }.getOrDefault(emptyMap())

    /** Reports cgroup v2 delegation and controller topology without child-group names. */
    private fun collectCgroupTopologyAnalytics(): Map<String, Any?> = runCatching {
        val root = File("/sys/fs/cgroup")
        val controllerList = readTextFile("${root.path}/cgroup.controllers")
            ?.split(Regex("\\s+"))?.filter(String::isNotBlank).orEmpty()
        val subtreeList = readTextFile("${root.path}/cgroup.subtree_control")
            ?.split(Regex("\\s+"))?.filter(String::isNotBlank).orEmpty()
        linkedMapOf(
            "configured" to root.isDirectory,
            "type" to readTextFile("${root.path}/cgroup.type"),
            "controllers" to controllerList,
            "subtreeControllers" to subtreeList,
            "events" to readKeyValueFile("${root.path}/cgroup.events"),
            "freezeState" to readTextFile("${root.path}/cgroup.freeze"),
            "pressureFiles" to listOf("cpu.pressure", "memory.pressure", "io.pressure")
                .filter { File(root, it).isFile },
        )
    }.getOrDefault(emptyMap())

    /** Reports aggregate kernel socket/conntrack pressure without addresses or connection identities. */
    private fun collectNetworkSocketPressure(): Map<String, Any?> = runCatching {
        val socketStats = listOf("/proc/net/sockstat", "/proc/net/sockstat6")
            .flatMap { path -> File(path).takeIf(File::isFile)?.readLines().orEmpty() }
            .mapNotNull { line ->
                val fields = line.trim().split(Regex("\\s+"))
                val protocol = fields.firstOrNull()?.removeSuffix(":") ?: return@mapNotNull null
                fields.drop(1).windowed(2, 2).mapNotNull { pair ->
                    pair.getOrNull(0)?.let { key -> pair.getOrNull(1)?.toLongOrNull()?.let { "${protocol}_$key" to it } }
                }
            }.flatten().toMap()
        val conntrackCurrent = readLongFile("/proc/sys/net/netfilter/nf_conntrack_count")
        val conntrackMaximum = readLongFile("/proc/sys/net/netfilter/nf_conntrack_max")
        linkedMapOf(
            "configured" to (socketStats.isNotEmpty() || conntrackCurrent != null),
            "socketStats" to socketStats,
            "conntrackCurrent" to conntrackCurrent,
            "conntrackMaximum" to conntrackMaximum,
            "conntrackUsageRatio" to conntrackCurrent?.toDouble()?.div((conntrackMaximum ?: 0L).coerceAtLeast(1L)),
        )
    }.getOrDefault(emptyMap())

    /** Reports buddy allocator free blocks by order without exposing physical addresses. */
    private fun collectBuddyAllocatorAnalytics(): Map<String, Any?> = runCatching {
        val rows = File("/proc/buddyinfo").takeIf(File::isFile)?.readLines().orEmpty()
        val orderTotals = linkedMapOf<Int, Long>()
        var nodeCount = 0
        var zoneCount = 0
        rows.forEach { row ->
            val fields = row.trim().split(Regex("\\s+"))
            val zoneIndex = fields.indexOfFirst { it == "zone" }
            if (zoneIndex < 0 || fields.size <= zoneIndex + 1) return@forEach
            nodeCount += if (fields.firstOrNull()?.startsWith("Node") == true) 1 else 0
            zoneCount++
            fields.drop(zoneIndex + 2).forEachIndexed { order, value ->
                value.toLongOrNull()?.let { orderTotals[order] = (orderTotals[order] ?: 0L) + it }
            }
        }
        linkedMapOf(
            "configured" to rows.isNotEmpty(),
            "nodeCount" to nodeCount,
            "zoneCount" to zoneCount,
            "freeBlocksByOrder" to orderTotals,
        )
    }.getOrDefault(emptyMap())

    /** Reports memory compaction/reclaim policy and aggregate vmstat counters. */
    private fun collectMemoryCompactionAnalytics(): Map<String, Any?> = runCatching {
        val keys = setOf(
            "pgscan_kswapd", "pgscan_direct", "pgsteal_kswapd", "pgsteal_direct",
            "compact_migrate_scanned", "compact_free_scanned", "compact_stall", "compact_success",
            "compact_fail", "kswapd_inodesteal", "pgactivate", "pgdeactivate",
        )
        val counters = File("/proc/vmstat").takeIf(File::isFile)?.readLines().orEmpty()
            .mapNotNull { line ->
                val fields = line.trim().split(Regex("\\s+"))
                if (fields.size == 2 && fields[0] in keys) fields[0] to fields[1].toLongOrNull() else null
            }.toMap()
        linkedMapOf(
            "configured" to counters.isNotEmpty(),
            "compactMemoryPolicy" to readLongFile("/proc/sys/vm/compact_memory"),
            "watermarkBoostFactor" to readLongFile("/proc/sys/vm/watermark_boost_factor"),
            "watermarkScaleFactor" to readLongFile("/proc/sys/vm/watermark_scale_factor"),
            "zoneReclaimMode" to readLongFile("/proc/sys/vm/zone_reclaim_mode"),
            "counters" to counters,
        )
    }.getOrDefault(emptyMap())

    /** Reports kernel panic, watchdog, and hung-task policies without reading kernel logs. */
    private fun collectKernelFaultPolicy(): Map<String, Any?> = runCatching {
        val values = linkedMapOf(
            "panicOnOops" to readLongFile("/proc/sys/kernel/panic_on_oops"),
            "panicTimeoutSeconds" to readLongFile("/proc/sys/kernel/panic"),
            "hungTaskTimeoutSeconds" to readLongFile("/proc/sys/kernel/hung_task_timeout_secs"),
            "hungTaskPanic" to readLongFile("/proc/sys/kernel/hung_task_panic"),
            "softlockupPanic" to readLongFile("/proc/sys/kernel/softlockup_panic"),
            "nmiWatchdog" to readLongFile("/proc/sys/kernel/nmi_watchdog"),
            "watchdogThresholdSeconds" to readLongFile("/proc/sys/kernel/watchdog_thresh"),
            "sysrqEnabled" to readLongFile("/proc/sys/kernel/sysrq"),
            "corePattern" to readTextFile("/proc/sys/kernel/core_pattern"),
        )
        linkedMapOf(
            "configured" to values.values.any { it != null },
            "values" to values,
        )
    }.getOrDefault(emptyMap())

    /** Reports aggregate descriptor target types without exposing descriptor paths or endpoint names. */
    private fun collectProcessFdTypeAnalytics(): Map<String, Any?> = runCatching {
        val counts = linkedMapOf<String, Int>()
        File("/proc/self/fd").takeIf(File::isDirectory)?.listFiles().orEmpty().forEach { descriptor ->
            val target = runCatching { descriptor.canonicalFile.path }.getOrNull() ?: return@forEach
            val type = when {
                target.startsWith("socket:") -> "socket"
                target.startsWith("pipe:") -> "pipe"
                target.startsWith("anon_inode:") -> "anonInode"
                target.startsWith("/dev/") -> "device"
                target.startsWith("/") -> "file"
                else -> "other"
            }
            counts[type] = (counts[type] ?: 0) + 1
        }
        linkedMapOf(
            "configured" to counts.isNotEmpty(),
            "total" to counts.values.sum(),
            "byType" to counts.toSortedMap(),
        )
    }.getOrDefault(emptyMap())

    /** Reports aggregate JVM thread states without retaining names, stack traces, or lock identities. */
    private fun collectThreadStateAnalytics(bean: java.lang.management.ThreadMXBean): Map<String, Any?> = runCatching {
        val infos = bean.getThreadInfo(bean.allThreadIds, 0).orEmpty().filterNotNull()
        val states = infos.groupingBy { it.threadState.name }.eachCount().toSortedMap()
        linkedMapOf(
            "configured" to infos.isNotEmpty(),
            "sampledCount" to infos.size,
            "stateCounts" to states,
            "daemonCount" to infos.count { it.isDaemon },
            "blockedCount" to infos.count { it.threadState == Thread.State.BLOCKED },
            "waitingCount" to infos.count { it.threadState == Thread.State.WAITING || it.threadState == Thread.State.TIMED_WAITING },
            "lockOwnerCount" to infos.count { it.lockOwnerId >= 0 },
        )
    }.getOrDefault(emptyMap())

    /** Reports cgroup hierarchy/controller membership while intentionally omitting group paths. */
    private fun collectCgroupMembershipAnalytics(): Map<String, Any?> = runCatching {
        val entries = File("/proc/self/cgroup").takeIf(File::isFile)?.readLines().orEmpty().mapNotNull { line ->
            val fields = line.split(':', limit = 3)
            if (fields.size != 3) null else linkedMapOf(
                "hierarchyId" to fields[0],
                "controllers" to fields[1].split(',').filter(String::isNotBlank).sorted(),
                "pathPresent" to fields[2].isNotBlank(),
            )
        }
        linkedMapOf(
            "configured" to entries.isNotEmpty(),
            "hierarchyCount" to entries.size,
            "controllerNames" to entries.flatMap { it["controllers"] as? List<*> ?: emptyList<Any?>() }
                .filterIsInstance<String>().distinct().sorted(),
            "entries" to entries,
        )
    }.getOrDefault(emptyMap())

    /** Reports process timer slack and aggregate timer capabilities without timer IDs. */
    private fun collectProcessTimerAnalytics(): Map<String, Any?> = runCatching {
        val timerEntries = File("/proc/self/timers").takeIf(File::isFile)?.readLines().orEmpty()
        linkedMapOf(
            "configured" to (File("/proc/self/timerslack_ns").isFile || timerEntries.isNotEmpty()),
            "timerSlackNanoseconds" to readLongFile("/proc/self/timerslack_ns"),
            "activePosixTimerCount" to timerEntries.count { it.trimStart().startsWith("ID:") },
            "monotonicClockAvailable" to runCatching { System.nanoTime() >= 0L }.getOrDefault(false),
            "timerFdPresent" to File("/proc/self/fd").listFiles().orEmpty().any { descriptor ->
                runCatching { descriptor.canonicalPath.contains("timerfd") }.getOrDefault(false)
            },
        )
    }.getOrDefault(emptyMap())

    /** Reports RCU scheduling policy knobs without reading grace-period trace data. */
    private fun collectKernelRcuAnalytics(): Map<String, Any?> = runCatching {
        val root = "/sys/kernel/rcu"
        linkedMapOf(
            "configured" to File(root).isDirectory,
            "expedited" to readTextFile("$root/rcu_expedited"),
            "normal" to readTextFile("$root/rcu_normal"),
            "nocbPoll" to readTextFile("$root/rcu_nocb_poll"),
            "nocbs" to readTextFile("$root/nocbs"),
            "rcuTaskStallTimeout" to readLongFile("/proc/sys/kernel/rcu_task_stall_timeout"),
            "traceDirectoryPresent" to File("/sys/kernel/debug/tracing/events/rcu").isDirectory,
        )
    }.getOrDefault(emptyMap())

    /** Reports network sysctl policy without addresses, routes, or interface identities. */
    private fun collectNetworkKernelPolicy(): Map<String, Any?> = runCatching {
        val values = linkedMapOf(
            "ipv4Forwarding" to readLongFile("/proc/sys/net/ipv4/ip_forward"),
            "ipv6Forwarding" to readLongFile("/proc/sys/net/ipv6/conf/all/forwarding"),
            "tcpSyncookies" to readLongFile("/proc/sys/net/ipv4/tcp_syncookies"),
            "tcpFinTimeoutSeconds" to readLongFile("/proc/sys/net/ipv4/tcp_fin_timeout"),
            "tcpKeepaliveTimeSeconds" to readLongFile("/proc/sys/net/ipv4/tcp_keepalive_time"),
            "tcpKeepaliveProbes" to readLongFile("/proc/sys/net/ipv4/tcp_keepalive_probes"),
            "tcpKeepaliveIntervalSeconds" to readLongFile("/proc/sys/net/ipv4/tcp_keepalive_intvl"),
            "tcpMemoryPages" to readTextFile("/proc/sys/net/ipv4/tcp_mem"),
            "localPortRange" to readTextFile("/proc/sys/net/ipv4/ip_local_port_range"),
            "ipv4AcceptRedirects" to readLongFile("/proc/sys/net/ipv4/conf/all/accept_redirects"),
            "ipv4RpFilter" to readLongFile("/proc/sys/net/ipv4/conf/all/rp_filter"),
            "ipv6AcceptRedirects" to readLongFile("/proc/sys/net/ipv6/conf/all/accept_redirects"),
        )
        linkedMapOf(
            "configured" to values.values.any { it != null },
            "values" to values,
        )
    }.getOrDefault(emptyMap())

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

    /** Collects signal masks and queue pressure without resolving signal names or user identities. */
    private fun collectProcessSignals(): Map<String, Any?> = runCatching {
        val values = File("/proc/self/status").takeIf(File::isFile)?.readLines().orEmpty()
            .mapNotNull { line ->
                val separator = line.indexOf(':')
                if (separator <= 0) null else line.substring(0, separator) to line.substring(separator + 1).trim()
            }.toMap()
        val queue = values["SigQ"]?.split('/')?.mapNotNull(String::toLongOrNull)
        linkedMapOf(
            "pendingMask" to values["ShdPnd"],
            "blockedMask" to values["SigBlk"],
            "ignoredMask" to values["SigIgn"],
            "caughtMask" to values["SigCgt"],
            "queuedSignals" to queue?.getOrNull(0),
            "queuedSignalLimit" to queue?.getOrNull(1),
            "pendingCount" to queue?.getOrNull(0),
        )
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
            "speculationStoreBypass" to value("Speculation_Store_Bypass"),
            "speculationIndirectBranch" to value("SpeculationIndirectBranch"),
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

    private fun collectProcessOomPolicy(): Map<String, Any?> = linkedMapOf(
        "score" to readLongFile("/proc/self/oom_score"),
        "adjustment" to readLongFile("/proc/self/oom_score_adj"),
        "killDisabled" to (readLongFile("/sys/fs/cgroup/memory.oom.group") == 0L),
    )

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

    /** Reads compact address-space counters from procfs; values are normalized to bytes. */
    private fun collectProcessAddressSpace(): Map<String, Any?> = runCatching {
        val values = File("/proc/self/statm").takeIf(File::isFile)?.readText()?.trim()
            ?.split(Regex("\\s+"))?.mapNotNull(String::toLongOrNull).orEmpty()
        val pageSize = 4096L
        linkedMapOf(
            "virtualBytes" to values.getOrNull(0)?.times(pageSize),
            "residentBytes" to values.getOrNull(1)?.times(pageSize),
            "sharedBytes" to values.getOrNull(2)?.times(pageSize),
            "textBytes" to values.getOrNull(3)?.times(pageSize),
            "libraryBytes" to values.getOrNull(4)?.times(pageSize),
            "dataBytes" to values.getOrNull(5)?.times(pageSize),
            "dirtyBytes" to values.getOrNull(6)?.times(pageSize),
        )
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

    /** Reads bounded scheduler counters for the current process from procfs. */
    private fun collectProcessSchedulerDetails(): Map<String, Any?> = runCatching {
        val values = File("/proc/self/sched").takeIf(File::isFile)?.readLines().orEmpty().mapNotNull { line ->
            val separator = line.indexOf(':')
            if (separator <= 0) return@mapNotNull null
            val key = line.substring(0, separator).trim()
            val value = line.substring(separator + 1).trim().split(Regex("\\s+"))
                .firstOrNull()?.toDoubleOrNull() ?: return@mapNotNull null
            key to value
        }.toMap()
        linkedMapOf(
            "voluntaryContextSwitches" to values["nr_voluntary_switches"],
            "involuntaryContextSwitches" to values["nr_involuntary_switches"],
            "migrations" to values["nr_migrations"],
            "runDelayNanoseconds" to values["se.sum_exec_runtime"],
            "averageRunnableTime" to values["se.avg.util_sum"],
        )
    }.getOrDefault(emptyMap())

    /** Reads scheduler execution, run-queue delay, and timeslice counters. */
    private fun collectProcessSchedStat(): Map<String, Long?> = runCatching {
        val values = File("/proc/self/schedstat").takeIf(File::isFile)?.readText()?.trim()
            ?.split(Regex("\\s+"))?.mapNotNull(String::toLongOrNull).orEmpty()
        linkedMapOf(
            "runTimeNs" to values.getOrNull(0),
            "runDelayNs" to values.getOrNull(1),
            "timeslices" to values.getOrNull(2),
        )
    }.getOrDefault(emptyMap())

    private fun collectSystemScheduling(): Map<String, Any?> = runCatching {
        val lines = File("/proc/stat").takeIf(File::isFile)?.readLines().orEmpty()
        val values = linkedMapOf<String, Any?>()
        val cpuRows = mutableListOf<Map<String, Long>>()
        lines.forEach { line ->
            val parts = line.trim().split(Regex("\\s+"))
            val key = parts.firstOrNull() ?: return@forEach
            val value = parts.getOrNull(1)?.toLongOrNull() ?: return@forEach
            if (key == "cpu" || key.matches(Regex("cpu\\d+"))) {
                val names = listOf("user", "nice", "system", "idle", "iowait", "irq", "softirq", "steal", "guest", "guestNice")
                cpuRows += names.mapIndexedNotNull { index, name ->
                    parts.getOrNull(index + 1)?.toLongOrNull()?.let { name to it }
                }.toMap()
            }
            when (key) {
                "ctxt" -> values["contextSwitches"] = value
                "intr" -> values["interrupts"] = value
                "processes" -> values["forks"] = value
                "procs_running" -> values["runnableProcesses"] = value
                "procs_blocked" -> values["blockedProcesses"] = value
            }
        }
        cpuRows.firstOrNull()?.let { aggregate ->
            values["cpuTotal"] = aggregate
            values["cpuCoreCount"] = cpuRows.count { it !== aggregate }
            values["cpuIdleTicks"] = aggregate["idle"]
            values["cpuIowaitTicks"] = aggregate["iowait"]
            values["cpuStealTicks"] = aggregate["steal"]
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
