package com.soundreminder.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

class CryptoUtilsTest {

    @Test
    @DisplayName("AES-GCM round trip")
    void roundTrip() throws Exception {
        String plain = "secret reminder data";
        char[] password = "password123".toCharArray();
        String cipher = CryptoUtils.encryptToBase64(plain, password);
        String decrypted = CryptoUtils.decryptFromBase64(cipher, password);
        assertEquals(plain, decrypted);
    }

    @Test
    @DisplayName("Wrong password fails decryption")
    void wrongPassword() {
        assertThrows(Exception.class, () -> {
            String cipher = CryptoUtils.encryptToBase64("data", "pass".toCharArray());
            CryptoUtils.decryptFromBase64(cipher, "wrong".toCharArray());
        });
    }
}
