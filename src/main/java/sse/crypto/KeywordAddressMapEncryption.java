package sse.crypto;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import sse.domain.state.EncryptedKeywordAddressMap;
import sse.domain.state.KeywordAddressMap;

public final class KeywordAddressMapEncryption {

    private static final String CIPHER_ALGORITHM = "AES/GCM/NoPadding";
    private static final String KEY_ALGORITHM = "AES";
    private static final String KEY_LABEL = "KeywordMapKey";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;

    private KeywordAddressMapEncryption() {
        // Utility class.
    }

    public static SecretKey deriveKey(SecretKey masterKey) {
        if (masterKey == null) {
            throw new IllegalArgumentException("masterKey cannot be null");
        }
        return new SecretKeySpec(Prf.prf(masterKey, KEY_LABEL), KEY_ALGORITHM);
    }

    public static byte[] generateIv() {
        byte[] iv = new byte[GCM_IV_LENGTH];
        new SecureRandom().nextBytes(iv);
        return iv;
    }

    public static EncryptedKeywordAddressMap encrypt(SecretKey key, byte[] iv, KeywordAddressMap map) {
        if (key == null || iv == null || map == null) {
            throw new IllegalArgumentException("key, iv, and map cannot be null");
        }
        if (iv.length != GCM_IV_LENGTH) {
            throw new IllegalArgumentException("iv must be " + GCM_IV_LENGTH + " bytes");
        }

        try {
            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            return new EncryptedKeywordAddressMap(cipher.doFinal(serialize(map)), iv);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to encrypt keyword address map", e);
        }
    }

    public static KeywordAddressMap decrypt(SecretKey key, EncryptedKeywordAddressMap encryptedMap) {
        if (key == null || encryptedMap == null) {
            throw new IllegalArgumentException("key and encryptedMap cannot be null");
        }

        try {
            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key,
                    new GCMParameterSpec(GCM_TAG_LENGTH, encryptedMap.iv()));
            byte[] plainData = cipher.doFinal(encryptedMap.encryptedData());
            Object obj = deserialize(plainData);
            if (!(obj instanceof KeywordAddressMap)) {
                throw new IllegalArgumentException("Decrypted keyword address map has invalid type");
            }
            return (KeywordAddressMap) obj;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to decrypt keyword address map", e);
        }
    }

    private static byte[] serialize(KeywordAddressMap map) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ObjectOutput out = new ObjectOutputStream(bos)) {
            out.writeObject(map);
            out.flush();
            bos.flush();
            return bos.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to serialize keyword address map", e);
        }
    }

    private static Object deserialize(byte[] plainData) {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(plainData);
             ObjectInput in = new ObjectInputStream(bis)) {
            return in.readObject();
        } catch (IOException | ClassNotFoundException e) {
            throw new IllegalStateException("Failed to deserialize keyword address map", e);
        }
    }
}
