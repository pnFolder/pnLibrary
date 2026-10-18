package ru.privatenull.pnlibrary.core.updates.install

import ru.privatenull.pnlibrary.api.platform.PlatformType
import ru.privatenull.pnlibrary.api.updates.ArtifactDescriptor
import ru.privatenull.pnlibrary.api.updates.ProductChange
import ru.privatenull.pnlibrary.api.updates.ProductId
import ru.privatenull.pnlibrary.api.updates.ProductRelease
import ru.privatenull.pnlibrary.api.updates.UpdatePlan
import ru.privatenull.pnlibrary.api.version.ApiVersionRange
import ru.privatenull.pnlibrary.common.jvm.JavaRuntime
import ru.privatenull.pnlibrary.core.updates.engine.ArtifactDownloader
import ru.privatenull.pnlibrary.core.updates.engine.ArtifactSpecification
import ru.privatenull.pnlibrary.core.updates.engine.TransactionArtifact
import ru.privatenull.pnlibrary.core.updates.engine.TrustedHttpClient
import ru.privatenull.pnlibrary.core.updates.engine.UpdateTransaction
import java.io.ByteArrayInputStream
import java.nio.file.Path
import java.util.UUID

internal data class InstalledUpdateTarget(
    val currentJar: Path,
    val updateDirectory: Path,
    val supportedApi: ApiVersionRange? = null,
)

/** Downloads every artifact in an update plan and prepares one restart transaction. */
internal class UpdatePlanStager(
    private val platform: PlatformType,
    private val dataFolder: Path,
    private val http: TrustedHttpClient,
    private val transaction: UpdateTransaction,
    private val maximumArtifactBytes: Long,
    private val requireSha256: Boolean = true,
) {
    fun stage(
        planId: UUID,
        plan: UpdatePlan,
        installedTargets: Map<ProductId, InstalledUpdateTarget>,
        configurationSnapshot: UUID? = null,
    ) {
        val stagingDirectory = dataFolder.resolve("updates/staging/$planId")
        val artifacts = plan.changes.map { change ->
            prepareArtifact(change, plan, stagingDirectory, installedTargets[change.product])
        }
        transaction.prepareForRestart(artifacts, planId, plan.targetApi, configurationSnapshot)
    }

    private fun prepareArtifact(
        change: ProductChange,
        plan: UpdatePlan,
        stagingDirectory: Path,
        installedTarget: InstalledUpdateTarget?,
    ): TransactionArtifact {
        val release = plan.selected.single { it.product == change.product }
        val artifact = selectArtifact(change, release)
        require(!requireSha256 || artifact.sha256 != null) {
            "Release artifact has no published SHA-256: ${artifact.file}"
        }
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
            size = artifact.size,
            sha256 = artifact.sha256,
        )
        val target = installedTarget?.updateDirectory?.resolve(installedTarget.currentJar.fileName)
            ?: dataFolder.parent.resolve("update").resolve(artifact.file)

        return TransactionArtifact(
            specification = specification,
            source = stagedFile,
            target = target,
            rollbackSource = installedTarget?.currentJar ?: target,
            previousVersion = change.from?.toString(),
            previousApi = installedTarget?.supportedApi,
            installedTarget = installedTarget?.currentJar ?: dataFolder.parent.resolve(artifact.file),
        )
    }

    private fun selectArtifact(
        change: ProductChange,
        release: ProductRelease,
    ): ArtifactDescriptor = release.artifacts.firstOrNull { artifact ->
        artifact.platform == platform && artifact.supports(JavaRuntime.feature)
    } ?: error("No ${platform.id} artifact for ${change.product} ${change.to}")

}
