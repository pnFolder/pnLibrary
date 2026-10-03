package ru.privatenull.pnlibrary.core.updates

import ru.privatenull.pnlibrary.api.platform.PlatformType
import ru.privatenull.pnlibrary.api.updates.ArtifactDescriptor
import ru.privatenull.pnlibrary.api.updates.ProductChange
import ru.privatenull.pnlibrary.api.updates.ProductId
import ru.privatenull.pnlibrary.api.updates.ProductRelease
import ru.privatenull.pnlibrary.api.updates.UpdatePlan
import ru.privatenull.pnlibrary.update.ArtifactDownloader
import ru.privatenull.pnlibrary.update.ArtifactSpecification
import ru.privatenull.pnlibrary.update.TransactionArtifact
import ru.privatenull.pnlibrary.update.TrustedHttpClient
import ru.privatenull.pnlibrary.update.UpdateTransaction
import java.io.ByteArrayInputStream
import java.nio.file.Path
import java.security.MessageDigest
import java.util.UUID

internal data class InstalledUpdateTarget(
    val currentJar: Path,
    val updateDirectory: Path,
)

/** Downloads every artifact in an update plan and prepares one restart transaction. */
internal class UpdatePlanStager(
    private val platform: PlatformType,
    private val dataFolder: Path,
    private val http: TrustedHttpClient,
    private val transaction: UpdateTransaction,
    private val maximumArtifactBytes: Long,
) {
    fun stage(
        planId: UUID,
        plan: UpdatePlan,
        installedTargets: Map<ProductId, InstalledUpdateTarget>,
    ) {
        val stagingDirectory = dataFolder.resolve("updates/staging/$planId")
        val artifacts = plan.changes.map { change ->
            prepareArtifact(change, plan, stagingDirectory, installedTargets[change.product])
        }
        transaction.prepareForRestart(artifacts)
    }

    private fun prepareArtifact(
        change: ProductChange,
        plan: UpdatePlan,
        stagingDirectory: Path,
        installedTarget: InstalledUpdateTarget?,
    ): TransactionArtifact {
        val release = plan.selected.single { it.product == change.product }
        val artifact = selectArtifact(change, release)
        val downloadUri = requireNotNull(artifact.downloadUri) {
            "Release artifact has no verified download URL: ${artifact.file}"
        }
        val bytes = http.get(downloadUri, maximumArtifactBytes.toInt())
        val stagedFile = stagingDirectory.resolve(change.product.value).resolve(artifact.file)
        ArtifactDownloader(maximumArtifactBytes).download(
            openStream = { ByteArrayInputStream(bytes) },
            destination = stagedFile,
        )

        val specification = ArtifactSpecification(
            product = change.product,
            version = change.to,
            supportedApi = release.supportedApi,
            fileName = artifact.file,
            size = bytes.size.toLong(),
            sha256 = sha256(bytes),
        )
        val target = installedTarget?.updateDirectory?.resolve(installedTarget.currentJar.fileName)
            ?: dataFolder.parent.resolve("update").resolve(artifact.file)

        return TransactionArtifact(
            specification = specification,
            source = stagedFile,
            target = target,
            rollbackSource = installedTarget?.currentJar ?: target,
        )
    }

    private fun selectArtifact(
        change: ProductChange,
        release: ProductRelease,
    ): ArtifactDescriptor = release.artifacts.firstOrNull { artifact ->
        artifact.platform == platform && artifact.supports(Runtime.version().feature())
    } ?: error("No ${platform.id} artifact for ${change.product} ${change.to}")

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { byte -> "%02x".format(byte) }
}
