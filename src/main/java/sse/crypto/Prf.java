package sse.crypto;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

public class Prf {

    private Prf() {
        // Utility class, prevent instantiation
    }

    public static byte[] prf(SecretKey key, byte[] input) {
        try {
            Mac hmac = Mac.getInstance("HmacSHA256");
            hmac.init(key);
            return hmac.doFinal(input);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 PRF failed", e);
        }
    }

    public static byte[] prf(SecretKey key, String input) {
        return prf(key, input.getBytes(StandardCharsets.UTF_8));
    }
    
    public static byte[] prf(byte[] keyBytes, byte[] input) {
        return prf(new SecretKeySpec(keyBytes, "HmacSHA256"), input);
    }

    public static byte[] prf(byte[] keyBytes, String input) {
        return prf(keyBytes, input.getBytes(StandardCharsets.UTF_8));
    }

}
