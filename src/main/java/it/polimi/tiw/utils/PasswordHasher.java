package it.polimi.tiw.utils;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Versioned, independently salted password hashes; passwords are never trimmed. */
public final class PasswordHasher {
  private static final int ITERATIONS = 600_000;
  private static final SecureRandom RANDOM = new SecureRandom();
  private static final String PREFIX = "pbkdf2-sha256$";
  private PasswordHasher() {}

  public static String hash(String password) {
    byte[] salt = new byte[16];
    RANDOM.nextBytes(salt);
    return PREFIX + ITERATIONS + "$" + Base64.getEncoder().encodeToString(salt)
        + "$" + Base64.getEncoder().encodeToString(derive(password, salt, ITERATIONS));
  }

  public static boolean isLegacy(String stored) {
    // The old schema and both authentication controllers limited passwords to 25 characters.
    // Encoded hashes exceed this cap, so malformed long values never use plaintext comparison.
    return stored != null && !stored.isEmpty() && stored.length() <= 25;
  }

  public static boolean verify(String password, String stored) {
    if (password == null || stored == null) return false;
    if (isLegacy(stored))
      return MessageDigest.isEqual(stored.getBytes(StandardCharsets.UTF_8),
          password.getBytes(StandardCharsets.UTF_8));
    try {
      String[] fields = stored.split("\\$", -1);
      if (fields.length != 4 || !fields[0].equals("pbkdf2-sha256")) return false;
      int iterations = Integer.parseInt(fields[1]);
      if (iterations < ITERATIONS || iterations > 2_000_000) return false;
      byte[] salt = Base64.getDecoder().decode(fields[2]);
      byte[] expected = Base64.getDecoder().decode(fields[3]);
      if (salt.length != 16 || expected.length != 32) return false;
      return MessageDigest.isEqual(expected, derive(password, salt, iterations));
    } catch (IllegalArgumentException e) {
      return false;
    }
  }

  private static byte[] derive(String password, byte[] salt, int iterations) {
    PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, 256);
    try {
      return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Password hashing is unavailable", e);
    } finally {
      spec.clearPassword();
    }
  }
}
