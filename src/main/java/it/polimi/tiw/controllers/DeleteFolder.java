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
import it.polimi.tiw.dao.FolderDAO;
import it.polimi.tiw.utils.ConnectionHandler;
import it.polimi.tiw.utils.VersionHandler;

@WebServlet("/DeleteFolder")
public class DeleteFolder extends HttpServlet {
	private static final long serialVersionUID = 1L;
	private Connection connection = null;

	public DeleteFolder() {
		super();

	}

	@Override
	public void init() throws ServletException {
		connection = ConnectionHandler.getConnection(getServletContext());
	}

	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

		HttpSession session = req.getSession();
		resp.setContentType("text/plain");
		
		Integer folderID = null;
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
				// Saves the value of the folderID
				folderID = Integer.parseInt(items.get(0).getString());
			} catch (NumberFormatException | NullPointerException e) {
				resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
				resp.getWriter().println("Errore: Folder ID non valido");
				return;
			}
		}

		User utente = (User) session.getAttribute("utente");
		FolderDAO folderDao = new FolderDAO(connection);
		
		
		String folderName;
		try {
			folderName = folderDao.getFolderName(utente.getUserID(), folderID);
		} catch (SQLException e) {
			resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			resp.getWriter().println("Errore SQL: impossibile estrarre il nome della cartella");
			return;
		}
				
		
		VersionHandler.saveDeletionDatas(utente.getUserID(), resp, folderID, 1);
		
		
		String parentFolderName;
		try {
			parentFolderName = folderDao.getParentFolderName(utente.getUserID(), folderID);
		} catch (SQLException e) {
			resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			resp.getWriter().println("Errore SQL: impossibile effettuare l'estrazione del parent folder name dal DB");
			return;
		}
		
		try {
			folderDao.deleteFolder(utente.getUserID(), folderID);
		} catch (SQLException e) {
			resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			resp.getWriter().println("Errore SQL: impossibile effettuare l'eliminazione della Folder nel DB");
			return;
		}
		

		String operationString;
		operationString = "DELETE_FOLDER >> FOLDER: " + folderName + " (PF: " + parentFolderName + ")";
		
		ArrayList<String> versionQueue = (ArrayList<String>) session.getAttribute("versionQueue");
		if (versionQueue.size() < 10) {
			versionQueue.add(operationString);
		} else {
			versionQueue.remove(0);
			versionQueue.add(operationString);
		}
		
		
		String privateOperationString = "DF_" + folderID;
		ArrayList<String> privateVersionQueue = (ArrayList<String>) session.getAttribute("privateVersionQueue");
		if (privateVersionQueue.size() < 10) {
			privateVersionQueue.add(privateOperationString);
		} else {
			privateVersionQueue.remove(0);
			privateVersionQueue.add(privateOperationString);
		}
		
		session.setAttribute("privateVersionQueue", privateVersionQueue);
		session.setAttribute("versionQueue", versionQueue);
		
		
		VersionHandler.changeVersionHistory(utente.getUserID(), resp, folderID, session);
		
	}
	
	@Override
    public void destroy() {
        try{
            ConnectionHandler.closeConnection(connection);
        }catch(SQLException e){
            e.printStackTrace();
        }
    }

}
