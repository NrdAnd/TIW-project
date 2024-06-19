package it.polimi.tiw.controllers;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.tomcat.util.http.fileupload.disk.DiskFileItemFactory;
import org.apache.tomcat.util.http.fileupload.servlet.ServletFileUpload;
import org.apache.tomcat.util.http.fileupload.servlet.ServletRequestContext;
import org.apache.tomcat.util.http.fileupload.FileItem;

import it.polimi.tiw.beans.User;
import it.polimi.tiw.dao.DocumentDAO;
import it.polimi.tiw.utils.ConnectionHandler;

@WebServlet("/DeleteDocument")
public class DeleteDocument extends HttpServlet {
	private static final long serialVersionUID = 1L;
	private Connection connection = null;

	public DeleteDocument() {
		super();
	}

	@Override
	public void init() throws ServletException {
		connection = ConnectionHandler.getConnection(getServletContext());
	}

	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

		HttpSession session = req.getSession();
		resp.setContentType("text/plain");

		Integer documentID = null;
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
				// Saves the value of the documentID
				documentID = Integer.parseInt(items.get(0).getString());
			} catch (NumberFormatException | NullPointerException e) {
				resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
				resp.getWriter().println("Errore: Invalid Document ID");
				return;
			}
		}

		User utente = (User) session.getAttribute("utente");
		DocumentDAO documentDao = new DocumentDAO(connection);
		
		String documentName;
		
		try {
			documentName = documentDao.getDocumentNameByDocumentID(utente.getUserID(), documentID);
			if (documentName == null) {
				resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
				resp.getWriter().println("Error: Document Name is invalid");
				return;
			}
		} catch (SQLException e) {
			resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			resp.getWriter().println("SQL Error: impossible to fetch Document name");
			return;
		}
		
		
		try {
			documentDao.deleteDocument(utente.getUserID(), documentID);
			resp.setStatus(HttpServletResponse.SC_OK);
		} catch (SQLException e) {
			resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			resp.getWriter().println("SQL Error: Impossible to delete the document");
			return;
		}
		
		
		String operationString = "DELETED DOCUMENT: " + documentName;

		ArrayList<String> versionQueue = (ArrayList<String>) session.getAttribute("versionQueue");
		if (versionQueue.size() < 10) {
			versionQueue.add(operationString);
		} else {
			versionQueue.remove(0);
			versionQueue.add(operationString);
		}
		
		session.setAttribute("versionQueue", versionQueue);
		
	}

    @Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		doPost(req, resp);
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
