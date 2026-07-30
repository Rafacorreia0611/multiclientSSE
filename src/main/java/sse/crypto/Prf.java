package sse.crypto;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

public final class Prf {

    public static final String ALGORITHM = "HmacSHA256";

    private Prf() {
        // Utility class, prevent instantiation
    }

    public static byte[] prf(SecretKey key, byte[] input) {
        try {
            Mac hmac = Mac.getInstance(ALGORITHM);
            hmac.init(key);
            return hmac.doFinal(input);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 PRF failed", e);
        }
    }

    public static byte[] prf(SecretKey key, String input) {
        return prf(key, input.getBytes(StandardCharsets.UTF_8));
    }

    public static byte[] prf(SecretKey key, int input) {
        return prf(key, ByteBuffer.allocate(Integer.BYTES).putInt(input).array());
    }
    
    public static byte[] prf(byte[] keyBytes, byte[] input) {
        return prf(new SecretKeySpec(keyBytes, ALGORITHM), input);
    }

    public static byte[] prf(byte[] keyBytes, String input) {
        return prf(keyBytes, input.getBytes(StandardCharsets.UTF_8));
    }

    public static byte[] prf(byte[] keyBytes, int input) {
        return prf(keyBytes, ByteBuffer.allocate(Integer.BYTES).putInt(input).array());
    }

}
