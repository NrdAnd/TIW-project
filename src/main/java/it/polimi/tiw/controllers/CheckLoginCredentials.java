package it.polimi.tiw.controllers;

import it.polimi.tiw.beans.User;
import it.polimi.tiw.dao.UserDAO;
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
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Stack;

@WebServlet("/CheckLoginCredentials")
public class CheckLoginCredentials extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private Connection connection = null;
    private TemplateEngine templateEngine;

    public CheckLoginCredentials() {
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
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        // Getting and checking the credentials
        String username = req.getParameter("username");
        String password = req.getParameter("password");

        ServletContext servletContext = getServletContext();
        final WebContext ctx = new WebContext(req, resp, servletContext, req.getLocale());
        String path;

        if(username == null || password == null || username.isEmpty() || password.isEmpty()){
            path = "index.html";
            ctx.setVariable("errorMsg", "Credenziali vuote o mancanti");
            templateEngine.process(path, ctx, resp.getWriter());
            return;
        }

        UserDAO utenteDAO = new UserDAO(connection);

        try {
            // Checking validity of credentials
            User utente = utenteDAO.checkLogin(username, password);

            if(utente == null){
                path = "/index.html";
                ctx.setVariable("errorMsg", "Username o password errati");
                templateEngine.process(path, ctx, resp.getWriter());
            }else{
            	
                path = req.getContextPath() + "/GoToHomePage";
                req.getSession().setMaxInactiveInterval(300);
                req.getSession().setAttribute("utente", utente);
                
                Stack<String> pageStack = new Stack<>();
                req.getSession().setAttribute("pageStack", pageStack);
                
                resp.sendRedirect(path);
            }
        } catch (SQLException e) {
            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Impossibile validare le cerdenziali");
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