package it.polimi.tiw.dao;

import it.polimi.tiw.utils.*;
import it.polimi.tiw.beans.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.lang.Integer;

public class FolderDAO {

	private final Connection connection;

	public FolderDAO(Connection connection) {
		this.connection = connection;
	}

	/**
	 * This method creates and returns the folderTree
	 * 
	 * @param userID is the user ID
	 * @return the folderTree
	 * @throws SQLException if there's an SQL Exception
	 */
	public TreeNode getFolderTree(int userID) throws SQLException {

		String query = "SELECT folder_id, folder_name, creation_date, parent_folder_id, is_root, depth FROM Folder WHERE owner_id = ? ORDER BY depth ASC";
		PreparedStatement statement = connection.prepareStatement(query);
		statement.setInt(1, userID);

		ResultSet result = statement.executeQuery();

		result.next();

		Folder initialFolder = new Folder();
		initialFolder.setFolderID(result.getInt("folder_id"));
		initialFolder.setOwnerID(userID);
		initialFolder.setFolderName(result.getString("folder_name"));
		initialFolder.setCreationDate(result.getTimestamp("creation_date"));
		initialFolder.setParentFolderID(result.getInt("parent_folder_id"));
		initialFolder.setRoot(result.getBoolean("is_root"));
		initialFolder.setDepth(result.getInt("depth"));

		ArrayList<Folder> allFolders = new ArrayList<>();

		while (result.next()) {

			Folder folder = new Folder();
			folder.setFolderID(result.getInt("folder_id"));
			folder.setOwnerID(userID);
			folder.setFolderName(result.getString("folder_name"));
			folder.setCreationDate(result.getTimestamp("creation_date"));
			folder.setParentFolderID(result.getInt("parent_folder_id"));
			folder.setRoot(result.getBoolean("is_root"));
			folder.setDepth(result.getInt("depth"));

			allFolders.add(folder);

		}

		TreeNode rootNode = treeFolderCreation(initialFolder, allFolders);
		return rootNode;
	}

	/**
	 * This method permits to extract the subFolderTree of a specific Folder
	 * 
	 * @param userID   is the user ID
	 * @param folderID is the folder ID
	 * @return the subFolderTree
	 * @throws SQLException is there's an exception
	 */

	public TreeNode getSubTreeFolder(int userID, int folderID) throws SQLException {

		String query = "WITH RECURSIVE FolderHierarchy AS (SELECT * FROM Folder WHERE owner_id = ? AND folder_id = ? UNION ALL SELECT f.* FROM Folder f INNER JOIN FolderHierarchy fh ON f.parent_folder_id = fh.folder_id) SELECT * FROM FolderHierarchy ORDER BY depth ASC";

		PreparedStatement statement = connection.prepareStatement(query);
		statement.setInt(1, userID);
		statement.setInt(2, folderID);

		ResultSet result = statement.executeQuery();

		result.next();

		Folder initialFolder = new Folder();
		initialFolder.setFolderID(folderID);
		initialFolder.setOwnerID(userID);
		initialFolder.setFolderName(result.getString("folder_name"));
		initialFolder.setCreationDate(result.getTimestamp("creation_date"));
		initialFolder.setParentFolderID(result.getInt("parent_folder_id"));
		initialFolder.setRoot(result.getBoolean("is_root"));
		initialFolder.setDepth(result.getInt("depth"));

		ArrayList<Folder> allFolders = new ArrayList<>();

		while (result.next()) {

			Folder folder = new Folder();
			folder.setFolderID(result.getInt("folder_id"));
			folder.setOwnerID(userID);
			folder.setFolderName(result.getString("folder_name"));
			folder.setCreationDate(result.getTimestamp("creation_date"));
			folder.setParentFolderID(result.getInt("parent_folder_id"));
			folder.setRoot(result.getBoolean("is_root"));
			folder.setDepth(result.getInt("depth"));

			allFolders.add(folder);

		}

		TreeNode rootNode = treeFolderCreation(initialFolder, allFolders);
		return rootNode;

	}

