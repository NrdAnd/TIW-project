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


import it.polimi.tiw.beans.User;
import it.polimi.tiw.dao.DocumentDAO;
import it.polimi.tiw.dao.FolderDAO;
import it.polimi.tiw.utils.ConnectionHandler;


@WebServlet("/OpenContentManager") // Filtered
public class OpenContentManager extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private Connection connection = null;
    private TemplateEngine templateEngine;

    public OpenContentManager() {
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

        String path = "/contentmanager.html";
        ServletContext servletContext = getServletContext();
        WebContext ctx = new WebContext(req, resp, servletContext, req.getLocale());
        
        // Get and check parameters

        User utente = (User) session.getAttribute("utente");
        FolderDAO folderDao = new FolderDAO(connection);
        DocumentDAO documentDao = new DocumentDAO(connection);
        
        
        try {
        	
            ctx.setVariable("documentList", documentDao.getUsersDocuments(utente.getUserID()));
            ctx.setVariable("folderList", folderDao.getAllFolders(utente.getUserID()));
            
        } catch (SQLException e) {
            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "SQL error: impossibile ricavare le cartelle ed i documenti dell'utente");
            return;
        }
        
        try {
        	Stack<String> pageStack = (Stack<String>) session.getAttribute("pageStack");
            pageStack.push("ContentManager");
            session.setAttribute("pageStack", pageStack);
        } catch (NullPointerException e) {
        	resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "PageStack nullo");
            return;
        }
        
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
