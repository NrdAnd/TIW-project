package it.polimi.tiw.controllers;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;


import it.polimi.tiw.beans.User;
import it.polimi.tiw.dao.DocumentDAO;
import it.polimi.tiw.utils.ConnectionHandler;


@WebServlet("/MoveDocument") // Filtered
public class MoveDocument extends HttpServlet {
	private static final long serialVersionUID = 1L;
	private Connection connection = null;
	
    public MoveDocument() {
        super();
    }

    @Override
    public void init() throws ServletException {
        connection = ConnectionHandler.getConnection(getServletContext());
    }
    
    @Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		
    	HttpSession session = req.getSession();
		resp.setContentType("text/plain");
		
		
		User utente = (User) session.getAttribute("utente");
		Integer destinationFolderID,documentID;
        try {
        	destinationFolderID = Integer.parseInt(req.getParameter("destinationFolderID"));
        }catch(NumberFormatException | NullPointerException e) {
        	resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().println("Errore: Destination Folder ID non valido");
            return;
        }
        try {
        	documentID = Integer.parseInt(req.getParameter("documentID"));
        }catch(NumberFormatException | NullPointerException e) {
        	resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().println("Errore: Document ID non valido");
            return;
        }
        
        DocumentDAO documentDAO = new DocumentDAO(connection);
        
        try {
            documentDAO.updateDocumentPosition(documentID, destinationFolderID,utente.getUserID()); 
        } catch (SQLException e) {
        	resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().println("Errore SQL: impossibile eseguire la update della posizione del documento nel DB");
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
