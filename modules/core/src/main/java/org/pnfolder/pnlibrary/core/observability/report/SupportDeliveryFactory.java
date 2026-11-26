package org.pnfolder.pnlibrary.core.observability.report;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.runtime.PnLibraryConfig;
import org.pnfolder.pnlibrary.core.upload.CatboxUploader;
import org.pnfolder.pnlibrary.core.upload.EncryptedReportUploader;
import org.pnfolder.pnlibrary.core.upload.FileIoUploader;
import org.pnfolder.pnlibrary.core.upload.UploadProvider;
import org.pnfolder.pnlibrary.core.upload.UploadProviderChain;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Creates encryption and remote-delivery components from the support policy. */
public final class SupportDeliveryFactory {
    private final PnLibraryConfig config;

    public SupportDeliveryFactory(@NotNull PnLibraryConfig config) {
        this.config = config;
    }

    /** The codec for encrypted modes, or {@code null} when reports are not encrypted. */
    @Nullable
    public EncryptedEnvelopeCodec encryptionCodec() {
        if (!config.getUploadMode().startsWith("encrypted")) {
            return null;
        }
        try {
            return new EncryptedEnvelopeCodec(publicKey(), config.getUploadKeyId());
        } catch (Exception failure) {
            throw new IllegalStateException("Unable to initialize diagnostic encryption", failure);
        }
    }

    /** The configured upload destination, or {@code null} when uploading is off. */
    @Nullable
    public UploadProvider uploader() {
        if (!config.isUpload() || "disabled".equals(config.getUploadMode())) {
            return null;
        }
        List<UploadProvider> providers = new ArrayList<>();
        for (String id : config.getUploadProviders()) {
            providers.add(createProvider(id));
        }
        return new UploadProviderChain(providers);
    }

    private UploadProvider createProvider(String id) {
        switch (id) {
            case "catbox":
                return new CatboxUploader();
            case "fileio":
                return new FileIoUploader();
            case "custom":
                return new EncryptedReportUploader(URI.create(config.getUploadEndpoint()),
                        URI.create(config.getUploadPublicBase()));
            default:
                throw new IllegalStateException("Unsupported upload provider: " + id);
        }
    }

    private String publicKey() throws IOException {
        if (!config.getUploadPublicKey().trim().isEmpty()) {
            return config.getUploadPublicKey();
        }
        try (InputStream resource = SupportDeliveryFactory.class.getResourceAsStream("/diagnostic-public.pem")) {
            if (resource == null) {
                throw new IllegalArgumentException("Bundled diagnostic public key missing");
            }
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
            for (int read = resource.read(chunk); read >= 0; read = resource.read(chunk)) {
                buffer.write(chunk, 0, read);
            }
            return new String(buffer.toByteArray(), StandardCharsets.US_ASCII);
        }
    }
}
