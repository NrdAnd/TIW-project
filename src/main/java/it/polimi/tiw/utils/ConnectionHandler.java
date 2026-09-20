package it.polimi.tiw.utils;

import javax.servlet.ServletContext;
import javax.servlet.UnavailableException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class ConnectionHandler {
	public static Connection getConnection(ServletContext context) throws UnavailableException {
		Connection connection;
		try {
			String driver = configuration(context, "dbDriver", "TIW_DB_DRIVER");
			String url = configuration(context, "dbUrl", "TIW_DB_URL");
			String user = configuration(context, "dbUser", "TIW_DB_USER");
			String password = configuration(context, "dbPassword", "TIW_DB_PASSWORD");
			if (driver == null || driver.isBlank() || url == null || url.isBlank()
                    || user == null || user.isBlank() || password == null || password.isEmpty()) {
                throw new UnavailableException("Database configuration is missing; configure TIW_DB_* or Tomcat context parameters");
            }
            Class.forName(driver);
			connection = DriverManager.getConnection(url, user, password);
		} catch (ClassNotFoundException e) {
			throw new UnavailableException("Can't load database driver");
		} catch (SQLException e) {
			throw new UnavailableException("Couldn't get db connection");
		}
		return connection;
	}

    /** Environment overrides keep credentials out of the repository and the WAR. */
    static String configuration(ServletContext context, String parameter, String environment) {
        String value = System.getenv(environment);
        return value != null ? value : context.getInitParameter(parameter);
    }

	public static void closeConnection(Connection connection) throws SQLException {
		if (connection != null) {
			connection.close();
		}
	}
}
