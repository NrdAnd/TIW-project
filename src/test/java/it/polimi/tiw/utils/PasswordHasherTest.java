package it.polimi.tiw.utils;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class PasswordHasherTest {
  @Test void saltsAndVerifiesExactPasswords() {
    String value = "CaféPassword_7 ";
    String hash = PasswordHasher.hash(value);
    assertNotEquals(value, hash);
    assertNotEquals(hash, PasswordHasher.hash(value));
    assertTrue(PasswordHasher.verify(value, hash));
    assertFalse(PasswordHasher.verify(value.strip(), hash));
    assertFalse(PasswordHasher.verify(value.toLowerCase(), hash));
    assertFalse(PasswordHasher.verify("wrong", hash));
  }
  @Test void legacyPasswordsAreComparedExactlyBeforeUpgrade() {
    assertTrue(PasswordHasher.verify("Legacy_7", "Legacy_7"));
    assertFalse(PasswordHasher.verify("Legacy_7 ", "Legacy_7"));
    assertFalse(PasswordHasher.verify("legacy_7", "Legacy_7"));
    assertFalse(PasswordHasher.verify(null, "Legacy_7"));
    assertFalse(PasswordHasher.verify("", ""));
  }
  @Test void invalidHashEncodingsAndWorkFactorsAreRejected() {
    for (String stored : new String[] {
        "pbkdf2-sha256$600000$not-base64!!!$invalid-digest",
        "pbkdf2-sha256$-1$AAAAAAAAAAAAAAAAAAAAAA==$AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "pbkdf2-sha256$2147483647$AAAAAAAAAAAAAAAAAAAAAA==$AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "some-unknown-encoding-longer-than-the-old-column"})
      assertFalse(PasswordHasher.verify(stored, stored));
  }
}
