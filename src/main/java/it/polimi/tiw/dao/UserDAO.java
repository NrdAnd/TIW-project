package it.polimi.tiw.dao;

import it.polimi.tiw.beans.User;
import it.polimi.tiw.utils.PasswordHasher;
import java.sql.*;

public class UserDAO {
  private final Connection connection;
  public UserDAO(Connection connection) { this.connection = connection; }

  public User checkLogin(String username, String password) throws SQLException {
    try (PreparedStatement query = connection.prepareStatement(
        "SELECT user_id, username, email, password FROM User WHERE username=?")) {
      query.setString(1, username);
      try (ResultSet rows = query.executeQuery()) {
        if (!rows.next()) return null;
        String stored = rows.getString("password");
        if (!PasswordHasher.verify(password, stored)) return null;
        User user = user(rows);
        if (PasswordHasher.isLegacy(stored)) {
          try (PreparedStatement update = connection.prepareStatement(
              "UPDATE User SET password=? WHERE user_id=? AND BINARY password=BINARY ?")) {
            update.setString(1, PasswordHasher.hash(password));
            update.setInt(2, user.getUserID());
            update.setString(3, stored);
            update.executeUpdate();
          }
        }
        return user;
      }
    }
  }

  public int registerUser(String email, String password, String username) throws SQLException {
    try (PreparedStatement query = connection.prepareStatement(
        "INSERT INTO User(username,email,password) VALUES(?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
      query.setString(1, username);
      query.setString(2, email);
      query.setString(3, PasswordHasher.hash(password));
      if (query.executeUpdate() != 1) throw new SQLException("Registration failed");
      try (ResultSet keys = query.getGeneratedKeys()) {
        if (!keys.next()) throw new SQLException("Missing generated user ID");
        return keys.getInt(1);
      }
    }
  }

  public boolean checkRegister(String username) throws SQLException {
    return getUtenteByUsername(username) != null;
  }

  /** Historical method name: true means the email is available. */
  public boolean emailIsDuplicate(String email) throws SQLException {
    return getUtenteByEmail(email) == null;
  }

  public User getUtenteById(int id) throws SQLException { return find("user_id", id); }
  public User getUtenteByEmail(String email) throws SQLException { return find("email", email); }
  public User getUtenteByUsername(String username) throws SQLException { return find("username", username); }

  private User find(String column, Object value) throws SQLException {
    // column comes only from the three constant call sites above.
    try (PreparedStatement query = connection.prepareStatement(
        "SELECT user_id,username,email FROM User WHERE " + column + "=?")) {
      query.setObject(1, value);
      try (ResultSet rows = query.executeQuery()) { return rows.next() ? user(rows) : null; }
    }
  }

  private User user(ResultSet rows) throws SQLException {
    User user = new User();
    user.setUserID(rows.getInt("user_id"));
    user.setUsername(rows.getString("username"));
    user.setEmail(rows.getString("email"));
    return user;
  }
}
