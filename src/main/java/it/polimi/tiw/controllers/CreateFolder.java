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
	private TemplateEngine templateEngine;

	public CreateFolder() {
		super();
	}

	@Override
	public void init() throws ServletException {
		connection = ConnectionHandler.getConnection(getServletContext());
		ServletContext servletContext = getServletContext();
		ServletContextTemplateResolver templateResolver = new ServletContextTemplateResolver(servletContext);
		templateResolver.setTemplateMode(TemplateMode.HTML);
		this.templateEngine = new TemplateEngine();
		this.templateEngine.setTemplateResolver(templateResolver);
		templateResolver.setSuffix(".html");
	}

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		doPost(req, resp);
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

		HttpSession session = req.getSession();

		Integer destinationID;
		String newFolderName;
		try {
			destinationID = Integer.parseInt(req.getParameter("destinationID"));
		} catch (NumberFormatException | NullPointerException e) {
			resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "DestinationID mancante o vuoto");
			return;
		}

		try {
			newFolderName = req.getParameter("newFolderName");
		} catch (IllegalArgumentException | NullPointerException e) {
			resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "NewFolderName mancante o vuoto");
			return;
		}

		
		FolderDAO folderDao = new FolderDAO(connection);
		User utente = (User) session.getAttribute("utente");
		int parentFolderDepth;

		try {
			parentFolderDepth = folderDao.getDepthByID(utente.getUserID(), destinationID);
		} catch (SQLException e) {
			resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "SQL error: query non andata a buon fine");
			return;
		}

		if (parentFolderDepth < 0) {
			resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "ParentFolderDepth mancante o vuoto");
			return;
		}

		/*DateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		Timestamp timestamp;
		try {
			timestamp = new Timestamp(dateFormatter.parse(req.getParameter("Data")).getTime());
		} catch (ParseException | NullPointerException e) {
			resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "La formattazione della data non è andata a buon fine");
			e.printStackTrace();
			return;
		}
		*/
		
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
				resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "SQL error: query non andata a buon fine");
				return;
			}
		} catch (SQLException e) {
			resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "SQL error: query non andata a buon fine");
			return;
		}
		
		String url = req.getContextPath() + "/OpenContentManager";
		resp.sendRedirect(url);
		
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
