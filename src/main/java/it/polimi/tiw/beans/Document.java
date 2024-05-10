package it.polimi.tiw.beans;

import java.sql.Timestamp;

public class Document {

	private int documentID;
	private int ownerID;
	private String documentName;
	private String summary;
	private String documentType;
	private Timestamp creationDate;
	private int folderID;
	

	public int getDocumentID() {
		return documentID;
	}

	public void setDocumentID(int documentID) {
		this.documentID = documentID;
	}

	public int getOwnerID() {
		return ownerID;
	}

	public void setOwnerID(int ownerID) {
		this.ownerID = ownerID;
	}

	public String getDocumentName() {
		return documentName;
	}

	public void setDocumentName(String documentName) {
		this.documentName = documentName;
	}

	public String getSummary() {
		return summary;
	}

	public void setSummary(String summary) {
		this.summary = summary;
	}

	public String getDocumentType() {
		return documentType;
	}

	public void setDocumentType(String documentType) {
		this.documentType = documentType;
	}

	public Timestamp getCreationDate() {
		return creationDate;
	}

	public void setCreationDate(Timestamp creationDate) {
		this.creationDate = creationDate;
	}

	public int getFolderID() {
		return folderID;
	}

	public void setFolderID(int folderID) {
		this.folderID = folderID;
	}
	
	
	
}
