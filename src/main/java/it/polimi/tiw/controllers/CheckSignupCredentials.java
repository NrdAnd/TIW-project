package it.polimi.tiw.controllers;

import it.polimi.tiw.beans.User;
import it.polimi.tiw.dao.FolderDAO;
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
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.SQLException;
import it.polimi.tiw.utils.RequestFields;
import java.util.Map;
import it.polimi.tiw.utils.EmailRules;

@WebServlet("/CheckSignupCredentials")
@MultipartConfig(maxRequestSize = 16384, maxFileSize = 4096)
public class CheckSignupCredentials extends HttpServlet {
	private static final long serialVersionUID = 1L;

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		req.setCharacterEncoding("UTF-8");
		if (!RequestFields.unambiguous(req)) {
			ApiResponse.error(resp, 400, "INVALID_REQUEST", "Duplicate fields or query parameters are not allowed.");
			return;
		}
		String email = req.getParameter("email");
		String password = req.getParameter("password");
		String passwordCheck = req.getParameter("passwordCheck");
		String username = req.getParameter("username");

		if (email == null || password == null || passwordCheck == null || username == null || email.isEmpty()
				|| password.isEmpty() || passwordCheck.isEmpty() || username.isEmpty()) {
			ApiResponse.error(
					resp,
					HttpServletResponse.SC_BAD_REQUEST,
					"MISSING_REGISTRATION_DATA",
					"Complete every field before creating your account.");
			return;
		}

		email = email.trim();
		username = username.trim();
		if (email.isEmpty() || username.isEmpty()) {
			ApiResponse.error(
					resp,
					HttpServletResponse.SC_BAD_REQUEST,
					"MISSING_REGISTRATION_DATA",
					"Complete every field before creating your account.");
			return;
		}
		
		if (email.length()>30 || username.length()>25 || password.length()>25 || passwordCheck.length()>25) {
			ApiResponse.error(
					resp,
					HttpServletResponse.SC_BAD_REQUEST,
					"REGISTRATION_DATA_TOO_LONG",
					"Use at most 25 characters for the username and password, and 30 for the email address.");
			return;
		}

		if (!EmailRules.valid(email)) {
			ApiResponse.error(
					resp,
					HttpServletResponse.SC_BAD_REQUEST,
					"INVALID_EMAIL",
					"Enter a valid email address.");
			return;
		}

		if (!passwordCheck.equals(password)) {
			ApiResponse.error(
					resp,
					HttpServletResponse.SC_BAD_REQUEST,
					"PASSWORD_MISMATCH",
					"The two passwords do not match.");
			return;
		}

		try (Connection connection = ConnectionHandler.getConnection(getServletContext())) {
			connection.setAutoCommit(false);
			try {
				UserDAO userDao = new UserDAO(connection);
				if (!userDao.emailIsDuplicate(email)) {
					connection.rollback();
					ApiResponse.error(
							resp,
							HttpServletResponse.SC_CONFLICT,
							"EMAIL_ALREADY_EXISTS",
							"An account already uses this email address.");
					return;
				}
				if (userDao.checkRegister(username)) {
					connection.rollback();
					ApiResponse.error(
							resp,
							HttpServletResponse.SC_CONFLICT,
							"USERNAME_ALREADY_EXISTS",
							"This username is already in use. Choose another one.");
					return;
				}

				int userId = userDao.registerUser(email, password, username);
				new FolderDAO(connection).createHomePageFolder(userId);
				connection.commit();

				User user = new User();
				user.setUserID(userId);
				user.setUsername(username);
				user.setEmail(email);
				req.getSession().setMaxInactiveInterval(300);
			req.changeSessionId();
				req.getSession().setAttribute("utente", user);
				ApiResponse.ok(resp, Map.of("user", username));
			} catch (SQLException e) {
				try {
					connection.rollback();
				} catch (SQLException rollbackError) {
					getServletContext().log("Registration rollback failed.");
				}
				throw e;
			}
		} catch (UnavailableException e) {
			getServletContext().log("Registration unavailable: database connection could not be opened.");
			ApiResponse.error(
					resp,
					HttpServletResponse.SC_SERVICE_UNAVAILABLE,
					"DATABASE_UNAVAILABLE",
					"The database is unavailable. Check that MySQL is running and that the application credentials are correct.");
		} catch (SQLIntegrityConstraintViolationException e) {
			ApiResponse.error(
					resp,
					HttpServletResponse.SC_CONFLICT,
					"ACCOUNT_ALREADY_EXISTS",
					"The username or email address is already in use.");
		} catch (SQLException e) {
			getServletContext().log("Registration database operation failed (SQL state " + e.getSQLState() + ").");
			ApiResponse.error(
					resp,
					HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
					"REGISTRATION_FAILED",
					"The account could not be created. No partial changes were saved.");
		}
	}
	
	
    @Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		ApiResponse.methodNotAllowed(resp, "POST");
	}
}
