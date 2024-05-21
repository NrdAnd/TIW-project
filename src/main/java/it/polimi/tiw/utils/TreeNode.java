package it.polimi.tiw.utils;

import it.polimi.tiw.beans.Document;
import it.polimi.tiw.beans.Folder;
import java.util.ArrayList;

public class TreeNode {

	private Folder folder;
	private ArrayList<TreeNode> children;
	private ArrayList<Document> documentList;

	public TreeNode(Folder folder) {
		this.folder = folder;
		this.children = new ArrayList<>();
		this.documentList = new ArrayList<>();
	}

	public Folder getFolder() {
		return folder;
	}

	public ArrayList<TreeNode> getChildren() {

		ArrayList<TreeNode> copyList = new ArrayList<>();
		copyList.addAll(children);
		return copyList;
	}

	public void addChild(TreeNode child) {
		children.add(child);
	}

	public void setDocumentList(ArrayList<Document> documentList) {
		this.documentList.addAll(documentList);
	}

	
	public ArrayList<Document> getDocumentList() {

		ArrayList<Document> copyList = new ArrayList<>();
		copyList.addAll(documentList);
		return copyList;
	}

}