	/**
	 * It's a private method used to create the tree, taking as input the root
	 * folder from which to create the folder tree
	 * 
	 * @param initialFolder is the root folder
	 * @param folderList    is the list that contains the other folders to add to
	 *                      the folder tree
	 * @return the folder tree
	 */
	private TreeNode treeFolderCreation(Folder initialFolder, ArrayList<Folder> folderList) {

		// Create the first Node of the tree: it contains the HomePageFolder
		TreeNode rootNode = new TreeNode(initialFolder);

		// Map to connect FolderID and a TreeNode with that FolderID
		HashMap<Integer, TreeNode> nodeMap = new HashMap<>();
		nodeMap.put(initialFolder.getFolderID(), rootNode);

		// Create tree
		for (int i = 0; i < folderList.size(); i++) {

			Folder folder = folderList.get(i);

			TreeNode node = new TreeNode(folder);
			nodeMap.put(folder.getFolderID(), node);

			TreeNode parentNode = nodeMap.get(folder.getParentFolderID());

			parentNode.addChild(node);

		}

		return rootNode;

	}

	/**
	 * This method permits to find the correct Folder by its ID
	 * 
	 * @param userID   is the user ID
	 * @param folderID is the folder ID
	 * @return the correct folder object
	 * @throws SQLException if there's an SQL Exception
	 */
	public Folder findFolderByID(int userID, int folderID) throws SQLException {

		String query = "SELECT folder_name, creation_date, parent_folder_id, is_root, depth FROM Folder WHERE owner_id = ? AND folder_id = ? ORDER BY depth ASC";
		PreparedStatement statement = connection.prepareStatement(query);
		statement.setInt(1, userID);
		statement.setInt(2, folderID);

		ResultSet result = statement.executeQuery();

		// Test if there isn't any value in the table
		if (!result.isBeforeFirst())
			return null;

		result.next();
		Folder folder = new Folder();
		folder.setFolderID(folderID);
		folder.setOwnerID(userID);
		folder.setFolderName(result.getString("folder_name"));
		folder.setCreationDate(result.getTimestamp("creation_date"));
		folder.setParentFolderID(result.getInt("parent_folder_id"));
		folder.setRoot(result.getBoolean("is_root"));
		folder.setDepth(result.getInt("depth"));

		return folder;

	}

	/**
	 * This method creates and return the list of subfolders of a specific folder
	 * 
	 * @param userID   is the user ID
	 * @param folderID is the folder ID
	 * @return the list of the subfolders
	 */

	public ArrayList<Folder> getSubFoldersByFolderID(int userID, int parentFolderID) throws SQLException {

		String query = "SELECT folder_id, folder_name, creation_date, is_root, depth FROM Folder WHERE owner_id = ? AND parent_folder_id = ?";
		PreparedStatement statement = connection.prepareStatement(query);
		statement.setInt(1, userID);
		statement.setInt(2, parentFolderID);

		ResultSet result = statement.executeQuery();

		ArrayList<Folder> subFolders = new ArrayList<>();

		while (result.next()) {

			Folder folder = new Folder();
			folder.setFolderID(result.getInt("folder_id"));
			folder.setOwnerID(userID);
			folder.setFolderName(result.getString("folder_name"));
			folder.setCreationDate(result.getTimestamp("creation_date"));
			folder.setParentFolderID(parentFolderID);
			folder.setRoot(result.getBoolean("is_root"));
			folder.setDepth(result.getInt("depth"));

			subFolders.add(folder);
		}

		return subFolders;

	}

	/**
	 * This method create a new Folder in the Database
	 * 
	 * @param userID         is the user ID
	 * @param folderName     is the folder Name
	 * @param creationDate   is the creation Date
	 * @param parentFolderID is the ID of the parent folder of the current new
	 *                       folder
	 * @param isRoot         it permits to indicate if the folder is root or not
	 * @param depth          is the parameters that represents the depth of the
	 *                       current folder
	 * @return a code, that it is equal to 1 if the query was successfull
	 * @throws SQLException
	 */

