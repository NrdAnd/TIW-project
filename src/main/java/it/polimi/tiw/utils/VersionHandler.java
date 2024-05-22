package it.polimi.tiw.utils;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.Stack;

import javax.servlet.http.HttpServletResponse;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;

import it.polimi.tiw.beans.Document;
import it.polimi.tiw.beans.Folder;
import it.polimi.tiw.dao.DocumentDAO;
import it.polimi.tiw.dao.FolderDAO;

public class VersionHandler {

	private static Connection connection = null;
	private static final Gson gson = new Gson();

	private static void initDB() {

		try {
			String url = "jdbc:mysql://localhost:3306/REDACTED_DATABASE";
			String username = "root";
			String password = "REDACTED_DATABASE_PASSWORD";
			connection = DriverManager.getConnection(url, username, password);

		} catch (SQLException e) {
			e.printStackTrace();
			// return;
		}
	}

	private static void closeConnection() {

		if (connection != null) {
			try {
				connection.close();
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
	}

	public static void operationStringDeparsing(int userID, HttpServletResponse resp, String privateOperationString) {

		String[] stringVector = privateOperationString.split("_");

		switch (stringVector[0]) {

		case "CD": {

			int documentID = Integer.parseInt(stringVector[1]);
			deleteDocument(userID, resp, documentID);

			break;
		}

		case "CF": {

			int folderID = Integer.parseInt(stringVector[1]);
			deleteFolder(userID, resp, folderID);

			break;

		}

		case "DF": {

			int folderID = Integer.parseInt(stringVector[1]);
			createFolder(userID, resp, folderID);

			break;
		}

		case "DD": {

			int documentID = Integer.parseInt(stringVector[1]);
			createDocument(userID, resp, documentID);

			break;
		}

		case "MD": {

			int documentID = Integer.parseInt(stringVector[1]);
			int initialFolderID = Integer.parseInt(stringVector[2]);
			int postFolderID = Integer.parseInt(stringVector[3]);
			moveDocument(userID, resp, documentID, initialFolderID, postFolderID);

			break;
		}

		}

	}

	/**
	 * 
	 * @param userID
	 * @param resp
	 * @param dataID
	 * @return
	 */
	public static HashMap<Integer, TreeNode> extractDeletionDatas(int userID, HttpServletResponse resp, int dataID) {

		HashMap<Integer, TreeNode> datasMap = new HashMap<Integer, TreeNode>();
		final String filePath = "REDACTED_HOME/git/TIW_Project_2024_RIA/src/main/java/it/polimi/tiw/utils/SaveDatas_ID_"
				+ userID + ".json";

		Gson gson = new Gson();
		Type mapType = new TypeToken<HashMap<Integer, TreeNode>>() {
		}.getType();

		// Legge i dati dal file JSON
		try (FileReader reader = new FileReader(filePath)) {
			datasMap = gson.fromJson(reader, mapType);
		} catch (IOException e) {
			e.printStackTrace();
		}

		return datasMap;

	}

	/**
	 * 
	 * @param userID
	 * @param resp
	 * @param dataID
	 * @param optionValue is 1 if the dataID is a folderID, otherwhise 0 if it's a
	 *                    documentID
	 */
	public static void saveDeletionDatas(int userID, HttpServletResponse resp, int dataID, int optionValue) {

		initDB();
		FolderDAO folderDao = new FolderDAO(connection);
		DocumentDAO documentDao = new DocumentDAO(connection);
		final String filePath = "REDACTED_HOME/git/TIW_Project_2024_RIA/src/main/java/it/polimi/tiw/utils/SaveDatas_ID_"
				+ userID + ".json";
		HashMap<Integer, TreeNode> dataMap = extractDeletionDatas(userID, resp, dataID);
		
		if(dataMap == null) {
			dataMap = new HashMap<Integer, TreeNode>();
		}
		
		Gson gson = new GsonBuilder().setPrettyPrinting().create();

		if (optionValue == 1) {

			TreeNode folderTree = null;
			try {
				folderTree = folderDao.getSubTreeFolder(userID, dataID);
			} catch (SQLException e) {
				resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
				try {
					resp.getWriter().println("SQL error: impossibile ricavare l'albero di cartelle");
				} catch (Exception ex) {
					closeConnection();
					ex.printStackTrace();
					return;
				}
				closeConnection();
				return;
			}

			Stack<TreeNode> stack = new Stack<>();
			stack.push(folderTree);
			while (!stack.isEmpty()) {

				TreeNode currentNode = stack.pop();

				if (currentNode.getFolder().getDepth() > 0) {

					try {

						ArrayList<Document> documentList = documentDao.getAllDocuments(userID,
								currentNode.getFolder().getFolderID());
						if (documentList != null) {
							currentNode.setDocumentList(documentList);
						}

					} catch (SQLException ex) {
						resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
						try {
							resp.getWriter().println("SQL error: impossibile ricavare l'albero di cartelle");
						} catch (Exception e) {
							closeConnection();
							e.printStackTrace();
							return;
						}
						closeConnection();
						return;

					}
				}
				if (currentNode.getChildren().size() > 0) {
					for (int i = currentNode.getChildren().size() - 1; i >= 0; i--) {
						TreeNode node = currentNode.getChildren().get(i);
						stack.push(node);
					}
				}
			}

			dataMap.put(dataID, folderTree);

			try (FileWriter writer = new FileWriter(filePath)) {
				gson.toJson(dataMap, writer);
			} catch (IOException e) {
				closeConnection();
				e.printStackTrace();
				return;
			}

			closeConnection();
			return;

		} else {

			ArrayList<Document> document = new ArrayList<>();
			TreeNode node = new TreeNode(null);

			try {
				
				document.add(documentDao.takeDatasBeforeDelete(userID, dataID));
				node.setDocumentList(document);

			} catch (SQLException e) {
				resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
				try {
					resp.getWriter().println("Errore SQL: impossibile eseguire la query di eliminazione del documento");
				} catch (IOException ex) {
					ex.printStackTrace();
					closeConnection();
					return;
				}
				closeConnection();
				return;
			}

			dataMap.put(dataID, node);

			try (FileWriter writer = new FileWriter(filePath)) {
				gson.toJson(dataMap, writer);
			} catch (IOException e) {
				e.printStackTrace();
				closeConnection();
				return;
			}

			closeConnection();
			return;

		}

	}

	/**
	 * 
	 * @param userID
	 * @param resp
	 * @param documentID
	 */
	private static void deleteDocument(int userID, HttpServletResponse resp, int documentID) {

		initDB();
		DocumentDAO documentDao = new DocumentDAO(connection);

		try {

			documentDao.deleteDocument(userID, documentID);

		} catch (SQLException e) {

			resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			try {
				resp.getWriter().println("Errore SQL: impossibile eseguire la query di eliminazione del documento");
			} catch (IOException ex) {
				ex.printStackTrace();
			}

			closeConnection();
			return;
		}
		
		closeConnection();
	}

	/**
	 * 
	 * @param userID
	 * @param resp
	 * @param folderID
	 */
	private static void deleteFolder(int userID, HttpServletResponse resp, int folderID) {

		initDB();
		FolderDAO folderDao = new FolderDAO(connection);

		try {

			folderDao.deleteFolder(userID, folderID);

		} catch (SQLException e) {

			resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			try {
				resp.getWriter().println("Errore SQL: impossibile eseguire la query di eliminazione della folder");
			} catch (IOException ex) {
				ex.printStackTrace();
			}

			closeConnection();
			return;
		}

		closeConnection();

	}

	/**
	 * 
	 * @param userID
	 * @param resp
	 * @param documentID
	 * @param initialFolderID
	 * @param postFolderID
	 */
	private static void moveDocument(int userID, HttpServletResponse resp, int documentID, int initialFolderID,
			int postFolderID) {

		initDB();
		DocumentDAO documentDao = new DocumentDAO(connection);

		try {

			documentDao.updateDocumentPosition(documentID, initialFolderID, userID);

		} catch (SQLException e) {

			resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			try {
				resp.getWriter().println("Errore SQL: impossibile eseguire la query di spostamento del documento");
			} catch (IOException ex) {
				ex.printStackTrace();
			}

			closeConnection();
			return;
		}

		closeConnection();

	}

	/**
	 * 
	 * @param userID
	 * @param resp
	 * @param folderID
	 */
	private static void createFolder(int userID, HttpServletResponse resp, int folderID) {

		initDB();
		FolderDAO folderDao = new FolderDAO(connection);
		DocumentDAO documentDao = new DocumentDAO(connection);
		HashMap<Integer, TreeNode> dataMap = extractDeletionDatas(userID, resp, folderID);
		final String filePath = "REDACTED_HOME/git/TIW_Project_2024_RIA/src/main/java/it/polimi/tiw/utils/SaveDatas_ID_"
				+ userID + ".json";

		
		TreeNode root = dataMap.get(folderID);

		if (root == null) {
			resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			try {
				resp.getWriter().println("Errore: mappa estratta dal file di salvataggio nulla");
			} catch (IOException ex) {
				ex.printStackTrace();
			}

			closeConnection();
			return;
		}

		Queue<TreeNode> queue = new ArrayDeque<>();
		queue.add(root);

		while (!queue.isEmpty()) {
			
			TreeNode current = queue.poll();
			Folder folder = current.getFolder();
			
			try {
				folderDao.createFolder(userID, folder.getFolderName(), folder.getParentFolderID(), folder.isRoot(), folder.getDepth(), current.getFolder().getFolderID());
			} catch (SQLException e) {
				
				e.printStackTrace();
				resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
				try {
					resp.getWriter().println("Errore SQL: impossibile eseguire la query di ri-creazione della cartella");
				} catch (IOException ex) {
					ex.printStackTrace();
				}

				closeConnection();
				return;
			}
		
			
			for (Document document : current.getDocumentList()) {
				try {
					documentDao.createDocument(userID, document.getDocumentName(), document.getSummary(), document.getDocumentType(), document.getFolderID());
				} catch (SQLException e) {
					resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
					try {
						resp.getWriter().println("Errore SQL: impossibile eseguire la query di ri-creazione del documento");
					} catch (IOException ex) {
						ex.printStackTrace();
					}

					closeConnection();
					return;
				}
			}

			for (TreeNode child : current.getChildren()) {
				queue.add(child);
			}
		}
		
		dataMap.remove(folderID);
		try (FileWriter writer = new FileWriter(filePath)) {
			gson.toJson(dataMap, writer);
		} catch (IOException e) {
			closeConnection();
			e.printStackTrace();
			return;
		}
		
		closeConnection();
	}


	/**
	 * 
	 * @param userID
	 * @param resp
	 * @param documentID
	 */
	private static void createDocument(int userID, HttpServletResponse resp, int documentID) {

		initDB();
		DocumentDAO documentDao = new DocumentDAO(connection);
		HashMap<Integer, TreeNode> dataMap = extractDeletionDatas(userID, resp, documentID);
		final String filePath = "REDACTED_HOME/git/TIW_Project_2024_RIA/src/main/java/it/polimi/tiw/utils/SaveDatas_ID_"
				+ userID + ".json";

		
		TreeNode root = dataMap.get(documentID);

		if (root == null) {
			resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			try {
				resp.getWriter().println("Errore: mappa estratta dal file di salvataggio nulla");
			} catch (IOException ex) {
				ex.printStackTrace();
			}

			closeConnection();
			return;
		}
		
		Document document = root.getDocumentList().get(0);
		
		try {
			documentDao.createDocument(userID, document.getDocumentName(), document.getSummary(), document.getDocumentType(), document.getFolderID());
		} catch (SQLException e) {
			
			resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			try {
				resp.getWriter().println("Errore SQL: impossibile eseguire la query di ri-creazione del documento");
			} catch (IOException ex) {
				ex.printStackTrace();
			}

			closeConnection();
			return;
		}
		
		dataMap.remove(documentID);
		try (FileWriter writer = new FileWriter(filePath)) {
			gson.toJson(dataMap, writer);
		} catch (IOException e) {
			closeConnection();
			e.printStackTrace();
			return;
		}
		
		closeConnection();
		
	}

}
