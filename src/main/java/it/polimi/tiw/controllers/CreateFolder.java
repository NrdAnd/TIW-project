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

import org.apache.tomcat.util.http.fileupload.FileItem;
import org.apache.tomcat.util.http.fileupload.disk.DiskFileItemFactory;
import org.apache.tomcat.util.http.fileupload.servlet.ServletFileUpload;
import org.apache.tomcat.util.http.fileupload.servlet.ServletRequestContext;

import it.polimi.tiw.beans.User;
import it.polimi.tiw.dao.FolderDAO;
import it.polimi.tiw.utils.ConnectionHandler;

@WebServlet("/CreateFolder") // Filtered
public class CreateFolder extends HttpServlet {
	private static final long serialVersionUID = 1L;
	private Connection connection = null;

	public CreateFolder() {
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
		String newFolderName = null;
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
				return;
			}

			try {

				newFolderName = items.get(0).getString();

			} catch (IllegalStateException | NullPointerException e) {
				resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
				resp.getWriter().println("Error: New Folder Name is invalid");
				return;
			}

			try {

				// Saves the value of the documentID
				destinationID = Integer.parseInt(items.get(1).getString());

			} catch (NumberFormatException | NullPointerException e) {
				resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
				resp.getWriter().println("Error: Destination ID is invalid");
				return;
			}
		} 
		
		else {
			resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
			resp.getWriter().println("FormData is unacceptable");
			return;
		}

		if (destinationID == 0 || newFolderName.isBlank() || newFolderName.length()>25) {
			resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			resp.getWriter().println("Invalid parameters (FolderName)");
			return;
		}

		FolderDAO folderDao = new FolderDAO(connection);
		User utente = (User) session.getAttribute("utente");
		int parentFolderDepth;

		try {
			parentFolderDepth = folderDao.getDepthByID(utente.getUserID(), destinationID);
		} catch (SQLException e) {
			resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			resp.getWriter().println("SQL Error: impossible fetching parent folder depth");
			return;
		}

		if (parentFolderDepth < 0) {
			resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
			resp.getWriter().println("Error: Parent folder does not exists");
			return;
		}
		try {
			if (!folderDao.checkUniqueName(utente.getUserID(), destinationID, newFolderName)) {
				resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
				resp.getWriter().println("There is already a folder with the same name in this position");
				return;
			}
		} catch (SQLException e) {
			resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			resp.getWriter().println("SQL Error: Something went wrong with the query");
			return;
		}

		try {

			int code;
			code = folderDao.createFolder(utente.getUserID(), newFolderName, destinationID,
					parentFolderDepth + 1, null);
			if (code != 1) {
				resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
				resp.getWriter().println("Error: Impossible to create Folder");
				return;
			}
		} catch (SQLException e) {
			resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			resp.getWriter().println("SQL Error: Impossible to create Folder");
		}

		String folderName;
		try {
			folderName = folderDao.getFolderName(utente.getUserID(), destinationID);
			if (folderName == null) {
				resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
				resp.getWriter().println("Error: Folder Name is invalid");
				return;
			}
		} catch (SQLException e) {
			resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			resp.getWriter().println("SQL Error: impossible to fetch Folder name");
			return;
		}
		
		
		String operationString = "CREATED FOLDER: " + newFolderName + " INSIDE: " + folderName;

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
	public void destroy() {
		try {
			ConnectionHandler.closeConnection(connection);
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

}