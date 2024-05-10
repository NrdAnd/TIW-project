package it.polimi.tiw.controllers;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Stack;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ServletContextTemplateResolver;

import it.polimi.tiw.beans.Document;
import it.polimi.tiw.beans.User;
import it.polimi.tiw.dao.DocumentDAO;
import it.polimi.tiw.utils.ConnectionHandler;
import it.polimi.tiw.utils.TreeNode;


@WebServlet("/MoveDocument") // Filtered
public class MoveDocument extends HttpServlet {
	private static final long serialVersionUID = 1L;
	private Connection connection = null;
	private TemplateEngine templateEngine;
    public MoveDocument() {
        super();
        // TODO Auto-generated constructor stub
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
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		HttpSession session = req.getSession();
		
		ServletContext servletContext = getServletContext();
		final WebContext ctx = new WebContext(req, resp, servletContext, req.getLocale());
		
		User utente = (User) session.getAttribute("utente");
		Integer destinationFolderID,documentID;
        try {
        	destinationFolderID = Integer.parseInt(req.getParameter("destinationFolderID"));
        }catch(NumberFormatException | NullPointerException e) {
        	resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "DestinationFolderID non trovato");
        	return;
        }
        try {
        	documentID = Integer.parseInt(req.getParameter("documentID"));
        }catch(NumberFormatException | NullPointerException e) {
        	resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "DocumentID non trovato");
        	return;
        }
        
        DocumentDAO documentDAO = new DocumentDAO(connection);
        
        try {
            documentDAO.updateDocumentPosition(documentID, destinationFolderID,utente.getUserID()); 
        } catch (SQLException e) {
            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Errore durante l'aggiornamento della posizione del documento");
            return;
        }
        
        String url = req.getContextPath() + "/GoToFolder?folderID=" + destinationFolderID;
        resp.sendRedirect(url);
    }
	
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		doPost(req,resp);
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
