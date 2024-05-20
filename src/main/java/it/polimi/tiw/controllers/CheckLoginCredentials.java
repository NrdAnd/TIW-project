package it.polimi.tiw.controllers;

import it.polimi.tiw.beans.User;
import it.polimi.tiw.dao.UserDAO;
import it.polimi.tiw.utils.ConnectionHandler;

import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedList;
import java.util.Queue;

@WebServlet("/CheckLoginCredentials")
@MultipartConfig
public class CheckLoginCredentials extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private Connection connection = null;

    public CheckLoginCredentials() {
        super();
    }

    @Override
    public void init() throws ServletException {
        connection = ConnectionHandler.getConnection(getServletContext());
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        // Getting and checking the credentials
        String username = req.getParameter("username");
        String password = req.getParameter("password");

        resp.setContentType("text/plain");

        if(username == null || password == null || username.isEmpty() || password.isEmpty()){
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().println("Credenziali vuote o mancanti");
            return;
        }

        UserDAO utenteDAO = new UserDAO(connection);

        User utente;
        try {
            // Checking validity of credentials
            utente = utenteDAO.checkLogin(username, password);
        } catch (SQLException e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().println("Impossibile validare le credenziali");
            return;
        }

        if(utente == null){
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            resp.getWriter().println("Username o password errati");
            return;
        }else{
            req.getSession().setMaxInactiveInterval(300);
            req.getSession().setAttribute("utente", utente);

            resp.setStatus(HttpServletResponse.SC_OK);
            resp.setContentType("application/json");
            resp.setCharacterEncoding("UTF-8");
            resp.getWriter().println(utente.getUsername());
            
            Queue<String> versionQueue = new LinkedList<>();
            req.getSession().setAttribute("versionQueue", versionQueue);
            return;
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
