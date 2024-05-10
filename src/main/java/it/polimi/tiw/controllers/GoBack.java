package it.polimi.tiw.controllers;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Stack;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ServletContextTemplateResolver;

import it.polimi.tiw.beans.Folder;
import it.polimi.tiw.beans.User;
import it.polimi.tiw.dao.DocumentDAO;
import it.polimi.tiw.dao.FolderDAO;
import it.polimi.tiw.utils.ConnectionHandler;

@WebServlet("/GoBack") // Filtered
public class GoBack extends HttpServlet {
	private static final long serialVersionUID = 1L;
	private Connection connection = null;
	private TemplateEngine templateEngine;

	public GoBack() {
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

		Stack<String> pageStack;
		try {
			pageStack = (Stack<String>) session.getAttribute("pageStack");
		} catch (NullPointerException e) {
			resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "PageStack nullo");
			return;
		}

		if (!pageStack.isEmpty()) {

			// Questa pop serve a rimuovere dallo stack la pagina corrente, che può essere 
			// indifferentemnete un numero o una stringa 
			pageStack.pop();

			// controlla se lo stack è vuoto, se si si torna alla homepage
			if (!pageStack.isEmpty()) {

				String nextPage = pageStack.pop();

				// Controlla se la Stringa è composta da uno o più numeri.
				// Se si: si processa la sottocartella,
				// Se no: si torna alla homepage
				
				if (nextPage.matches("\\d+")) {

					int newFolderID = Integer.parseInt(nextPage);
					String url = req.getContextPath() + "/GoToFolder?folderID=" + newFolderID;
					session.setAttribute("pageStack", pageStack);
					resp.sendRedirect(url);

				} else {

					String url = req.getContextPath() + "/GoToHomePage";
					session.setAttribute("pageStack", pageStack);
					resp.sendRedirect(url);

				}

			} else {

				String url = req.getContextPath() + "/GoToHomePage";
				session.setAttribute("pageStack", pageStack);
				resp.sendRedirect(url);
			}

		} else {

			String url = req.getContextPath() + "/GoToHomePage";
			session.setAttribute("pageStack", pageStack);
			resp.sendRedirect(url);

		}

	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		doGet(req, resp);
	}

	@Override
	public void destroy() {
		try {
			ConnectionHandler.closeConnection(connection);
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
}
