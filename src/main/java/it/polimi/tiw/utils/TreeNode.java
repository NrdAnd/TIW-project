package it.polimi.tiw.utils;

import it.polimi.tiw.beans.Folder;
import java.util.ArrayList;

public class TreeNode {
	
    private Folder folder;
    private ArrayList<TreeNode> children;
    private int childrenListSize;

    public TreeNode(Folder folder) {
        this.folder = folder;
        this.children = new ArrayList<>();
        this.childrenListSize = 0;
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
    
    public int getChildrenListSize() {
    	return this.children.size();
    }
}

