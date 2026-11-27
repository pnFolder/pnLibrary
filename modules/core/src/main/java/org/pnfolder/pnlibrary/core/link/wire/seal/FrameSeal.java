package org.pnfolder.pnlibrary.core.link.wire.seal;

import org.pnfolder.pnlibrary.core.link.wire.frame.FrameReader;
import org.pnfolder.pnlibrary.core.link.wire.frame.FrameWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.Arrays;
import org.pnfolder.pnlibrary.common.lang.Failures;

/** Message format: body + HMAC-SHA256(body, sharedKey). A wrong signature or an unreadable body is dropped. */
public final class FrameSeal {
    private static final int MAC_BYTES = 32;

    private FrameSeal() {
    }

    public static byte[] signed(FrameSigner signer, byte[] key, FrameWriter write) {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(body)) {
            write.write(out);
        } catch (IOException impossible) {
            throw new IllegalStateException(impossible);
        }
        byte[] bytes = body.toByteArray();
        byte[] mac = signer.sign(bytes, key);
        byte[] result = Arrays.copyOf(bytes, bytes.length + mac.length);
        System.arraycopy(mac, 0, result, bytes.length, mac.length);
        return result;
    }

    public static <T> T verified(byte[] message, byte[] key, FrameSigner signer, FrameReader<T> read) {
        if (message.length <= MAC_BYTES) {
            return null;
        }
        byte[] body = Arrays.copyOfRange(message, 0, message.length - MAC_BYTES);
        byte[] mac = Arrays.copyOfRange(message, message.length - MAC_BYTES, message.length);
        if (!MessageDigest.isEqual(mac, signer.sign(body, key))) {
            return null;
        }
        return Failures.runCatchingNonFatal(() -> {
            try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(body))) {
                return read.read(in);
            }
        }).getOrNull();
    }
}
