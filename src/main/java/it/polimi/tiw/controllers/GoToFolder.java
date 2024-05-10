package it.polimi.tiw.controllers;

import it.polimi.tiw.beans.User;
import it.polimi.tiw.dao.FolderDAO;
import it.polimi.tiw.dao.DocumentDAO;

import it.polimi.tiw.utils.ConnectionHandler;
import it.polimi.tiw.utils.TreeNode;

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


@WebServlet("/GoToFolder") // Filtered
public class GoToFolder extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private Connection connection = null;
    private TemplateEngine templateEngine;

    public GoToFolder() {
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

        String path = "/content.html";
        ServletContext servletContext = getServletContext();
        WebContext ctx = new WebContext(req, resp, servletContext, req.getLocale());
        
        
        // Get and check parameters
        Integer folderID;
        try {
             folderID = Integer.parseInt(req.getParameter("folderID"));
        } catch (NumberFormatException | NullPointerException e) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "FolderID mancante o vuoto");
            return;
        }
        

        User utente = (User) session.getAttribute("utente");
        FolderDAO folderDao = new FolderDAO(connection);
        DocumentDAO documentDao = new DocumentDAO(connection);
        
        
        try {
        	
        	TreeNode folderTree = folderDao.getSubTreeFolder(utente.getUserID(), folderID);
            ctx.setVariable("folderTree", folderTree);
            ctx.setVariable("documentList", documentDao.getAllDocuments(utente.getUserID(), folderID));
            
            /*This control is used to set the parameter that allows replacing the text 'GoBack' with 'Go to HomePage' 
            //on the GoBack button if the folder in question is root.
            if (folderTree.getFolder().isRoot()) {
            	previousPage = "homepage.html";
            }
            
            ctx.setVariable("previousPage", previousPage);*/
            
        } catch (SQLException e) {
            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "SQL error: impossibile ricavare le cartelle ed i documenti dell'utente");
            return;
        }
        
        
        try {
        	
        	Stack<String> pageStack = (Stack<String>) session.getAttribute("pageStack");
        	
        	
        	//Controlla se lo stack è vuoto, se si passa alla content un parametro fittizio per la stampa dinamica del pulsante di GoBack
        	if (!pageStack.isEmpty()) {
        		
            	ctx.setVariable("nextPage", "content.html");
            	
        	} else {
        		
        		ctx.setVariable("nextPage", "homepage.html");
        		
        	}
        	
            pageStack.push(String.valueOf(folderID));
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