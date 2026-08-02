package sse.crypto;

import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAKeyGenParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;

import sse.domain.IndexAddress;
import sse.domain.SearchTokenValue;

public final class TrapdoorPermutation {

    private static final String KEY_ALGORITHM = "RSA";
    private static final int RSA_KEY_SIZE_BITS = 2048;
    private static final RSAKeyGenParameterSpec RSA_KEY_GEN_PARAMS =
            new RSAKeyGenParameterSpec(RSA_KEY_SIZE_BITS, RSAKeyGenParameterSpec.F0);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final BigInteger ZERO = BigInteger.ZERO;
    private static final BigInteger ONE = BigInteger.ONE;

    private TrapdoorPermutation() {
        // Utility class, prevent instantiation
    }

    public static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance(KEY_ALGORITHM);
            generator.initialize(RSA_KEY_GEN_PARAMS, SECURE_RANDOM);
            return generator.generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("RSA key pair generation failed", e);
        }
    }

    public static byte[] encodePublicKey(RSAPublicKey publicKey) {
        if (publicKey == null) {
            throw new IllegalArgumentException("publicKey cannot be null");
        }
        return publicKey.getEncoded();
    }

    public static byte[] encodePrivateKey(RSAPrivateKey privateKey) {
        if (privateKey == null) {
            throw new IllegalArgumentException("privateKey cannot be null");
        }
        return privateKey.getEncoded();
    }

    public static RSAPublicKey decodePublicKey(byte[] encoded) {
        if (encoded == null) {
            throw new IllegalArgumentException("encoded public key cannot be null");
        }
        try {
            KeyFactory keyFactory = KeyFactory.getInstance(KEY_ALGORITHM);
            return (RSAPublicKey) keyFactory.generatePublic(new X509EncodedKeySpec(encoded));
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("Invalid RSA public key encoding", e);
        }
    }

    public static RSAPrivateKey decodePrivateKey(byte[] encoded) {
        if (encoded == null) {
            throw new IllegalArgumentException("encoded private key cannot be null");
        }
        try {
            KeyFactory keyFactory = KeyFactory.getInstance(KEY_ALGORITHM);
            return (RSAPrivateKey) keyFactory.generatePrivate(new PKCS8EncodedKeySpec(encoded));
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("Invalid RSA private key encoding", e);
        }
    }

    public static SearchTokenValue generateRandomToken(RSAPublicKey publicKey) {
        if (publicKey == null) {
            throw new IllegalArgumentException("publicKey cannot be null");
        }

        BigInteger modulus = publicKey.getModulus();
        int tokenLength = modulusLengthBytes(modulus);

        while (true) {
            BigInteger candidate = new BigInteger(modulus.bitLength(), SECURE_RANDOM);
            if (isValidRsaDomainElement(candidate, modulus)) {
                return new SearchTokenValue(i2osp(candidate, tokenLength));
            }
        }
    }

    public static SearchTokenValue privateStep(SearchTokenValue token, RSAPrivateKey privateKey) {
        if (privateKey == null) {
            throw new IllegalArgumentException("privateKey cannot be null");
        }

        BigInteger modulus = privateKey.getModulus();
        int tokenLength = modulusLengthBytes(modulus);
        BigInteger tokenValue = validatedTokenInteger(token, modulus, tokenLength);
        BigInteger nextValue = tokenValue.modPow(privateKey.getPrivateExponent(), modulus);
        return new SearchTokenValue(i2osp(nextValue, tokenLength));
    }

    public static SearchTokenValue publicStep(SearchTokenValue token, RSAPublicKey publicKey) {
        if (publicKey == null) {
            throw new IllegalArgumentException("publicKey cannot be null");
        }

        BigInteger modulus = publicKey.getModulus();
        int tokenLength = modulusLengthBytes(modulus);
        BigInteger tokenValue = validatedTokenInteger(token, modulus, tokenLength);
        BigInteger previousValue = tokenValue.modPow(publicKey.getPublicExponent(), modulus);
        return new SearchTokenValue(i2osp(previousValue, tokenLength));
    }

    public static IndexAddress deriveAddress(byte[] keywordAddressKey, SearchTokenValue token) {
        if (keywordAddressKey == null) {
            throw new IllegalArgumentException("keywordAddressKey cannot be null");
        }
        if (token == null) {
            throw new IllegalArgumentException("token cannot be null");
        }

        return new IndexAddress(Prf.prf(keywordAddressKey, token.value()));
    }

    private static BigInteger validatedTokenInteger(SearchTokenValue token, BigInteger modulus, int tokenLength) {
        if (token == null) {
            throw new IllegalArgumentException("token cannot be null");
        }
        byte[] tokenBytes = token.value();
        if (tokenBytes.length != tokenLength) {
            throw new IllegalArgumentException("Token length " + tokenBytes.length
                    + " does not match RSA modulus length " + tokenLength);
        }

        BigInteger tokenValue = os2ip(tokenBytes);
        if (!isValidRsaDomainElement(tokenValue, modulus)) {
            throw new IllegalArgumentException("Token is not a valid RSA-domain element");
        }
        return tokenValue;
    }

    private static boolean isValidRsaDomainElement(BigInteger value, BigInteger modulus) {
        return value.compareTo(ZERO) > 0
                && value.compareTo(modulus) < 0
                && value.gcd(modulus).equals(ONE);
    }

    private static int modulusLengthBytes(BigInteger modulus) {
        return (modulus.bitLength() + 7) / 8;
    }

    private static BigInteger os2ip(byte[] bytes) {
        return new BigInteger(1, bytes);
    }

    private static byte[] i2osp(BigInteger value, int length) {
        if (value.compareTo(ZERO) < 0) {
            throw new IllegalArgumentException("value cannot be negative");
        }
        BigInteger maxValue = ONE.shiftLeft(8 * length);
        if (value.compareTo(maxValue) >= 0) {
            throw new IllegalArgumentException("value is too large for " + length + " bytes");
        }

        byte[] encoded = value.toByteArray();
        if (encoded.length == length) {
            return encoded;
        }
        if (encoded.length == length + 1 && encoded[0] == 0) {
            return Arrays.copyOfRange(encoded, 1, encoded.length);
        }
        if (encoded.length > length) {
            throw new IllegalArgumentException("value encoding is longer than " + length + " bytes");
        }

        byte[] fixedLength = new byte[length];
        System.arraycopy(encoded, 0, fixedLength, length - encoded.length, encoded.length);
        return fixedLength;
    }
}
