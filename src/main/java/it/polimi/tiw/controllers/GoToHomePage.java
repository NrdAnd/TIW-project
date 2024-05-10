package it.polimi.tiw.controllers;

import it.polimi.tiw.beans.Folder;
import it.polimi.tiw.beans.User;
import it.polimi.tiw.dao.FolderDAO;

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
import java.util.ArrayList;
import java.util.Stack;

@WebServlet("/GoToHomePage") // Filtered
public class GoToHomePage extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private Connection connection = null;
    private TemplateEngine templateEngine;

    public GoToHomePage() {
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

        String path = "/homepage.html";
        ServletContext servletContext = getServletContext();
        WebContext ctx = new WebContext(req, resp, servletContext, req.getLocale());

        User utente = (User) session.getAttribute("utente");
        
        try {
        	Stack<String> pageStack = (Stack<String>) session.getAttribute("pageStack");
        	//Svuota lo stack che contiene tutta la sequenza di pagine visualizzate
            pageStack.clear();
            //Risetta il pageStack vuoto
            session.setAttribute("pageStack", pageStack);
        } catch (NullPointerException e) {
        	resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "PageStack nullo");
            return;
        }
        
        FolderDAO folderDao = new FolderDAO(connection);
        //DocumentDAO documentDao = new DocumentDAO(connection);
        
        
        try {
        	
            ctx.setVariable("folderTree", folderDao.getFolderTree(utente.getUserID()));
        	//ctx.setVariable("folderTree", folderDao.getSubTreeFolder(utente.getUserID(), 1));
        	
            ctx.setVariable("isMovingAction", false);
            
            /*
             * ctx.setVariable("numberOfDocuments", documentDao.getAllDocuments(utente.getUserID()); se si vuole 
             * far vedere il numero di documenti delle cartelle bisognerebbe creare un metodo nel DAO che restituisca la hashMap in cui c'è associazione 
             * tra la cartella e il numero di documenti trovati in esso 
            */
            
            templateEngine.process(path, ctx, resp.getWriter());
        	      
            
        } catch (SQLException e) {
            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "SQL error: impossibile ricavare le cartelle ed i documenti dell'utente");
            return;
        }
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
