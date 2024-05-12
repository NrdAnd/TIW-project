package it.polimi.tiw.controllers;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.thymeleaf.TemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ServletContextTemplateResolver;

import it.polimi.tiw.beans.User;
import it.polimi.tiw.dao.DocumentDAO;
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

		Integer destinationID;
		String newFolderName;
		try {
			destinationID = Integer.parseInt(req.getParameter("destinationID"));
		} catch (NumberFormatException | NullPointerException e) {
			resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().println("Errore: DestinationID non valido");
            return;
		}

		try {
			newFolderName = req.getParameter("newFolderName");
		} catch (IllegalArgumentException | NullPointerException e) {
			resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().println("Errore: New Folder Name non valido");
            return;
		}

		
		FolderDAO folderDao = new FolderDAO(connection);
		User utente = (User) session.getAttribute("utente");
		int parentFolderDepth;

		try {
			parentFolderDepth = folderDao.getDepthByID(utente.getUserID(), destinationID);
		} catch (SQLException e) {
			resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().println("Errore SQL: impossibile estrarre la profondità della cartella padre");
            return;
		}

		if (parentFolderDepth < 0) {
			resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().println("Errore: Profondità negativa");
            return;
		}
		
		boolean newFolderIsRoot;
		if (parentFolderDepth == 0) {
			newFolderIsRoot = true;
		} else {
			newFolderIsRoot = false;
		}

		
		try {
			
			int code;
			code = folderDao.createFolder(utente.getUserID(), newFolderName, destinationID, newFolderIsRoot, parentFolderDepth + 1);
			if (code != 1) {
				resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
	            resp.getWriter().println("Errore SQL: impossibile effettuare il salvataggio in ");
			}
		} catch (SQLException e) {
			resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().println("Errore SQL: impossibile effettuare il salvataggio in rubrica");
		}
	
		
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
