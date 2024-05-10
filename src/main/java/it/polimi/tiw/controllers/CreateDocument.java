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

@WebServlet("/CreateDocument") // Filtered
public class CreateDocument extends HttpServlet {
	private static final long serialVersionUID = 1L;
	private Connection connection = null;
	private TemplateEngine templateEngine;

	public CreateDocument() {
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
		String newDocumentName, summary, documentType;
		try {
			destinationID = Integer.parseInt(req.getParameter("destinationID"));
		} catch (NumberFormatException | NullPointerException e) {
			resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "DestinationID mancante o vuoto");
			return;
		}

		try {
			newDocumentName = req.getParameter("newDocumentName");
		} catch (IllegalArgumentException | NullPointerException e) {
			resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "NewDocumentName mancante o vuoto");
			return;
		}
		
		try {
			summary = req.getParameter("summary");
		} catch (IllegalArgumentException | NullPointerException e) {
			resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Summary mancante o vuoto");
			return;
		}
		
		try {
			documentType = req.getParameter("documentType");
		} catch (IllegalArgumentException | NullPointerException e) {
			resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "DocumentType mancante o vuoto");
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
		
		
		User utente = (User) session.getAttribute("utente");
		DocumentDAO documentDao = new DocumentDAO(connection);

		int code;
		try {

			code = documentDao.createDocument(utente.getUserID(), newDocumentName, summary, documentType, destinationID);
			
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
