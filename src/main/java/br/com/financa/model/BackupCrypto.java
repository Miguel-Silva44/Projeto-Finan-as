package br.com.financa.model;

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.*;
import javax.crypto.spec.*;

public final class BackupCrypto {
  private static final byte[] MAGIC = {'F', 'I', 'N', '1'};

  private BackupCrypto() {}

  private static javax.crypto.SecretKey key(char[] password, byte[] salt) throws Exception {
    FinancialRules.require(
        password.length >= 16, "A senha de backup precisa ter ao menos 16 caracteres.");
    var spec = new PBEKeySpec(password, salt, 600000, 256);
    try {
      return new SecretKeySpec(
          SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded(),
          "AES");
    } finally {
      spec.clearPassword();
    }
  }

  public static byte[] encrypt(byte[] content, char[] password) throws Exception {
    byte[] salt = new byte[16], nonce = new byte[12];
    var random = new SecureRandom();
    random.nextBytes(salt);
    random.nextBytes(nonce);
    var cipher = Cipher.getInstance("AES/GCM/NoPadding");
    cipher.init(Cipher.ENCRYPT_MODE, key(password, salt), new GCMParameterSpec(128, nonce));
    cipher.updateAAD(MAGIC);
    byte[] encrypted = cipher.doFinal(content);
    return ByteBuffer.allocate(32 + encrypted.length)
        .put(MAGIC)
        .put(salt)
        .put(nonce)
        .put(encrypted)
        .array();
  }

  public static byte[] decrypt(byte[] content, char[] password) throws Exception {
    if (content.length < 48 || !Arrays.equals(Arrays.copyOf(content, 4), MAGIC))
      throw new IllegalArgumentException("Formato de backup inválido.");
    var cipher = Cipher.getInstance("AES/GCM/NoPadding");
    cipher.init(
        Cipher.DECRYPT_MODE,
        key(password, Arrays.copyOfRange(content, 4, 20)),
        new GCMParameterSpec(128, Arrays.copyOfRange(content, 20, 32)));
    cipher.updateAAD(MAGIC);
    return cipher.doFinal(Arrays.copyOfRange(content, 32, content.length));
  }
}
