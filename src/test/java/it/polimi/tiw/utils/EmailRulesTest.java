package it.polimi.tiw.utils;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
class EmailRulesTest {
  @Test void rejectsAmbiguousAndWhitespaceAddresses() {
    for (String email : new String[]{"a b@c.d", "a@@c.d", ".a@c.d", "a..b@c.d", "a@-c.d", "a@c", "a\n@c.d", "a(b)@c.d"})
      assertFalse(EmailRules.valid(email), email);
  }
  @Test void acceptsCommonAddresses() {
    for (String email : new String[]{"a@test.invalid", "a.b+tag@example.com", "a@sub-domain.example.org"})
      assertTrue(EmailRules.valid(email), email);
  }
}
