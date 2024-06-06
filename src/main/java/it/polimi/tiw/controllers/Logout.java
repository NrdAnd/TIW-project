package it.polimi.tiw.controllers;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import it.polimi.tiw.beans.User;

import java.io.File;
import java.io.IOException;

@WebServlet("/Logout")
public class Logout extends HttpServlet {
	private static final long serialVersionUID = 1L;

	public Logout() {
		super();
	}

	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {

		HttpSession session = req.getSession();
		User utente = (User) session.getAttribute("utente");

		try {
			String filePath = "REDACTED_HOME/git/TIW_Project_2024_RIA/src/main/java/it/polimi/tiw/utils/SaveDatas_ID_" + utente.getUserID() + ".json";
			File saveDatasFile = new File(filePath);
			saveDatasFile.delete();
		} catch (Exception e) {
			//e.printStackTrace();
		}
		
		session = req.getSession(false);

		if (session != null) {
			session.invalidate();
		}

		String path = getServletContext().getContextPath() + "/index.html";
		resp.sendRedirect(path);
	}

	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
		doGet(request, response);
	}

}