	public int createFolder(int userID, String folderName, int parentFolderID, boolean isRoot, int depth)
			throws SQLException {

		String query = "INSERT INTO Folder(owner_id, folder_name, parent_folder_id, is_root, depth) VALUES (?,?,?,?,?)";
		PreparedStatement statement = connection.prepareStatement(query);
		statement.setInt(1, userID);
		statement.setString(2, folderName);
		statement.setInt(3, parentFolderID);
		statement.setBoolean(4, isRoot);
		statement.setInt(5, depth);

		int code = statement.executeUpdate();

		if (code == 0)
			throw new SQLException("Registration failed, no rows affected");

		return code;

	}

	/**
	 * This method permits to understand if a folder is a root folder or not
	 * 
	 * @param userID   is the user ID
	 * @param folderID is the folder ID
	 * @return an int value with these rules:
	 * 
	 *         -> -1 : if there isn't any value in the table -> 0 : if the folder is
	 *         not a root folder -> 1 : if the folder is a root folder
	 * 
	 * @throws SQLException is there is a SQLException
	 */
	public int folderIsRoot(int userID, int folderID) throws SQLException {

		String query = "SELECT is_root FROM Folder WHERE owner_id = ? AND folder_id = ?";
		PreparedStatement statement = connection.prepareStatement(query);
		statement.setInt(1, userID);
		statement.setInt(2, folderID);

		ResultSet result = statement.executeQuery();

		// Test if there isn't any value in the table
		if (!result.isBeforeFirst())
			return -1;

		result.next();

		if (result.getBoolean("is_root") == true) {
			return 1;
		} else {
			return 0;
		}
	}

	/**
	 * This method returns the fatherID of the current Folder
	 * 
	 * @return an int value with these rules:
	 * 
	 *         -> -1 : if there isn't any value in the table -> the effective
	 *         folderFatherID is it exists
	 * 
	 * @throws SQLException is there is a SQLException
	 */
	public int getFolderFatherID(int userID, int folderID) throws SQLException {

		String query = "SELECT parent_folder_id FROM Folder WHERE owner_id = ? AND folder_id = ?";
		PreparedStatement statement = connection.prepareStatement(query);
		statement.setInt(1, userID);
		statement.setInt(2, folderID);

		ResultSet result = statement.executeQuery();
		// Test if there isn't any value in the table
		if (!result.isBeforeFirst())
			return -1;

		result.next();

		return result.getInt("parent_folder_id");
	}

	/**
	 * This method returns the fatherID of the current Folder
	 * 
	 * @return an int value with these rules:
	 * 
	 *         -> -1 : if there isn't any value in the table -> the effective
	 *         folderFatherID is it exists
	 * 
	 * @throws SQLException is there is a SQLException
	 */
	public int getDepthByID(int userID, int folderID) throws SQLException {
		String query = "SELECT depth FROM Folder WHERE owner_id = ? AND folder_id = ?";
		PreparedStatement statement = connection.prepareStatement(query);
		statement.setInt(1, userID);
		statement.setInt(2, folderID);

		ResultSet result = statement.executeQuery();
		// Test if there isn't any value in the table
		if (!result.isBeforeFirst())
			return -1;
		result.next();

		return result.getInt("depth");
	}

	/**
	 * This method create the HomePageFolder for the user, that it is a special
	 * folder which will contain all the other user's folders
	 * 
	 * @param userID       is the user ID
	 * @param creationDate is the creation Date
	 * @param depth        is the parameters that represents the depth of the
	 *                     current folder
	 * @return a code, that it is equal to 1 if the query was successfull
	 * @throws SQLException
	 */

	public int createHomePageFolder(int userID) throws SQLException {

		String query = "INSERT INTO Folder(owner_id, folder_name, depth) VALUES (?,?,?)";
		PreparedStatement statement = connection.prepareStatement(query);
		statement.setInt(1, userID);
		statement.setString(2, "Homepage");
		statement.setInt(3, 0);

		int code = statement.executeUpdate();

		if (code == 0)
			throw new SQLException("Registration failed, no rows affected");

		return code;

	}

