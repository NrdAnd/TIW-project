package it.polimi.tiw.controllers;

import it.polimi.tiw.beans.User;
import it.polimi.tiw.dao.UserDAO;
import it.polimi.tiw.utils.ApiResponse;
import it.polimi.tiw.utils.ConnectionHandler;

import javax.servlet.ServletException;
import javax.servlet.UnavailableException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import it.polimi.tiw.utils.RequestFields;

@WebServlet("/CheckLoginCredentials")
@MultipartConfig(maxRequestSize = 16384, maxFileSize = 4096)
public class CheckLoginCredentials extends HttpServlet {
	private static final long serialVersionUID = 1L;

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		req.setCharacterEncoding("UTF-8");
		if (!RequestFields.unambiguous(req)) {
			ApiResponse.error(resp, 400, "INVALID_REQUEST", "Duplicate fields or query parameters are not allowed.");
			return;
		}
		String username = req.getParameter("username");
		String password = req.getParameter("password");

		if (username == null || password == null || username.isEmpty() || password.isEmpty()) {
			ApiResponse.error(
					resp,
					HttpServletResponse.SC_BAD_REQUEST,
					"MISSING_CREDENTIALS",
					"Enter both your username and password.");
			return;
		}
		username = username.trim();
		if (username.isEmpty()) {
			ApiResponse.error(
					resp,
					HttpServletResponse.SC_BAD_REQUEST,
					"MISSING_CREDENTIALS",
					"Enter both your username and password.");
			return;
		}
		if (username.length() > 25 || password.length() > 25) {
			ApiResponse.error(
					resp,
					HttpServletResponse.SC_BAD_REQUEST,
					"INVALID_CREDENTIALS",
					"The username or password is longer than the allowed limit.");
			return;
		}

		try (Connection connection = ConnectionHandler.getConnection(getServletContext())) {
			User user = new UserDAO(connection).checkLogin(username, password);
			if (user == null) {
				ApiResponse.error(
						resp,
						HttpServletResponse.SC_UNAUTHORIZED,
						"INVALID_CREDENTIALS",
						"The username or password is incorrect.");
				return;
			}

			req.getSession().setMaxInactiveInterval(300);
			req.changeSessionId();
			req.getSession().setAttribute("utente", user);
			ApiResponse.ok(resp, java.util.Map.of("user", user.getUsername()));
		} catch (UnavailableException e) {
			getServletContext().log("Login unavailable: database connection could not be opened.");
			ApiResponse.error(
					resp,
					HttpServletResponse.SC_SERVICE_UNAVAILABLE,
					"DATABASE_UNAVAILABLE",
					"The database is unavailable. Check that MySQL is running and that the application credentials are correct.");
		} catch (SQLException e) {
			getServletContext().log("Login database query failed (SQL state " + e.getSQLState() + ").");
			ApiResponse.error(
					resp,
					HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
					"LOGIN_FAILED",
					"The sign-in request could not be completed. Please try again.");
		}
	}
	
    @Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		ApiResponse.methodNotAllowed(resp, "POST");
	}
}
