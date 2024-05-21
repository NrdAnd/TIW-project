package it.polimi.tiw.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;


import it.polimi.tiw.beans.Document;
public class DocumentDAO {
    private final Connection connection;

    public DocumentDAO(Connection connection){
        this.connection = connection;
    }
    
	/**
	 * This method returns the fatherID of the current Folder
	 * @return an int value with these rules:
	 * 								
	 * 						->  -1 : if there isn't any value in the table
	 * 						->  the effective folderFatherID is it exists
	 * 
	 * @throws SQLException is there is a SQLException
	 */
    
    public int createDocument(int ownerID, String name, String summary, String type,int folderID) throws SQLException {
    	String query = "INSERT INTO Document (owner_id, document_name, summary, document_type, folder_id) VALUES (?, ?, ?, ?, ?)";
        PreparedStatement statement = connection.prepareStatement(query);
        statement.setInt(1, ownerID);
        statement.setString(2, name);
        statement.setString(3, summary);
        statement.setString(4, type);
        statement.setInt(5, folderID);
        
        int code = statement.executeUpdate();
               
        if(code == 0) throw new SQLException("Registration failed, no rows affected");

        return code;
    }
    
    public Document findDocumentByID(int user, int IDDoc) throws SQLException {
    	String query = "SELECT * FROM Document WHERE document_id = ? AND owner_id = ?";
    	PreparedStatement statement = connection.prepareStatement(query);
    	statement.setInt(1, IDDoc);
    	statement.setInt(2, user);
    	
    	ResultSet result = statement.executeQuery();
    	
        if(!result.isBeforeFirst())
            return null;
        
        result.next();
    	
        Document document = new Document();
        document.setDocumentID(result.getInt("document_id"));
        document.setFolderID(result.getInt("folder_id"));
        document.setDocumentName(result.getString("document_name"));
        document.setOwnerID(result.getInt("owner_id"));
        document.setSummary(result.getString("summary"));
        document.setCreationDate(result.getTimestamp("creation_date"));
        document.setDocumentType(result.getString("document_type"));
        
        return document;
    }
    
    
    public int getOriginFolder(int user, int docID) throws SQLException {
    	String query = "SELECT folder_id FROM Document WHERE document_id = ? AND owner_id = ?";
    	PreparedStatement statement = connection.prepareStatement(query);
    	statement.setInt(1, docID);
    	statement.setInt(2, user);
    	
    	ResultSet result = statement.executeQuery();
    	
    	if(!result.isBeforeFirst())
        	return -1;
    	
    	result.next();
    	return result.getInt("folder_id");
    	
    }
    
    public void updateDocumentPosition(int IDDoc, int destinationFolderID, int user) throws SQLException {
    	String query = "UPDATE Document SET folder_id = ? WHERE document_id = ? AND owner_id = ?";
    	PreparedStatement statement = connection.prepareStatement(query);
        statement.setInt(1, destinationFolderID);
        statement.setInt(2, IDDoc);
        statement.setInt(3, user);

        int rowsAffected = statement.executeUpdate();

        if (rowsAffected == 0) {
            throw new SQLException("No document with specified ID was found.");
        }
    }
    
    public ArrayList<Document> getAllDocuments(int ownerID, int folderID) throws SQLException {
        String query = "SELECT * FROM Document WHERE owner_id = ? AND folder_id = ?";
        PreparedStatement statement = connection.prepareStatement(query);
        statement.setInt(1, ownerID);
        statement.setInt(2, folderID);

        ResultSet resultSet = statement.executeQuery();
        if(!resultSet.isBeforeFirst()) {
        	return null;
        }
        
        ArrayList<Document> documents = new ArrayList<>();
        while (resultSet.next()) {
            Document document = new Document();
            document.setDocumentID(resultSet.getInt("document_id"));
            document.setOwnerID(resultSet.getInt("owner_id"));
            document.setDocumentName(resultSet.getString("document_name"));
            document.setSummary(resultSet.getString("summary"));
            document.setDocumentType(resultSet.getString("document_type"));
            document.setCreationDate(resultSet.getTimestamp("creation_date"));
            document.setFolderID(resultSet.getInt("folder_id"));
            documents.add(document);
        }

        return documents;
    }
    
