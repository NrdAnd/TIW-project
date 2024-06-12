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
import it.polimi.tiw.utils.VersionHandler;

@WebServlet("/CreateDocument") // Filtered
public class CreateDocument extends HttpServlet {
	private static final long serialVersionUID = 1L;
	private Connection connection = null;

	public CreateDocument() {
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

		Integer destinationID = null;
		String newDocumentName = null, summary = null, documentType = null;
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
				destinationID = Integer.parseInt(items.get(3).getString());
			} catch (NumberFormatException | NullPointerException e) {
				resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
				resp.getWriter().println("Errore: DestinationID non valido");
				return;
			}

			try {
				newDocumentName = items.get(0).getString();
			} catch (IllegalArgumentException | NullPointerException e) {
				resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
				resp.getWriter().println("Errore: New Document Name non valido");
				return;
			}

			try {
				summary = items.get(2).getString();
			} catch (IllegalArgumentException | NullPointerException e) {
				resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
				resp.getWriter().println("Errore: Summary non valido");
				return;
			}

			try {
				documentType = items.get(1).getString();
			} catch (IllegalArgumentException | NullPointerException e) {
				resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
				resp.getWriter().println("Errore: Document Type non valido");
				return;
			}
			
			if(newDocumentName.isBlank() || summary.isBlank() || documentType.isBlank() || newDocumentName.length()>25 || summary.length()>250 || documentType.length()>5) {
				resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
				resp.getWriter().println("Errore: Parametri non validi");
				return;
			}

		}

		int code;
		User utente = (User) session.getAttribute("utente");
		DocumentDAO documentDao = new DocumentDAO(connection);
		
		try {
			if(!documentDao.checkUniqueName(utente.getUserID(), newDocumentName)){
				resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
				resp.getWriter().println("Possiedi un documento con questo nome");
				return;
			}
		} catch (SQLException e) {
			resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			resp.getWriter().println("Errore SQL: Query non andata a buon fine");
			return;
		}
		
		try {

			code = documentDao.createDocument(utente.getUserID(), newDocumentName, summary, documentType,
					destinationID);

			if (code != 1) {
				resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
				resp.getWriter().println("Errore SQL: impossibile effettuare il salvataggio del documento nel DB");
				return;
			}
		} catch (SQLException e) {
			resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			resp.getWriter().println("Errore SQL: impossibile effettuare il salvataggio del documento nel DB");
			return;
		}
		
		
		String folderName;
		FolderDAO folderDao = new FolderDAO(connection);
		
		try {
			folderName = folderDao.getFolderName(utente.getUserID(), destinationID);
			if (folderName == null) {
				resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
				resp.getWriter().println("Errore: Folder Name non valido");
				return;
			}
		} catch (SQLException e) {
			resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			resp.getWriter().println("Errore SQL: impossibile estrarre il Nome della Folder ID dal DB");
			return;
		}
		
		String parentFolderName;
		try {
			parentFolderName = folderDao.getParentFolderName(utente.getUserID(), destinationID);
			if (parentFolderName == null) {
				resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
				resp.getWriter().println("Errore: Parent Folder Name non valido");
				return;
			}
			
		} catch (SQLException e) {
			resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			resp.getWriter().println("Errore SQL: impossibile estrarre il Nome della Parent Folder ID dal DB");
			return;
		}
		
		VersionHandler.checkName(utente.getUserID(), resp, newDocumentName, null, session);
		
		String operationString = "CREATED DOCUMENT: " + newDocumentName + " INSIDE FOLDER: "+ parentFolderName +"/"+folderName;

		ArrayList<String> versionQueue = (ArrayList<String>) session.getAttribute("versionQueue");
		if (versionQueue.size() < 10) {
			versionQueue.add(operationString);
		} else {
			versionQueue.remove(0);
			versionQueue.add(operationString);
		}
		
		int documentID;
		try {
			documentID = documentDao.getLastDocumentID(utente.getUserID());
			if (documentID == -1) {
				resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
				resp.getWriter().println("Errore: Document ID non valido");
				return;
			}
		} catch (SQLException e) {
			resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			resp.getWriter().println("Errore SQL: impossibile estrarre il Max Document ID dal DB");
			return;
		}
		
		String privateOperationString = "CD_" + documentID;
		ArrayList<String> privateVersionQueue = (ArrayList<String>) session.getAttribute("privateVersionQueue");
		if (privateVersionQueue.size() < 10) {
			privateVersionQueue.add(privateOperationString);
		} else {
			privateVersionQueue.remove(0);
			privateVersionQueue.add(privateOperationString);
		}
		
		session.setAttribute("privateVersionQueue", privateVersionQueue);
		session.setAttribute("versionQueue", versionQueue);
		
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
