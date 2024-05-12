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

@WebServlet("/CreateDocument") // Filtered
public class CreateDocument extends HttpServlet {
	private static final long serialVersionUID = 1L;
	private Connection connection = null;

	public CreateDocument() {
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

		Integer destinationID;
		String newDocumentName, summary, documentType;
		try {
			destinationID = Integer.parseInt(req.getParameter("destinationID"));
		} catch (NumberFormatException | NullPointerException e) {
			resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().println("Errore: DestinationID non valido");
            return;
		}

		try {
			newDocumentName = req.getParameter("newDocumentName");
		} catch (IllegalArgumentException | NullPointerException e) {
			resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().println("Errore: New Document Name non valido");
            return;
		}
		
		try {
			summary = req.getParameter("summary");
		} catch (IllegalArgumentException | NullPointerException e) {
			resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().println("Errore: Summary non valido");
            return;
		}
		
		try {
			documentType = req.getParameter("documentType");
		} catch (IllegalArgumentException | NullPointerException e) {
			resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().println("Errore: Document Type non valido");
            return;
		}
		
		
		User utente = (User) session.getAttribute("utente");
		DocumentDAO documentDao = new DocumentDAO(connection);

		int code;
		try {

			code = documentDao.createDocument(utente.getUserID(), newDocumentName, summary, documentType, destinationID);
			
			if (code != 1) {
				resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
	            resp.getWriter().println("Errore SQL: impossibile effettuare il salvataggio in rubrica");
			}
		} catch (SQLException e) {
			resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().println("Errore SQL: impossibile effettuare il salvataggio del documento nel DB");
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