    public ArrayList<Document> getUsersDocuments(int ownerID) throws SQLException {
        ArrayList<Document> documents = new ArrayList<>();
        
        String query = "SELECT * FROM Document WHERE owner_id = ?";
        PreparedStatement statement = connection.prepareStatement(query);
        statement.setInt(1, ownerID);

        ResultSet resultSet = statement.executeQuery();

        while (resultSet.next()) {
            Document document = new Document();
            document.setDocumentID(resultSet.getInt("document_id"));
            document.setOwnerID(resultSet.getInt("owner_id"));
            document.setDocumentName(resultSet.getString("document_name"));
            document.setSummary(resultSet.getString("summary"));
            document.setDocumentType(resultSet.getString("document_type"));
            document.setCreationDate(resultSet.getTimestamp("creation_date"));
            document.setFolderID(resultSet.getInt("folder_id"));
            documents.add(document);
        }

        return documents;
    }
    
   
    
    /**
     * This method deletes a specific document
     * @param userID is the user ID
     * @param documentID is the document ID
     * @throws SQLException if there's an exception
     */
    
    public void deleteDocument(int userID, int documentID) throws SQLException {
    	
    	String query = "DELETE FROM Document WHERE owner_id = ? AND document_id = ?";
    	PreparedStatement statement = connection.prepareStatement(query);
    	statement.setInt(1, userID);
		statement.setInt(2, documentID);
		
		statement.executeUpdate();
    }
    
    
    /**
     * This method returns the maximum document id
     * @param userID is the user id
     * @return the maximum document id, otherwhise if the result is empty it returns -1
     * @throws SQLException if there's an exception
     */
    
    public int getLastDocumentID(int userID) throws SQLException {
    	
    	String query = "SELECT MAX(document_id) AS max_document_id FROM Document WHERE owner_id = ?";
    	PreparedStatement statement = connection.prepareStatement(query);
    	statement.setInt(1, userID);
    	ResultSet result= statement.executeQuery();
    	
    	if(!result.isBeforeFirst())
        	return -1;
    	
    	result.next();
    	
    	return result.getInt("max_document_id");
       			
    }
    
    
    /**
	 * This method extracts the document id by the folder Name
	 * @param userID     is the user id
	 * @param documentName is the document name
	 * @return the document id if it exists, otherwhise -1
	 */
	public int getDocumentIDByDocumentName(int userID, String documentName) throws SQLException {

		String query = "SELECT document_id FROM Document WHERE owner_id = ? AND document_name = ?";
		PreparedStatement statement = connection.prepareStatement(query);
		statement.setInt(1, userID);
		statement.setString(2, documentName);
		ResultSet result = statement.executeQuery();

		if (!result.isBeforeFirst())
			return -1;
		
		result.next();

		return result.getInt("document_id");

	}
	
	
	/**
	 * This method returns the document name
	 * @param userID is the user id
	 * @param documentID is the document id
	 * @return the document name if it exists, otherwhise null
	 * @throws SQLException if there's an exception
	 */
	public String getDocumentNameByDocumentID(int userID, int documentID) throws SQLException {
		String query = "SELECT document_name FROM Document WHERE owner_id = ? AND document_id = ?";
		PreparedStatement statement = connection.prepareStatement(query);
		statement.setInt(1, userID);
		statement.setInt(2, documentID);
		ResultSet result = statement.executeQuery();

		if (!result.isBeforeFirst())
			return null;
		
		result.next();

		return result.getString("document_name");
	}
	
	
	/**
	 * It takes the document datas before it is deleted
	 * @param userID is the user id
	 * @param documentID the document id
	 * @return the document object
	 */
	public Document takeDatasBeforeDelete(int userID, int documentID) throws SQLException {
		
		String query = "SELECT * FROM Document WHERE owner_id = ? AND document_id = ?";
    	PreparedStatement statement = connection.prepareStatement(query);
    	statement.setInt(1, userID);
		statement.setInt(2, documentID);
	
		ResultSet result = statement.executeQuery();
		
		if (!result.isBeforeFirst())
			return null;
		
		result.next();
		
		Document document = new Document();
		document.setDocumentID(result.getInt("document_id"));
		document.setOwnerID(userID);
		document.setDocumentName(result.getString("folder_name"));
		document.setCreationDate(result.getTimestamp("creation_date"));
		document.setDocumentType(result.getString("document_type"));
		document.setSummary(result.getString("summary"));
		document.setFolderID(result.getInt("folder_id"));
		
		return document;
		
	}
}