	/**
	 * This method returns the arrayList of all folders that are contained in the
	 * User's DB
	 * 
	 * @param userID is the user ID
	 * @return an arrayList contains all folders inside the User's DB
	 * @throws SQLException if there's an exception
	 */
	public ArrayList<Folder> getAllFolders(int userID) throws SQLException {

		ArrayList<Folder> folderList = new ArrayList<>();

		String query = "SELECT * FROM Folder WHERE owner_id = ?";
		PreparedStatement statement = connection.prepareStatement(query);
		statement.setInt(1, userID);

		ResultSet result = statement.executeQuery();

		if (!result.isBeforeFirst())
			return null;

		while (result.next()) {

			Folder folder = new Folder();
			folder.setFolderID(result.getInt("folder_id"));
			folder.setOwnerID(userID);
			folder.setFolderName(result.getString("folder_name"));
			folder.setCreationDate(result.getTimestamp("creation_date"));
			folder.setParentFolderID(result.getInt("parent_folder_id"));
			folder.setRoot(result.getBoolean("is_root"));
			folder.setDepth(result.getInt("depth"));

			folderList.add(folder);
		}
		return folderList;
	}

	/**
	 * 
	 * @param userID   is the user ID
	 * @param folderID is the folder ID
	 * @return the name of the Folder
	 * @throws SQLException if there is an exception
	 */

	public String getFolderName(int userID, int folderID) throws SQLException {
		String query = "SELECT folder_name FROM Folder WHERE owner_id = ? AND folder_id = ?";
		PreparedStatement statement = connection.prepareStatement(query);
		statement.setInt(1, userID);
		statement.setInt(2, folderID);

		ResultSet result = statement.executeQuery();

		if (!result.isBeforeFirst())
			return null;

		result.next();

		return result.getString("folder_name");
	}

	/**
	 * This method deletes a specific folder with its subfolders and its documents
	 * 
	 * @param userID   is the user ID
	 * @param folderID is the folder ID
	 * @throws SQLException if ther's an exception
	 */

	public void deleteFolder(int userID, int folderID) throws SQLException {

		String folderDelete = "WITH RECURSIVE FolderHierarchy AS (SELECT folder_id FROM Folder WHERE owner_id = ? AND folder_id = ? UNION ALL SELECT f.folder_id FROM Folder f INNER JOIN FolderHierarchy fh ON f.parent_folder_id = fh.folder_id) DELETE FROM Folder WHERE folder_id IN (SELECT folder_id FROM FolderHierarchy) ORDER BY folder_id DESC";
		String docDelete = "WITH RECURSIVE FolderHierarchy AS (SELECT * FROM Folder WHERE owner_id = ? AND folder_id = ? UNION ALL SELECT f.* FROM Folder f INNER JOIN FolderHierarchy fh ON f.parent_folder_id = fh.folder_id) DELETE FROM Document WHERE folder_id IN (SELECT folder_id FROM FolderHierarchy);";

		connection.setAutoCommit(false);

		try {
			PreparedStatement documentStatement = connection.prepareStatement(docDelete);
			documentStatement.setInt(1, userID);
			documentStatement.setInt(2, folderID);

			PreparedStatement folderStatement = connection.prepareStatement(folderDelete);
			folderStatement.setInt(1, userID);
			folderStatement.setInt(2, folderID);

			documentStatement.executeUpdate();
			folderStatement.executeUpdate();
			connection.commit();
		} catch (SQLException e) {
			connection.rollback();
			throw e;
		} finally {
			connection.setAutoCommit(true);
		}
	}

	/**
	 * This method returns the maximum folder id
	 * 
	 * @param userID is the user id
	 * @return the maximum folder id, otherwhise if the result is empty it returns
	 *         -1
	 * @throws SQLException if there's an exception
	 */

