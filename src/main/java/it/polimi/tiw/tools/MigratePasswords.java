package it.polimi.tiw.tools;

import it.polimi.tiw.utils.PasswordHasher;
import java.sql.*;

/** Offline CLI: apply migration 003 first; uses TIW_DB_URL/USER/PASSWORD, never prints credentials. */
public final class MigratePasswords {
  public static void main(String[] args) throws Exception {
    if (args.length != 1 || !args[0].equals("--migrate"))
      throw new IllegalArgumentException("Back up the database and apply migration 003, then pass --migrate");
    int migrated = 0;
    try (Connection c = DriverManager.getConnection(required("TIW_DB_URL"),
        required("TIW_DB_USER"), required("TIW_DB_PASSWORD"));
        PreparedStatement query = c.prepareStatement("SELECT user_id,password FROM User WHERE CHAR_LENGTH(password)<=25");
        ResultSet rows = query.executeQuery();
        PreparedStatement update = c.prepareStatement(
            "UPDATE User SET password=? WHERE user_id=? AND BINARY password=BINARY ?")) {
      while (rows.next()) {
        String stored = rows.getString(2);
        if (!PasswordHasher.isLegacy(stored)) continue;
        update.setString(1, PasswordHasher.hash(stored));
        update.setInt(2, rows.getInt(1));
        update.setString(3, stored);
        migrated += update.executeUpdate();
      }
    }
    System.out.println("Migrated password records: " + migrated);
  }
  private static String required(String key) {
    String value = System.getenv(key);
    if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing " + key);
    return value;
  }
}
