package it.polimi.tiw.controllers;

import it.polimi.tiw.beans.User;
import it.polimi.tiw.dao.FolderDAO;
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
import java.util.regex.Pattern;

@WebServlet("/CheckSignupCredentials")
public class CheckSignupCredentials extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private Connection connection = null;
    private TemplateEngine templateEngine;

    public CheckSignupCredentials(){
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
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
    	
        String email = req.getParameter("email");
        String username = req.getParameter("username");
        String password = req.getParameter("password");
        String passwordCheck = req.getParameter("passwordCheck");

        String path;
        ServletContext servletContext = getServletContext();
        final WebContext ctx = new WebContext(req, resp, servletContext, req.getLocale());

        // checking credentials are not null or empty
        if(email == null || password == null || passwordCheck == null || username == null ||
            email.isEmpty() || password.isEmpty() || username.isEmpty()) {
            path = "/signup.html";
            ctx.setVariable("errorMsg", "Errore: Credenziali mancanti o nulle");
            templateEngine.process(path, ctx, resp.getWriter());
            return;
        }

        // Validate email
        Pattern emailPattern = Pattern.compile("^.+@.+\\..+$");
        if(!emailPattern.matcher(email).matches()){
            path = "/signup.html";
            ctx.setVariable("errorMsg", "Errore: email non valida");
            templateEngine.process(path, ctx, resp.getWriter());
            return;
        }

        boolean isDuplicate = false;
        boolean pswError = false;

        // check that the entered passwords match
        if(!passwordCheck.equals(password)) {
            pswError = true;
            ctx.setVariable("errorMsg", "Le password inserite non corrispondono!");
        }

        if(!pswError){
            UserDAO userDao = new UserDAO(connection);

            // checks the uniqueness of the username
            try {
                isDuplicate = userDao.checkRegister(username);
            } catch (SQLException e) {
                resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "SQL error: impossibile controllare unicità dello username");
            }

            if(isDuplicate){
                ctx.setVariable("errorMsg", "Lo username specificato è già in uso!");
            }else {
                // adds the user to the DB
                try {
                    userDao.registerUser(email, password, username);
                } catch (SQLException e) {
                    resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "SQL error: impossibile registrare l'utente");
                    return;
                }
                
                
                /*// redirects to the login page
                path = getServletContext().getContextPath() + "/index.html";
                resp.sendRedirect(path); */

                
                // alternatively, redirects to the registered user's HomePage (NOT TESTED)
                // asks the DB for the user to ensure that it was added correctly
                
                path = req.getContextPath() + "/GoToHomePage";
                try {
                    User utente = userDao.getUtenteByUsername(username);   
                    req.getSession().setAttribute("utente", utente);
                    
                    FolderDAO folderDao = new FolderDAO(connection);
                    try {
            			
            			int code;
            			code = folderDao.createHomePageFolder(utente.getUserID());
            			if (code != 1) {
            				resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "SQL error: query non andata a buon fine");
            				return;
            			}
            		} catch (SQLException e) {
            			resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "SQL error: query non andata a buon fine");
            			return;
            		}
                    
                    Stack<String> pageStack = new Stack<>();
                    req.getSession().setAttribute("pageStack", pageStack);
                    resp.sendRedirect(path);
                } catch (SQLException e) {
                    resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Errore SQL: impossibile ricavare l'utente richiesto");
                    return;
                }
            }
        }

        // error handling
        path = "/signup.html";
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