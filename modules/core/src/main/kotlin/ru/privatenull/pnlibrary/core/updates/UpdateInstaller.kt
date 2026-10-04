package ru.privatenull.pnlibrary.core.updates

import ru.privatenull.pnlibrary.api.updates.UpdatePlan
import ru.privatenull.pnlibrary.api.updates.UpdatePlanSnapshot
import ru.privatenull.pnlibrary.spi.platform.PlatformAdapter
import ru.privatenull.pnlibrary.update.ArtifactVerifier
import ru.privatenull.pnlibrary.update.TrustedHttpClient
import ru.privatenull.pnlibrary.update.UpdateTransaction
import java.nio.file.Path

/** Stages update files and verifies a staged transaction after the next server start. */
internal class UpdateInstaller(
    platform: PlatformAdapter,
    dataFolder: Path,
    private val configuration: UpdateConfiguration,
    http: TrustedHttpClient,
) {
    private val transaction = UpdateTransaction(
        dataFolder.resolve("updates/transactions"),
        ArtifactVerifier(MAXIMUM_ARTIFACT_BYTES),
        dataFolder.parent,
    )
    private val stager = UpdatePlanStager(
        platform = platform.type,
        dataFolder = dataFolder,
        http = http,
        transaction = transaction,
        maximumArtifactBytes = MAXIMUM_ARTIFACT_BYTES,
    )

    fun recoverInterruptedTransactions() {
        transaction.recoverAll()
    }

    fun stage(snapshot: UpdatePlanSnapshot, registrations: List<RegisteredUpdate>) {
        val plan = requireNotNull(snapshot.plan) { "update plan has no installable target" }
        verifyPluginDownloadsAllowed(plan)

        val targets = registrations.associate { registration ->
            registration.descriptor.id to InstalledUpdateTarget(
                currentJar = registration.jar,
                updateDirectory = registration.updateDirectory,
            )
        }
        stager.stage(snapshot.id, plan, targets)
    }

    fun rollbackLatest() {
        val candidate = transaction.latestRollbackCandidate()
            ?: error("Нет сохранённого набора JAR для отката")
        transaction.rollback(candidate)
    }

    fun reconcilePending(
        registrations: List<RegisteredUpdate>,
        reportHealth: (healthy: Boolean, message: String) -> Unit,
    ) {
        val installedVersions = registrations.associate { registration ->
            registration.product to registration.version
        }

        transaction.awaitingHealth().forEach { pending ->
            val mismatches = pending.expectedVersions.filter { (product, expected) ->
                installedVersions[product] != expected
            }
            val healthy = mismatches.isEmpty()
            transaction.completeHealth(pending.journal, healthy)

            if (healthy) {
                reportHealth(true, "Обновлённые плагины загружены и работают.")
            } else {
                val details = mismatches.entries.joinToString { (product, expected) ->
                    "$product: ожидалась $expected, загружена ${installedVersions[product] ?: "не загружена"}"
                }
                reportHealth(
                    false,
                    "После перезапуска обновление не подтвердилось ($details). Доступен ручной откат.",
                )
            }
        }
    }

    private fun verifyPluginDownloadsAllowed(plan: UpdatePlan) {
        plan.changes
            .filterNot { change -> change.product.value == "pnlibrary" }
            .forEach { change ->
                val policy = configuration.plugins[change.product.value]
                require(configuration.pluginUpdatesEnabled && policy?.enabled != false) {
                    "Загрузка плагина ${change.product} отключена политикой обновлений"
                }
            }
    }

    private companion object {
        const val MAXIMUM_ARTIFACT_BYTES = 512L * 1024L * 1024L
    }
}
