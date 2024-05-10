package it.polimi.tiw.controllers;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;

import it.polimi.tiw.beans.User;
import it.polimi.tiw.beans.Document;
import it.polimi.tiw.dao.DocumentDAO;
import it.polimi.tiw.dao.FolderDAO;
import it.polimi.tiw.utils.TreeNode;

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

import it.polimi.tiw.utils.ConnectionHandler;


@WebServlet("/SelectDocumentToMove") // Filtered
public class SelectDocumentToMove extends HttpServlet {
	private static final long serialVersionUID = 1L;
	private Connection connection = null;
	private TemplateEngine templateEngine;
	    
	 
    public SelectDocumentToMove() {
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
		String path;
        final WebContext ctx = new WebContext(req, resp, getServletContext(), req.getLocale());
        
        
        Integer documentID, originFolderID;
        String originFolderName;
        try {
        	originFolderID = Integer.parseInt(req.getParameter("originFolderID"));
        	documentID = Integer.parseInt(req.getParameter("documentID"));
        } catch (NumberFormatException | NullPointerException e) {
        	resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "DocumentID mancante o vuoto");
        	return;
        }
        
        try {
        	originFolderID = Integer.parseInt(req.getParameter("originFolderID"));
        } catch (NumberFormatException | NullPointerException e) {
        	resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "OriginFolderID mancante o vuoto");
        	return;
        }
        
        try {
        	originFolderName = req.getParameter("folderName");
        } catch (IllegalArgumentException | NullPointerException e) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "OriginFolderName mancante o vuoto");
            return;
        }
        
        
        //Search the document in the DB
        DocumentDAO documentDao = new DocumentDAO(connection);
        Document document;
        try {
        	User utente = (User) session.getAttribute("utente");
        	document = documentDao.findDocumentByID(utente.getUserID(), documentID);
        	
        	// If the specified account isn't owned by the current users, redirects to the homepage  (Document = null is okey, maybe the other part is overkill)
            if(document == null || utente.getUserID() != document.getOwnerID()){
                resp.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Errore: il documento richiesto è nullo o non fa parte dei tuoi docuementi.");
                return;
            }
            
        } catch (SQLException e) {
        	resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "SQL error: impossibile ricavare informazioni sul documento richiesto");
        	return;
        }
        
        
        if (document.getFolderID() != originFolderID) {
        	resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Il documento non appartiene alla cartella da cui è stato selezionato");
        	return;
        }
        
        
        //Extraction of tree folder from the DB
        FolderDAO folderDao = new FolderDAO(connection);
        TreeNode folderTree;
        try {
        	
        	User utente = (User) session.getAttribute("utente");
            folderTree = folderDao.getFolderTree(utente.getUserID());
            
            /*
             * ctx.setVariable("numberOfDocuments", documentDao.getAllDocuments(utente.getUserID()); se si vuole 
             * far vedere il numero di documenti delle cartelle bisognerebbe creare un metodo nel DAO che restituisca la hashMap in cui c'è associazione 
             * tra la cartella e il numero di documenti trovati in esso 
            */
            
            
        } catch (SQLException e) {
            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "SQL error: impossibile ricavare le cartelle ed i documenti dell'utente");
            return;
        }
        
        path = "/homepage.html";
        ctx.setVariable("originFolderID", document.getFolderID());
        ctx.setVariable("originFolderName", originFolderName);
        ctx.setVariable("documentID", document.getDocumentID());
        ctx.setVariable("documentName", document.getDocumentName());
        ctx.setVariable("isMovingAction", true);
        ctx.setVariable("folderTree", folderTree);
        
        templateEngine.process(path, ctx, resp.getWriter());

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
