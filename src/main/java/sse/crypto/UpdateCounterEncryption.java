package sse.crypto;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

import sse.domain.EncryptedUpdateCounter;
import sse.domain.KeywordToken;

public final class UpdateCounterEncryption {

    private static final String CIPHER_ALGORITHM = "AES/GCM/NoPadding";
    private static final String KEY_ALGORITHM = "AES";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;
    private static final int AES_KEY_SIZE = 256;

    private UpdateCounterEncryption() {
        // Private constructor to prevent instantiation
    }

    public static SecretKey generateRandomKey() {
        KeyGenerator keyGen;
        try {
            keyGen = KeyGenerator.getInstance(KEY_ALGORITHM);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(KEY_ALGORITHM + " not available in this JVM/provider", e);
        }
        keyGen.init(AES_KEY_SIZE);
        return keyGen.generateKey();
    }

    public static byte[] generateIv() {
        byte[] iv = new byte[GCM_IV_LENGTH];
        new SecureRandom().nextBytes(iv);
        return iv;
    }

    public static EncryptedUpdateCounter encryptUpdateCounter(SecretKey key, byte[] iv,
                                                              Map<KeywordToken, Integer> updateCounter)
            throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException,
            InvalidAlgorithmParameterException, IOException, IllegalBlockSizeException, BadPaddingException {
        if (updateCounter == null) {
            throw new IllegalArgumentException("updateCounter cannot be null");
        }

        Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH, iv));

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ObjectOutput out = new ObjectOutputStream(bos)) {
            out.writeObject(updateCounter);
            out.flush();
        }

        return new EncryptedUpdateCounter(cipher.doFinal(bos.toByteArray()), iv);
    }

    public static Map<KeywordToken, Integer> decryptUpdateCounter(SecretKey key,
                                                                  EncryptedUpdateCounter encryptedUpdateCounter)
            throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidAlgorithmParameterException,
            InvalidKeyException, IllegalBlockSizeException, BadPaddingException, IOException, ClassNotFoundException {
        if (encryptedUpdateCounter == null) {
            throw new IllegalArgumentException("encryptedUpdateCounter cannot be null");
        }

        Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, key,
                new GCMParameterSpec(GCM_TAG_LENGTH, encryptedUpdateCounter.iv()));

        byte[] plainData = cipher.doFinal(encryptedUpdateCounter.encryptedData());
        try (ByteArrayInputStream bis = new ByteArrayInputStream(plainData);
             ObjectInput in = new ObjectInputStream(bis)) {
            Object obj = in.readObject();
            if (!(obj instanceof Map<?, ?>)) {
                throw new IllegalArgumentException("Decrypted update counter is not a map");
            }

            Map<?, ?> rawMap = (Map<?, ?>) obj;
            Map<KeywordToken, Integer> result = new HashMap<>(rawMap.size());
            for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                if (!(entry.getKey() instanceof KeywordToken) || !(entry.getValue() instanceof Integer)) {
                    throw new IllegalArgumentException("Decrypted update counter contains invalid entries");
                }
                result.put((KeywordToken) entry.getKey(), (Integer) entry.getValue());
            }
            return result;
        }
    }
}
