package it.polimi.tiw.controllers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.tomcat.util.http.fileupload.FileItem;
import org.apache.tomcat.util.http.fileupload.disk.DiskFileItemFactory;
import org.apache.tomcat.util.http.fileupload.servlet.ServletFileUpload;
import org.apache.tomcat.util.http.fileupload.servlet.ServletRequestContext;

import it.polimi.tiw.beans.User;
import it.polimi.tiw.utils.VersionHandler;

@WebServlet("/SetRevertAction")
public class SetRevertAction extends HttpServlet {
	private static final long serialVersionUID = 1L;
       
    public SetRevertAction() {
        super();
    }

	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		
		HttpSession session = req.getSession();
		resp.setContentType("text/plain");

		Integer operationNumber = null;
		String contentType = req.getContentType();

		if (contentType != null && contentType.startsWith("multipart/form-data")) {

			DiskFileItemFactory factory = new DiskFileItemFactory();
			ServletFileUpload upload = new ServletFileUpload(factory);
			List<FileItem> items = null;

			try {

				ServletRequestContext requestContext = new ServletRequestContext(req);
				// Parses the multipart request data
				items = upload.parseRequest(requestContext);

			} catch (Exception e) {
				e.printStackTrace();
			}

			try {
				operationNumber = Integer.parseInt(items.get(0).getString());
			} catch (NumberFormatException | NullPointerException e) {
				resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
				resp.getWriter().println("Errore: Operation String non valida");
				return;
			}
		}
		
		User utente = (User) session.getAttribute("utente");
		ArrayList<String> privateVersionQueue = (ArrayList<String>) session.getAttribute("privateVersionQueue");
		ArrayList<String> versionQueue = (ArrayList<String>) session.getAttribute("versionQueue");
		
		VersionHandler.operationStringDeparsing(utente.getUserID(), resp, privateVersionQueue.get(operationNumber));
		
		
        privateVersionQueue.remove(operationNumber.intValue());
        versionQueue.remove(operationNumber.intValue());

        req.getSession().setAttribute("versionQueue", versionQueue);
        req.getSession().setAttribute("privateVersionQueue", privateVersionQueue);
        
	}

}