	public int getLastFolderID(int userID) throws SQLException {

		String query = "SELECT MAX(folder_id) AS max_folder_id FROM Folder WHERE owner_id = ?";
		PreparedStatement statement = connection.prepareStatement(query);
		statement.setInt(1, userID);
		ResultSet result = statement.executeQuery();

		if (!result.isBeforeFirst())
			return -1;

		result.next();

		return result.getInt("max_folder_id");

	}

	/**
	 * This method extracts the parent folder name by the folder id
	 * 
	 * @param userID   is the user id
	 * @param folderId is the folder id
	 * @return the folder id if it exists, otherwhise null
	 */
	public String getParentFolderName(int userID, int folderId) throws SQLException {

		String query = "SELECT parent_folder_id FROM Folder WHERE owner_id = ? AND folder_id = ?";
		PreparedStatement statement = connection.prepareStatement(query);
		statement.setInt(1, userID);
		statement.setInt(2, folderId);
		ResultSet result = statement.executeQuery();

		if (!result.isBeforeFirst())
			return null;

		result.next();

		return this.getFolderName(userID, result.getInt("parent_folder_id"));

	}

	
 
	/**
	 * This method take the datas before deleting a specific folder with its
	 * subfolders and its documents
	 * 
	 * @param userID   is the user ID
	 * @param folderID is the folder ID
	 * @throws SQLException if ther's an exception
	 */

	/*public ArrayList<Object> takeDatasBeforeDelete(int userID, int folderID) throws SQLException {

		String folderDelete = "WITH RECURSIVE FolderHierarchy AS (SELECT * FROM Folder WHERE owner_id = ? AND folder_id = ? UNION ALL SELECT f.* FROM Folder f INNER JOIN FolderHierarchy fh ON f.parent_folder_id = fh.folder_id) SELECT FROM Folder WHERE folder_id IN (SELECT folder_id FROM FolderHierarchy) ORDER BY folder_id DESC";
		String docDelete = "WITH RECURSIVE FolderHierarchy AS (SELECT * FROM Folder WHERE owner_id = ? AND folder_id = ? UNION ALL SELECT f.* FROM Folder f INNER JOIN FolderHierarchy fh ON f.parent_folder_id = fh.folder_id) SELECT FROM Document WHERE folder_id IN (SELECT folder_id FROM FolderHierarchy);";

		PreparedStatement documentStatement = connection.prepareStatement(docDelete);
		documentStatement.setInt(1, userID);
		documentStatement.setInt(2, folderID);

		PreparedStatement folderStatement = connection.prepareStatement(folderDelete);
		folderStatement.setInt(1, userID);
		folderStatement.setInt(2, folderID);

		ResultSet documentResult = documentStatement.executeQuery();
		ResultSet folderResult = folderStatement.executeQuery();
		
		if (!documentResult.isBeforeFirst() && !folderResult.isBeforeFirst())
			return null;
		
		ArrayList<Object> docAndFolderList = new ArrayList<>();

		while (documentResult.next()) {

			Document document = new Document();
			document.setDocumentID(documentResult.getInt("document_id"));
			document.setOwnerID(userID);
			document.setDocumentName(documentResult.getString("folder_name"));
			document.setCreationDate(documentResult.getTimestamp("creation_date"));
			document.setDocumentType(documentResult.getString("document_type"));
			document.setSummary(documentResult.getString("summary"));
			document.setFolderID(documentResult.getInt("folder_id"));
			
			docAndFolderList.add(document);
			
		}
		
		
		while (folderResult.next()) {

			Folder folder = new Folder();
			folder.setFolderID(folderResult.getInt("folder_id"));
			folder.setOwnerID(userID);
			folder.setFolderName(folderResult.getString("folder_name"));
			folder.setCreationDate(folderResult.getTimestamp("creation_date"));
			folder.setParentFolderID(folderResult.getInt("parent_folder_id"));
			folder.setRoot(folderResult.getBoolean("is_root"));
			folder.setDepth(folderResult.getInt("depth"));

			docAndFolderList.add(folder);
		}
		
		return docAndFolderList;
	}
	
	*/

}
