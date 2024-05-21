package it.polimi.tiw.controllers;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.tomcat.util.http.fileupload.FileItem;
import org.apache.tomcat.util.http.fileupload.disk.DiskFileItemFactory;
import org.apache.tomcat.util.http.fileupload.servlet.ServletFileUpload;
import org.apache.tomcat.util.http.fileupload.servlet.ServletRequestContext;

import it.polimi.tiw.beans.User;
import it.polimi.tiw.dao.DocumentDAO;
import it.polimi.tiw.dao.FolderDAO;
import it.polimi.tiw.utils.ConnectionHandler;

@WebServlet("/MoveDocument") // Filtered
public class MoveDocument extends HttpServlet {
	private static final long serialVersionUID = 1L;
	private Connection connection = null;

	public MoveDocument() {
		super();
	}

	@Override
	public void init() throws ServletException {
		connection = ConnectionHandler.getConnection(getServletContext());
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

		HttpSession session = req.getSession();
		resp.setContentType("text/plain");

		Integer destinationFolderID = null, documentID = null;
		String contentType = req.getContentType();

		if (contentType != null && contentType.startsWith("multipart/form-data")) {

			DiskFileItemFactory factory = new DiskFileItemFactory();
			ServletFileUpload upload = new ServletFileUpload(factory);
			List<FileItem> items = null;

			try {

				ServletRequestContext requestContext = new ServletRequestContext(req);

				// Parses the multipart request data
				items = upload.parseRequest(requestContext);
			} catch (Exception e) {
				e.printStackTrace();
			}

			try {
				// Saves the value of the destinationFolderID and the documentID
				destinationFolderID = Integer.parseInt(items.get(0).getString());
				documentID = Integer.parseInt(items.get(1).getString());

			} catch (NumberFormatException | NullPointerException e) {
				resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
				resp.getWriter().println("Errore: Folder ID o Document ID non valido");
				return;
			}

		}

		User utente = (User) session.getAttribute("utente");
		DocumentDAO documentDAO = new DocumentDAO(connection);
		FolderDAO folderDao = new FolderDAO(connection);

		String initialFolderName;
		int initialFolderID;
		try {

			initialFolderID = documentDAO.getOriginFolder(utente.getUserID(), documentID);
			if (initialFolderID == -1) {
				resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
				resp.getWriter().println("Errore: Folder ID non valido");
				return;
			}

			initialFolderName = folderDao.getFolderName(utente.getUserID(), initialFolderID);
			if (initialFolderName == null) {
				resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
				resp.getWriter().println("Errore: Initial Folder Name non valido");
				return;
			}

		} catch (SQLException e) {
			resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			resp.getWriter().println("Errore SQL: impossibile eseguire l'estrazione del nome della cartella nel DB");
			return;
		}
		
		
		String initialParentFolderName;
		try {
			
			initialParentFolderName = folderDao.getParentFolderName(utente.getUserID(), initialFolderID);
					
		} catch (SQLException e) {
			resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			resp.getWriter().println("Errore SQL: impossibile eseguire l'estrazione del nome della cartella padre iniziale nel DB");
			return;
		}

		
		
		try {
			documentDAO.updateDocumentPosition(documentID, destinationFolderID, utente.getUserID());
		} catch (SQLException e) {
			resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			resp.getWriter().println("Errore SQL: impossibile eseguire la update della posizione del documento nel DB");
			return;
		}
		
		

		String postFolderName;
		int postFolderID;
		try {
			
			postFolderID = documentDAO.getOriginFolder(utente.getUserID(), documentID);
			if (postFolderID == -1) {
				resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
				resp.getWriter().println("Errore: Folder ID non valido");
				return;
			}

			postFolderName = folderDao.getFolderName(utente.getUserID(), postFolderID);
			if (postFolderName == null) {
				resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
				resp.getWriter().println("Errore: Post Folder Name non valido");
				return;
			}

		} catch (SQLException e) {
			resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			resp.getWriter().println("Errore SQL: impossibile eseguire l'estrazione del nome della cartella nel DB");
			return;
		}
		
		
		
		String postParentFolderName;
		try {
			
			postParentFolderName = folderDao.getParentFolderName(utente.getUserID(), postFolderID);
			
		} catch (SQLException e) {
			resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			resp.getWriter().println("Errore SQL: impossibile eseguire l'estrazione del nome della cartella padre finale nel DB");
			return;
		}

		
		String operationString = null;
		operationString = "MOVE_DOCUMENT >> FROM: " + initialFolderName + " (PF: " + initialParentFolderName + ") TO: " + postFolderName + 
				" (PF: " + postParentFolderName + ")";

		ArrayList<String> versionQueue = (ArrayList<String>) session.getAttribute("versionQueue");
		if (versionQueue.size() < 10) {
			versionQueue.add(operationString);
		} else {
			versionQueue.remove(0);
			versionQueue.add(operationString);
		}
		
		String privateOperationString = "MD_" + documentID + "_" + initialFolderID + "_" + postFolderID;
		ArrayList<String> privateVersionQueue = (ArrayList<String>) session.getAttribute("privateVersionQueue");
		if (privateVersionQueue.size() < 10) {
			privateVersionQueue.add(privateOperationString);
		} else {
			privateVersionQueue.remove(0);
			privateVersionQueue.add(privateOperationString);
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
