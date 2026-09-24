package it.polimi.tiw.utils;

import java.util.regex.Pattern;

public final class EmailRules {
  private static final Pattern EMAIL = Pattern.compile(
      "^[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\\.[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+)*"
      + "@[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?)+$");
  private EmailRules() {}
  public static boolean valid(String email) { return email != null && EMAIL.matcher(email).matches(); }
}
