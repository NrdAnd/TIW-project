package it.polimi.tiw.workspace;

import java.util.ArrayList;
import java.util.List;

/** Metadata only. Immutable file bytes live in FileBlob and are never copied into the journal. */
public class WorkspaceSnapshot {
  public List<FolderRow> folders = new ArrayList<>();
  public List<DocumentRow> documents = new ArrayList<>();

  public static class FolderRow {
    public int folderID, parentFolderID, depth;
    public String folderName, creationDate;
  }

  public static class DocumentRow {
    public int documentID, folderID;
    public String documentName, documentType, summary, creationDate, blobID;
  }
}
