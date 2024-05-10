package it.polimi.tiw.controllers;

import it.polimi.tiw.beans.Document;
import it.polimi.tiw.beans.Folder;
import it.polimi.tiw.beans.User;
import it.polimi.tiw.dao.DocumentDAO;
import it.polimi.tiw.dao.FolderDAO;
import it.polimi.tiw.utils.ConnectionHandler;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ServletContextTemplateResolver;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Stack;


@WebServlet("/OpenDocument") // Filtered
public class OpenDocument extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private Connection connection = null;
    private TemplateEngine templateEngine;

    public OpenDocument() {
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
        HttpSession session = req.getSession();

        String path = "/document.html";
        ServletContext servletContext = getServletContext();
        WebContext ctx = new WebContext(req, resp, servletContext, req.getLocale());
        
        
        // Get and check parameters
        Integer documentID;
        try {
             documentID = Integer.parseInt(req.getParameter("documentID"));
        } catch (NumberFormatException | NullPointerException e) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "DocumentID mancante o vuoto");
            return;
        }
        
        User utente = (User) session.getAttribute("utente");
        DocumentDAO documentDao = new DocumentDAO(connection);
        Document document;
        FolderDAO folderDao = new FolderDAO(connection);
        
        try {
        	
        	document = documentDao.findDocumentByID(utente.getUserID(), documentID);
            ctx.setVariable("documentID", document.getDocumentID());
            ctx.setVariable("parentFolderID", document.getFolderID());
            ctx.setVariable("folderName", folderDao.getFolderName(utente.getUserID(),document.getFolderID()));
            ctx.setVariable("ownerID", document.getOwnerID());
            ctx.setVariable("creationDate", document.getCreationDate());
            ctx.setVariable("summary", document.getSummary());
            ctx.setVariable("documentType", document.getDocumentType());
            ctx.setVariable("documentName", document.getDocumentName());
              
        } catch (SQLException | NullPointerException e) {
            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "SQL error: impossibile ricavare il documento dell'utente");
            return;
        }
        
        try {
        	Stack<String> pageStack = (Stack<String>) session.getAttribute("pageStack");
            pageStack.push("Document");
            session.setAttribute("pageStack", pageStack);
        } catch (NullPointerException e) {
        	resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "PageStack nullo");
            return;
        }
        
        templateEngine.process(path, ctx, resp.getWriter());
    }
   
    
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        super.doPost(req, resp);
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
