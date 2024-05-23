package it.polimi.tiw.controllers;

import it.polimi.tiw.beans.User;
import it.polimi.tiw.dao.UserDAO;
import it.polimi.tiw.utils.ConnectionHandler;
import it.polimi.tiw.utils.TreeNode;

import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Queue;

@WebServlet("/CheckLoginCredentials")
@MultipartConfig
public class CheckLoginCredentials extends HttpServlet {
	private static final long serialVersionUID = 1L;
	private Connection connection = null;

	public CheckLoginCredentials() {
		super();
	}

	@Override
	public void init() throws ServletException {
		connection = ConnectionHandler.getConnection(getServletContext());
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		// Getting and checking the credentials
		String username = req.getParameter("username");
		String password = req.getParameter("password");

		resp.setContentType("text/plain");

		if (username == null || password == null || username.isEmpty() || password.isEmpty()) {
			resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
			resp.getWriter().println("Credenziali vuote o mancanti");
			return;
		}

		UserDAO utenteDAO = new UserDAO(connection);

		User utente;
		try {
			// Checking validity of credentials
			utente = utenteDAO.checkLogin(username, password);
		} catch (SQLException e) {
			resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			resp.getWriter().println("Impossibile validare le credenziali");
			return;
		}

		if (utente == null) {
			resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
			resp.getWriter().println("Username o password errati");
			return;
		} else {
			req.getSession().setMaxInactiveInterval(300);
			req.getSession().setAttribute("utente", utente);

			resp.setStatus(HttpServletResponse.SC_OK);
			resp.setContentType("application/json");
			resp.setCharacterEncoding("UTF-8");
			resp.getWriter().println(utente.getUsername());

			ArrayList<String> versionQueue = new ArrayList<>();
			req.getSession().setAttribute("versionQueue", versionQueue);

			ArrayList<String> privateVersionQueue = new ArrayList<>();
			req.getSession().setAttribute("privateVersionQueue", privateVersionQueue);

			// Crea il file di salvataggio per il reverting in fase di eliminazione
			try {

				String filePath = "REDACTED_HOME/git/TIW_Project_2024_RIA/src/main/java/it/polimi/tiw/utils/SaveDatas_ID_"
						+ utente.getUserID() + ".json";
				File saveDatasFile = new File(filePath);
				saveDatasFile.createNewFile();

				// Sovrascrive il file con una nuova mappa vuota
				try (FileWriter writer = new FileWriter(filePath)) {

					HashMap<Integer, TreeNode> datasMap = new HashMap<Integer, TreeNode>();
					Gson gson = new GsonBuilder().setPrettyPrinting().create();
					gson.toJson(datasMap, writer);

				} catch (IOException ex) {
					ex.printStackTrace();
				}

				return;

			} catch (Exception e) {
				System.err.println("Si è verificato un errore durante la creazione del file: " + e.getMessage());
				return;
			}

		}
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
