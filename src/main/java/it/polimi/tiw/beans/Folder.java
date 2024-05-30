package it.polimi.tiw.beans;
import java.sql.Timestamp;

public class Folder {
	
	private int folderID;
	private int ownerID;
	private String folderName;
	private Timestamp creationDate;
	private int parentFolderID;
	private int depth;
	
	
	public int getFolderID() {
		return folderID;
	}

	public void setFolderID(int folderID) {
		this.folderID = folderID;
	}

	public int getOwnerID() {
		return ownerID;
	}

	public void setOwnerID(int ownerID) {
		this.ownerID = ownerID;
	}

	public String getFolderName() {
		return folderName;
	}

	public void setFolderName(String folderName) {
		this.folderName = folderName;
	}

	public Timestamp getCreationDate() {
		return creationDate;
	}

	public void setCreationDate(Timestamp creationDate) {
		this.creationDate = creationDate;
	}

	public int getParentFolderID() {
		return parentFolderID;
	}

	public void setParentFolderID(int parentFolderID) {
		this.parentFolderID = parentFolderID;
	}

	public int getDepth() {
		return depth;
	}

	public void setDepth(int depth) {
		this.depth = depth;
	}
	
	

}
