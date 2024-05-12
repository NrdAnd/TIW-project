package it.polimi.tiw.controllers;

import it.polimi.tiw.beans.Document;
import it.polimi.tiw.beans.User;
import it.polimi.tiw.dao.DocumentDAO;
import it.polimi.tiw.utils.ConnectionHandler;

import com.google.gson.Gson;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;


@WebServlet("/OpenDocument") // Filtered
public class OpenDocument extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private Connection connection = null;

    public OpenDocument() {
        super();
    }

    @Override
    public void init() throws ServletException {
        connection = ConnectionHandler.getConnection(getServletContext());
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
    	
        HttpSession session = req.getSession();
        resp.setContentType("text/plain");
                
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
        
        try { 	
        	document = documentDao.findDocumentByID(utente.getUserID(), documentID);
        } catch (SQLException | NullPointerException e) {
        	resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().println("Errore SQL: impossibile estrarre le informazioni del documento");
            return;
        }
        
        Gson gson = new Gson();
        String json = gson.toJson(document);
        
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        resp.getWriter().write(json);    
  
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
