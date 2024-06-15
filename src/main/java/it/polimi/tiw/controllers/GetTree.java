package it.polimi.tiw.controllers;

import it.polimi.tiw.beans.Document;
import it.polimi.tiw.beans.User;
import it.polimi.tiw.dao.DocumentDAO;
import it.polimi.tiw.dao.FolderDAO;

import it.polimi.tiw.utils.ConnectionHandler;
import it.polimi.tiw.utils.TreeNode;

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
import java.util.ArrayList;
import java.util.Stack;


@WebServlet("/GetTree") // Filtered
public class GetTree extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private Connection connection = null;

    public GetTree() {
        super();
    }

    @Override
    public void init() throws ServletException {
        connection = ConnectionHandler.getConnection(getServletContext());
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
    	
        HttpSession session = req.getSession();
        User utente = (User) session.getAttribute("utente");
        
        //System.out.println(utente.getUserID());
        resp.setContentType("text/plain");
        
        FolderDAO folderDao = new FolderDAO(connection);
        DocumentDAO documentDao = new DocumentDAO(connection);
        
        TreeNode folderTree;
        try {
            folderTree = folderDao.getFolderTree(utente.getUserID());
        } catch (SQLException e) {
        	resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().println("SQL error: Impossible to fetch the Folder Tree");
            return;
        }
        
        Stack<TreeNode> stack = new Stack<>();
        stack.push(folderTree);
        while (!stack.isEmpty()) {
        	
            TreeNode currentNode = stack.pop();
           
            if (currentNode.getFolder().getDepth() > 0) {
            	
            	try {
                	
                	ArrayList<Document> documentList = documentDao.getAllDocuments(utente.getUserID(), currentNode.getFolder().getFolderID());
                	if(documentList!=null) {
                		currentNode.setDocumentList(documentList);
                	}
                	
                } catch (SQLException e) {
                	resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                    resp.getWriter().println("SQL error: Impossible to fetch the Folder Tree");
                    return;
                }
            }
            if (currentNode.getChildren().size() > 0) {
                for (int i = currentNode.getChildren().size()-1; i >=0; i--) {
                	TreeNode node = currentNode.getChildren().get(i);
                    stack.push(node);
                }
            }
        }
        
        Gson gson = new Gson();
        String json = gson.toJson(folderTree);
        
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
