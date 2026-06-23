package com.aegisnotify.audit.infrastructure.security;

import com.aegisnotify.audit.application.port.out.EncryptionPort;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * AES-256-GCM encryption adapter implementing the EncryptionPort.
 *
 * <p>Uses a random 12-byte IV prepended to the ciphertext. The combined
 * IV + ciphertext is Base64-encoded for storage. The encryption key is
 * read from the {@code audit.encryption.key} property as a Base64 string.</p>
 */
@Component
public class AesGcmEncryptionAdapter implements EncryptionPort {

  private static final String ALGORITHM = "AES/GCM/NoPadding";
  private static final int GCM_IV_LENGTH = 12;
  private static final int GCM_TAG_LENGTH = 128;

  private final SecretKeySpec secretKey;
  private final SecureRandom secureRandom;

  public AesGcmEncryptionAdapter(
      @Value("${audit.encryption.key}") String base64Key) {
    byte[] keyBytes = Base64.getDecoder().decode(base64Key);
    this.secretKey = new SecretKeySpec(keyBytes, "AES");
    this.secureRandom = new SecureRandom();
  }

  @Override
  public String encrypt(String plainText) {
    try {
      byte[] iv = new byte[GCM_IV_LENGTH];
      secureRandom.nextBytes(iv);

      Cipher cipher = Cipher.getInstance(ALGORITHM);
      GCMParameterSpec parameterSpec =
          new GCMParameterSpec(GCM_TAG_LENGTH, iv);
      cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);

      byte[] cipherText =
          cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

      byte[] combined = new byte[iv.length + cipherText.length];
      System.arraycopy(iv, 0, combined, 0, iv.length);
      System.arraycopy(cipherText, 0, combined, iv.length,
          cipherText.length);

      return Base64.getEncoder().encodeToString(combined);
    } catch (GeneralSecurityException ex) {
      throw new EncryptionException("Failed to encrypt data", ex);
    }
  }

  @Override
  public String decrypt(String cipherTextBase64) {
    try {
      byte[] combined = Base64.getDecoder().decode(cipherTextBase64);

      byte[] iv = new byte[GCM_IV_LENGTH];
      System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH);

      byte[] cipherText = new byte[combined.length - GCM_IV_LENGTH];
      System.arraycopy(combined, GCM_IV_LENGTH, cipherText, 0,
          cipherText.length);

      Cipher cipher = Cipher.getInstance(ALGORITHM);
      GCMParameterSpec parameterSpec =
          new GCMParameterSpec(GCM_TAG_LENGTH, iv);
      cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);

      byte[] plainText = cipher.doFinal(cipherText);
      return new String(plainText, StandardCharsets.UTF_8);
    } catch (GeneralSecurityException ex) {
      throw new EncryptionException("Failed to decrypt data", ex);
    }
  }

  /**
   * Runtime exception for encryption/decryption failures.
   */
  public static class EncryptionException extends RuntimeException {

    public EncryptionException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
