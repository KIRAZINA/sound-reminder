package com.soundreminder.util;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Arrays;
import java.util.Base64;

/**
 * Utility for AES-256-GCM encryption/decryption with PBKDF2 key derivation.
 */
public final class CryptoUtils {

    private static final String CIPHER_ALGORITHM = "AES/GCM/NoPadding";
    private static final String KDF_ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int KEY_LENGTH_BITS = 256;
    private static final int IV_LENGTH_BYTES = 12;
    private static final int SALT_LENGTH_BYTES = 16;
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int PBKDF2_ITERATIONS = 65536;

    private CryptoUtils() {
    }

    public static String encryptToBase64(String plaintext, char[] password) throws Exception {
        byte[] cipher = encrypt(plaintext.getBytes(StandardCharsets.UTF_8), password);
        return Base64.getEncoder().encodeToString(cipher);
    }

    public static String decryptFromBase64(String ciphertextBase64, char[] password) throws Exception {
        byte[] cipher = Base64.getDecoder().decode(ciphertextBase64);
        byte[] plain = decrypt(cipher, password);
        return new String(plain, StandardCharsets.UTF_8);
    }

    public static byte[] encrypt(byte[] plaintext, char[] password) throws Exception {
        SecureRandom random = new SecureRandom();
        byte[] salt = new byte[SALT_LENGTH_BYTES];
        byte[] iv = new byte[IV_LENGTH_BYTES];
        random.nextBytes(salt);
        random.nextBytes(iv);

        SecretKey key = deriveKey(password, salt);
        Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
        byte[] encrypted = cipher.doFinal(plaintext);

        byte[] result = new byte[salt.length + iv.length + encrypted.length];
        System.arraycopy(salt, 0, result, 0, salt.length);
        System.arraycopy(iv, 0, result, salt.length, iv.length);
        System.arraycopy(encrypted, 0, result, salt.length + iv.length, encrypted.length);
        return result;
    }

    public static byte[] decrypt(byte[] ciphertext, char[] password) throws Exception {
        if (ciphertext.length < SALT_LENGTH_BYTES + IV_LENGTH_BYTES) {
            throw new IllegalArgumentException("Ciphertext too short");
        }
        byte[] salt = Arrays.copyOfRange(ciphertext, 0, SALT_LENGTH_BYTES);
        byte[] iv = Arrays.copyOfRange(ciphertext, SALT_LENGTH_BYTES, SALT_LENGTH_BYTES + IV_LENGTH_BYTES);
        byte[] encrypted = Arrays.copyOfRange(ciphertext, SALT_LENGTH_BYTES + IV_LENGTH_BYTES, ciphertext.length);

        SecretKey key = deriveKey(password, salt);
        Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
        return cipher.doFinal(encrypted);
    }

    private static SecretKey deriveKey(char[] password, byte[] salt) throws Exception {
        SecretKeyFactory factory = SecretKeyFactory.getInstance(KDF_ALGORITHM);
        KeySpec spec = new PBEKeySpec(password, salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS);
        SecretKey tmp = factory.generateSecret(spec);
        return new SecretKeySpec(tmp.getEncoded(), "AES");
    }
}
