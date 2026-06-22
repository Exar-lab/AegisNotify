package com.aegisnotify.audit.application.port.out;

public interface EncryptionPort {

  String encrypt(String plainText);

  String decrypt(String cipherText);
}
