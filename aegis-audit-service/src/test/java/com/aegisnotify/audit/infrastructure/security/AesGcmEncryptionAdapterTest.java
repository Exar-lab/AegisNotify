package com.aegisnotify.audit.infrastructure.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Base64;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AesGcmEncryptionAdapterTest {

  private AesGcmEncryptionAdapter adapter;
  private String base64Key;

  @BeforeEach
  void setUp() throws Exception {
    KeyGenerator keyGen = KeyGenerator.getInstance("AES");
    keyGen.init(256);
    SecretKey key = keyGen.generateKey();
    base64Key = Base64.getEncoder().encodeToString(key.getEncoded());
    adapter = new AesGcmEncryptionAdapter(base64Key);
  }

  @Test
  void encrypt_andDecrypt_roundTrip_returnsOriginalPlaintext() {
    String plainText = "user@example.com";

    String encrypted = adapter.encrypt(plainText);
    String decrypted = adapter.decrypt(encrypted);

    assertEquals(plainText, decrypted);
  }

  @Test
  void encrypt_roundTrip_withPhoneNumber() {
    String plainText = "+34600000000";

    String encrypted = adapter.encrypt(plainText);
    String decrypted = adapter.decrypt(encrypted);

    assertEquals(plainText, decrypted);
  }

  @Test
  void encrypt_producesBase64Output() {
    String encrypted = adapter.encrypt("test@example.com");

    assertNotNull(encrypted);
    assertTrue(encrypted.length() > 0);
    // Should be valid Base64
    byte[] decoded = Base64.getDecoder().decode(encrypted);
    // 12-byte IV + at minimum some ciphertext + 16-byte GCM tag
    assertTrue(decoded.length >= 12 + 16,
        "Ciphertext should contain at least IV (12) + GCM tag (16) bytes");
  }

  @Test
  void encrypt_producesDifferentCiphertextEachCall() {
    String plainText = "same-input@example.com";

    String encrypted1 = adapter.encrypt(plainText);
    String encrypted2 = adapter.encrypt(plainText);

    assertNotEquals(encrypted1, encrypted2,
        "Each encryption should use a random IV producing different ciphertext");
  }

  @Test
  void decrypt_withWrongKey_throwsException() throws Exception {
    String encrypted = adapter.encrypt("secret-data");

    // Create a different key
    KeyGenerator keyGen = KeyGenerator.getInstance("AES");
    keyGen.init(256);
    SecretKey wrongKey = keyGen.generateKey();
    String wrongBase64Key = Base64.getEncoder()
        .encodeToString(wrongKey.getEncoded());
    AesGcmEncryptionAdapter wrongAdapter =
        new AesGcmEncryptionAdapter(wrongBase64Key);

    assertThrows(RuntimeException.class,
        () -> wrongAdapter.decrypt(encrypted));
  }

  @Test
  void encrypt_emptyString_roundTrips() {
    String plainText = "";

    String encrypted = adapter.encrypt(plainText);
    String decrypted = adapter.decrypt(encrypted);

    assertEquals(plainText, decrypted);
  }
}
