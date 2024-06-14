package it.polimi.tiw.controllers;

import it.polimi.tiw.beans.User;
import it.polimi.tiw.dao.FolderDAO;
import it.polimi.tiw.dao.UserDAO;
import it.polimi.tiw.utils.ConnectionHandler;

import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.regex.Pattern;

@WebServlet("/CheckSignupCredentials")
@MultipartConfig
public class CheckSignupCredentials extends HttpServlet {
	private static final long serialVersionUID = 1L;
	private Connection connection = null;

	public CheckSignupCredentials() {
		super();
	}

	@Override
	public void init() throws ServletException {
		connection = ConnectionHandler.getConnection(getServletContext());
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

		String email = req.getParameter("email");
		String password = req.getParameter("password");
		String passwordCheck = req.getParameter("passwordCheck");
		String username = req.getParameter("username");

		resp.setContentType("text/plain");

		// checking credentials are not null or empty
		if (email == null || password == null || passwordCheck == null || username == null || email.isEmpty()
				|| password.isEmpty() || username.isEmpty()) {
			resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
			resp.getWriter().println("Errore: Credenziali mancanti o nulle");
			return;
		}

		// Validate email
		Pattern emailPattern = Pattern.compile("^.+@.+\\..+$");
		if (!emailPattern.matcher(email).matches()) {
			resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
			resp.getWriter().println("Errore: Email non valida");
			return;
		}
		UserDAO userDao = new UserDAO(connection);
		
		try {
			if (!userDao.emailIsDuplicate(email)) {
				resp.setStatus(HttpServletResponse.SC_CONFLICT);
				resp.getWriter().println("Errore: Email already exists");
				return;
			}
		} catch (SQLException e) {
			resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "SQL error; query non andata a buon fine");
			return;
		}

		// check that the entered passwords match
		if (!passwordCheck.equals(password)) {
			resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
			resp.getWriter().println("Le password inserite non corrispondono!");
			return;
		}


		// checks the uniqueness of the username
		boolean isDuplicate;
		try {
			isDuplicate = userDao.checkRegister(username);
		} catch (SQLException e) {
			resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			resp.getWriter().println("SQL error: impossibile controllare unicità dello username");
			return;
		}

		if (isDuplicate) {
			resp.setStatus(HttpServletResponse.SC_CONFLICT);
			resp.getWriter().println("Username already exists!");
			return;
		}

		// adds the user to the db
		try {
			userDao.registerUser(email, password, username);
		} catch (SQLException e) {
			resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			resp.getWriter().println("SQL error: impossibile registrare l'utente");
			return;
		}

		// Extracts the user in the DB
		User utente;
		try {
			utente = userDao.getUtenteByUsername(username);
			req.getSession().setMaxInactiveInterval(300);
			req.getSession().setAttribute("utente", utente);
			resp.setStatus(HttpServletResponse.SC_OK);
			resp.setContentType("application/json");
			resp.setCharacterEncoding("UTF-8");
			resp.getWriter().println(utente.getEmail());

			FolderDAO folderDao = new FolderDAO(connection);
			try {

				int code;
				code = folderDao.createHomePageFolder(utente.getUserID());
				if (code != 1) {
					resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
							"SQL error: query non andata a buon fine");
					return;
				}
			} catch (SQLException e) {
				resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "SQL error: query non andata a buon fine");
				return;
			}

		} catch (SQLException e) {
			resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
					"Errore SQL: impossibile ricavare l'utente richiesto");
			return;
		}

		ArrayList<String> versionQueue = new ArrayList<>();
		req.getSession().setAttribute("versionQueue", versionQueue);
		
	}

	
	@Override
	public void destroy() {
		try {
			ConnectionHandler.closeConnection(connection);
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
}
