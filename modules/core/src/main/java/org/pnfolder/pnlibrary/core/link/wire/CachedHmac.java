package org.pnfolder.pnlibrary.core.link.wire;

import java.util.Objects;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.function.Supplier;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.jetbrains.annotations.NotNull;

/** HMAC-SHA256 with one {@link Mac} per thread per key: Mac init is the expensive part, doFinal() resets it for reuse. */
public final class CachedHmac implements FrameSigner {
    private static final String ALGORITHM = "HmacSHA256";

    private static final class Keyed {
        final byte[] key;
        final Mac mac;

        Keyed(byte[] key, Mac mac) {
            this.key = key;
            this.mac = mac;
        }
    }

    private final Supplier<Mac> create;
    private final ThreadLocal<Keyed> perThread = new ThreadLocal<>();

    public CachedHmac() {
        this(CachedHmac::newMac);
    }

    public CachedHmac(@NotNull Supplier<Mac> create) {
        this.create = Objects.requireNonNull(create, "create");
    }

    @Override
    public @NotNull byte[] sign(@NotNull byte[] bytes, @NotNull byte[] key) {
        Keyed cached = perThread.get();
        if (cached == null || !MessageDigest.isEqual(cached.key, key)) {
            Mac mac = create.get();
            try {
                mac.init(new SecretKeySpec(key, ALGORITHM));
            } catch (java.security.InvalidKeyException invalid) {
                throw new IllegalArgumentException(invalid);
            }
            cached = new Keyed(key.clone(), mac);
            perThread.set(cached);
        }
        return cached.mac.doFinal(bytes);
    }

    private static Mac newMac() {
        try {
            return Mac.getInstance(ALGORITHM);
        } catch (NoSuchAlgorithmException missing) {
            throw new IllegalStateException(missing);
        }
    }
}
