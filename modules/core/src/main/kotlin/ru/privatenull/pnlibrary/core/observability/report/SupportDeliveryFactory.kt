package ru.privatenull.pnlibrary.core.observability.report

import ru.privatenull.pnlibrary.api.runtime.PnLibraryConfig
import ru.privatenull.pnlibrary.core.security.EncryptedEnvelopeCodec
import ru.privatenull.pnlibrary.core.upload.CatboxUploader
import ru.privatenull.pnlibrary.core.upload.EncryptedReportUploader
import ru.privatenull.pnlibrary.core.upload.FileIoUploader
import ru.privatenull.pnlibrary.core.upload.UploadProvider
import ru.privatenull.pnlibrary.core.upload.UploadProviderChain
import java.net.URI
import java.nio.charset.StandardCharsets

/** Creates encryption and remote-delivery components from the support policy. */
internal class SupportDeliveryFactory(
    private val config: PnLibraryConfig,
) {
    fun encryptionCodec(): EncryptedEnvelopeCodec? {
        if (!config.uploadMode.startsWith("encrypted")) return null

        return try {
            EncryptedEnvelopeCodec(publicKey(), config.uploadKeyId)
        } catch (error: Exception) {
            throw IllegalStateException("Unable to initialize diagnostic encryption", error)
        }
    }

    fun uploader(): UploadProvider? {
        if (!config.upload || config.uploadMode == "disabled") return null

        val providers = config.uploadProviders.map(::createProvider)
        return UploadProviderChain(providers)
    }

    private fun createProvider(id: String): UploadProvider = when (id) {
        "catbox" -> CatboxUploader()
        "fileio" -> FileIoUploader()
        "custom" -> EncryptedReportUploader(
            endpoint = URI.create(config.uploadEndpoint),
            publicBase = URI.create(config.uploadPublicBase),
        )
        else -> error("Unsupported upload provider: $id")
    }

    private fun publicKey(): String {
        if (config.uploadPublicKey.isNotBlank()) return config.uploadPublicKey

        val resource = SupportDeliveryFactory::class.java.getResourceAsStream("/diagnostic-public.pem")
            ?: throw IllegalArgumentException("Bundled diagnostic public key missing")
        return resource.bufferedReader(StandardCharsets.US_ASCII).use { reader -> reader.readText() }
    }
}
