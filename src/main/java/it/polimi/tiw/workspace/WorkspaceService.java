package it.polimi.tiw.workspace;

import static it.polimi.tiw.workspace.WorkspaceSnapshot.*;

import com.google.gson.Gson;
import it.polimi.tiw.utils.ConnectionHandler;
import java.io.*;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.sql.*;
import java.util.*;
import javax.servlet.ServletContext;
import javax.servlet.http.Part;

/** One connection and transaction per request; every mutation locks the owner's User row. */
public final class WorkspaceService implements AutoCloseable {
  private static final Gson JSON = new Gson();
  private final Connection connection;
  private final int owner;
  private final String sessionKey;
  private long revision;
  private Long head;

  public WorkspaceService(ServletContext context, int owner, String sessionKey) throws Exception {
    this.connection = ConnectionHandler.getConnection(context);
    this.owner = owner;
    this.sessionKey = sessionKey;
    connection.setAutoCommit(false);
  }

  private PreparedStatement statement(String sql, Object... values) throws SQLException {
    PreparedStatement result = connection.prepareStatement(sql);
    for (int i = 0; i < values.length; i++) result.setObject(i + 1, values[i]);
    return result;
  }

  private int execute(String sql, Object... values) throws SQLException {
    try (PreparedStatement q = statement(sql, values)) {
      return q.executeUpdate();
    }
  }

  private long insert(String sql, Object... values) throws SQLException {
    try (PreparedStatement q = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
      for (int i = 0; i < values.length; i++) q.setObject(i + 1, values[i]);
      q.executeUpdate();
      try (ResultSet rs = q.getGeneratedKeys()) {
        if (!rs.next()) throw new SQLException("Missing generated key");
        return rs.getLong(1);
      }
    }
  }

  public void lock() throws SQLException {
    try (PreparedStatement q =
            statement("SELECT user_id FROM User WHERE user_id=? FOR UPDATE", owner);
        ResultSet rs = q.executeQuery()) {
      if (!rs.next()) throw new WorkspaceException(401, "Please sign in again.");
    }
    execute("INSERT IGNORE INTO WorkspaceState(owner_id) VALUES (?)", owner);
    try (PreparedStatement q =
            statement(
                "SELECT revision,head_action_id FROM WorkspaceState WHERE owner_id=?", owner);
        ResultSet rs = q.executeQuery()) {
      rs.next();
      revision = rs.getLong(1);
      head = (Long) rs.getObject(2);
    }
    // Only still-live journal entries are extended; expired snapshots are never resurrected.
    execute(
        "UPDATE WorkspaceAction SET expires_at=DATE_ADD(CURRENT_TIMESTAMP, INTERVAL 300 SECOND)"
            + " WHERE owner_id=? AND session_key=? AND before_state IS NOT NULL AND"
            + " expires_at>CURRENT_TIMESTAMP",
        owner,
        sessionKey);
    execute(
        "UPDATE WorkspaceAction SET before_state=NULL WHERE owner_id=? AND"
            + " expires_at<=CURRENT_TIMESTAMP",
        owner);
  }

  public void commit() throws SQLException {
    connection.commit();
  }

  @Override
  public void close() throws SQLException {
    try {
      connection.rollback();
    } finally {
      connection.close();
    }
  }

  public WorkspaceSnapshot snapshot() throws SQLException {
    WorkspaceSnapshot snapshot = new WorkspaceSnapshot();
    try (PreparedStatement q =
            statement(
                "SELECT folder_id,folder_name,parent_folder_id,depth,creation_date FROM Folder"
                    + " WHERE owner_id=? ORDER BY depth,folder_id",
                owner);
        ResultSet rs = q.executeQuery()) {
      while (rs.next()) {
        FolderRow f = new FolderRow();
        f.folderID = rs.getInt(1);
        f.folderName = rs.getString(2);
        f.parentFolderID = rs.getInt(3);
        f.depth = rs.getInt(4);
        f.creationDate = rs.getTimestamp(5).toString();
        snapshot.folders.add(f);
      }
    }
    try (PreparedStatement q =
            statement(
                "SELECT"
                    + " document_id,folder_id,document_name,document_type,summary,creation_date,blob_id"
                    + " FROM Document WHERE owner_id=? ORDER BY document_id",
                owner);
        ResultSet rs = q.executeQuery()) {
      while (rs.next()) {
        DocumentRow d = new DocumentRow();
        d.documentID = rs.getInt(1);
        d.folderID = rs.getInt(2);
        d.documentName = rs.getString(3);
        d.documentType = rs.getString(4);
        d.summary = rs.getString(5);
        d.creationDate = rs.getTimestamp(6).toString();
        d.blobID = rs.getString(7);
        snapshot.documents.add(d);
      }
    }
    return snapshot;
  }

  private FolderRow folder(WorkspaceSnapshot state, int id) {
    return state.folders.stream()
        .filter(f -> f.folderID == id)
        .findFirst()
        .orElseThrow(() -> new WorkspaceException(404, "Folder not found."));
  }

  private DocumentRow document(WorkspaceSnapshot state, int id) {
    return state.documents.stream()
        .filter(d -> d.documentID == id)
        .findFirst()
        .orElseThrow(() -> new WorkspaceException(404, "File not found."));
  }

  private void visible(FolderRow folder) {
    if (folder.depth == 0)
      throw new WorkspaceException(400, "The workspace root cannot be changed.");
  }

  private void uniqueFolder(WorkspaceSnapshot state, int parent, String name, int excluding) {
    if (state.folders.stream()
        .anyMatch(
            f ->
                f.folderID != excluding
                    && f.parentFolderID == parent
                    && f.folderName.equalsIgnoreCase(name)))
      throw new WorkspaceException(409, "A folder with that name already exists here.");
  }

  private void uniqueDocument(
      WorkspaceSnapshot state, int parent, String name, String type, int excluding) {
    if (state.documents.stream()
        .anyMatch(
            d ->
                d.documentID != excluding
                    && d.folderID == parent
                    && d.documentName.equalsIgnoreCase(name)
                    && d.documentType.equalsIgnoreCase(type)))
      throw new WorkspaceException(409, "A file with that name already exists here.");
  }

  private Set<Integer> descendants(WorkspaceSnapshot state, int id) {
    Set<Integer> ids = new HashSet<>();
    ids.add(id);
    boolean changed;
    do {
      changed = false;
      for (FolderRow f : state.folders)
        if (ids.contains(f.parentFolderID)) changed |= ids.add(f.folderID);
    } while (changed);
    return ids;
  }

  public void record(String description, WorkspaceSnapshot before) throws SQLException {
    try (PreparedStatement q =
            statement(
                "SELECT (SELECT COUNT(*) FROM Folder WHERE owner_id=?) + (SELECT COUNT(*) FROM"
                    + " Document WHERE owner_id=?)",
                owner,
                owner);
        ResultSet rs = q.executeQuery()) {
      rs.next();
      if (rs.getLong(1) > 10000) throw new WorkspaceException(409, "Workspace item limit reached.");
    }
    long action =
        insert(
            "INSERT INTO"
                + " WorkspaceAction(owner_id,parent_action_id,session_key,description,expires_at,before_state)"
                + " VALUES(?,?,?,?,DATE_ADD(CURRENT_TIMESTAMP, INTERVAL 300 SECOND),?)",
            owner,
            head,
            sessionKey,
            description,
            JSON.toJson(before));
    execute(
        "UPDATE WorkspaceState SET revision=revision+1,head_action_id=? WHERE owner_id=?",
        action,
        owner);
    revision++;
    head = action;
  }

  public void createFolder(int parent, String requested) throws SQLException {
    WorkspaceSnapshot before = snapshot();
    FolderRow destination = folder(before, parent);
    String name = FileRules.name(requested, 100);
    if (destination.depth >= 40) throw new WorkspaceException(400, "Maximum folder depth is 40.");
    uniqueFolder(before, parent, name, -1);
    insert(
        "INSERT INTO Folder(owner_id,folder_name,parent_folder_id,depth) VALUES(?,?,?,?)",
        owner,
        name,
        parent,
        destination.depth + 1);
    record("Created folder “" + name + "”", before);
  }

  public void createMetadata(int parent, String name, String type, String summary)
      throws SQLException {
    WorkspaceSnapshot before = snapshot();
    visible(folder(before, parent));
    name = FileRules.name(name, 180);
    if (type == null || !type.matches("[a-zA-Z0-9]{1,20}"))
      throw new WorkspaceException(400, "Invalid document format.");
    if (summary == null || summary.isBlank() || summary.length() > 250)
      throw new WorkspaceException(400, "A summary of at most 250 characters is required.");
    uniqueDocument(before, parent, name, type, -1);
    insert(
        "INSERT INTO Document(owner_id,folder_id,document_name,document_type,summary)"
            + " VALUES(?,?,?,?,?)",
        owner,
        parent,
        name,
        type,
        summary);
    record("Created document “" + FileRules.fileName(name, type) + "”", before);
  }

  public long storageUsed() throws SQLException {
    try (PreparedStatement q =
            statement("SELECT COALESCE(SUM(file_size),0) FROM FileBlob WHERE owner_id=?", owner);
        ResultSet rs = q.executeQuery()) {
      rs.next();
      return rs.getLong(1);
    }
  }

  public void upload(int parent, List<Part> files) throws Exception {
    WorkspaceSnapshot before = snapshot();
    visible(folder(before, parent));
    if (files.isEmpty() || files.size() > 20)
      throw new WorkspaceException(400, "Choose between 1 and 20 files.");
    long size = 0;
    Set<String> names = new HashSet<>();
    for (Part file : files) {
      if (file.getSize() > FileRules.MAX_FILE)
        throw new WorkspaceException(413, "Each file must be at most 25 MB.");
      size += file.getSize();
      String[] name = FileRules.splitName(file.getSubmittedFileName());
      uniqueDocument(before, parent, name[0], name[1], -1);
      if (!names.add(FileRules.fileName(name[0], name[1]).toLowerCase(Locale.ROOT)))
        throw new WorkspaceException(409, "The selection contains duplicate names.");
    }
    pruneBlobs();
    if (size > FileRules.MAX_REQUEST || storageUsed() + size > FileRules.QUOTA)
      throw new WorkspaceException(
          413, "Storage limit reached: 250 MB including files retained for session undo.");
    for (Part file : files) {
      String[] name = FileRules.splitName(file.getSubmittedFileName());
      String blob = UUID.randomUUID().toString();
      byte[] prefix;
      try (InputStream in = file.getInputStream()) {
        prefix = in.readNBytes(16);
      }
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      try (InputStream in = new DigestInputStream(file.getInputStream(), digest);
          PreparedStatement q =
              connection.prepareStatement(
                  "INSERT INTO FileBlob(blob_id,owner_id,file_size,media_type,sha256,content)"
                      + " VALUES(?,?,?,?,?,?)")) {
        q.setString(1, blob);
        q.setInt(2, owner);
        q.setLong(3, file.getSize());
        q.setString(4, FileRules.previewType(prefix));
        q.setString(5, "");
        q.setBinaryStream(6, in, file.getSize());
        q.executeUpdate();
      }
      execute(
          "UPDATE FileBlob SET sha256=? WHERE blob_id=? AND owner_id=?",
          HexFormat.of().formatHex(digest.digest()),
          blob,
          owner);
      insert(
          "INSERT INTO Document(owner_id,folder_id,document_name,document_type,summary,blob_id)"
              + " VALUES(?,?,?,?,?,?)",
          owner,
          parent,
          name[0],
          name[1],
          "",
          blob);
    }
    record(
        files.size() == 1
            ? "Uploaded “" + files.get(0).getSubmittedFileName() + "”"
            : "Uploaded " + files.size() + " files",
        before);
  }

  public void rename(String type, int id, String requested) throws SQLException {
    WorkspaceSnapshot before = snapshot();
    String old;
    if (type.equals("folder")) {
      FolderRow f = folder(before, id);
      visible(f);
      String name = FileRules.name(requested, 100);
      old = f.folderName;
      if (old.equals(name)) throw new WorkspaceException(400, "Choose a different name.");
      uniqueFolder(before, f.parentFolderID, name, id);
      execute("UPDATE Folder SET folder_name=? WHERE folder_id=? AND owner_id=?", name, id, owner);
    } else if (type.equals("document")) {
      DocumentRow d = document(before, id);
      String[] name = FileRules.splitName(requested);
      old = FileRules.fileName(d.documentName, d.documentType);
      if (old.equals(requested)) throw new WorkspaceException(400, "Choose a different name.");
      uniqueDocument(before, d.folderID, name[0], name[1], id);
      execute(
          "UPDATE Document SET document_name=?,document_type=? WHERE document_id=? AND owner_id=?",
          name[0],
          name[1],
          id,
          owner);
    } else throw new WorkspaceException(400, "Unknown item type.");
    record("Renamed “" + old + "” to “" + requested + "”", before);
  }

  public void moveDocument(int id, int parent) throws SQLException {
    WorkspaceSnapshot before = snapshot();
    DocumentRow d = document(before, id);
    visible(folder(before, parent));
    if (d.folderID == parent) throw new WorkspaceException(400, "Choose a different destination.");
    uniqueDocument(before, parent, d.documentName, d.documentType, id);
    execute(
        "UPDATE Document SET folder_id=? WHERE document_id=? AND owner_id=?", parent, id, owner);
    record("Moved file “" + FileRules.fileName(d.documentName, d.documentType) + "”", before);
  }

  public void moveFolder(int id, int parent) throws SQLException {
    WorkspaceSnapshot before = snapshot();
    FolderRow f = folder(before, id), dest = folder(before, parent);
    visible(f);
    Set<Integer> subtree = descendants(before, id);
    if (subtree.contains(parent) || parent == f.parentFolderID)
      throw new WorkspaceException(
          400, "A folder cannot move into itself, its descendants or its current parent.");
    uniqueFolder(before, parent, f.folderName, id);
    int delta = dest.depth + 1 - f.depth;
    if (before.folders.stream()
        .filter(row -> subtree.contains(row.folderID))
        .anyMatch(row -> row.depth + delta > 40))
      throw new WorkspaceException(400, "Maximum folder depth is 40.");
    execute(
        "UPDATE Folder SET parent_folder_id=? WHERE folder_id=? AND owner_id=?", parent, id, owner);
    for (int folder : subtree)
      execute(
          "UPDATE Folder SET depth=depth+? WHERE folder_id=? AND owner_id=?", delta, folder, owner);
    record("Moved folder “" + f.folderName + "”", before);
  }

  public void deleteDocument(int id) throws SQLException {
    WorkspaceSnapshot before = snapshot();
    DocumentRow d = document(before, id);
    execute("DELETE FROM Document WHERE document_id=? AND owner_id=?", id, owner);
    record("Deleted file “" + FileRules.fileName(d.documentName, d.documentType) + "”", before);
  }

  public void deleteFolder(int id) throws SQLException {
    WorkspaceSnapshot before = snapshot();
    FolderRow f = folder(before, id);
    visible(f);
    Set<Integer> ids = descendants(before, id);
    for (int folder : ids)
      execute("DELETE FROM Document WHERE folder_id=? AND owner_id=?", folder, owner);
    List<FolderRow> reverse = new ArrayList<>(before.folders);
    Collections.reverse(reverse);
    for (FolderRow row : reverse)
      if (ids.contains(row.folderID))
        execute("DELETE FROM Folder WHERE folder_id=? AND owner_id=?", row.folderID, owner);
    record("Deleted folder “" + f.folderName + "” and its contents", before);
  }

  private record Action(
      long id, Long parent, String key, String description, String created, String snapshot) {}

  private Map<Long, Action> actions() throws SQLException {
    Map<Long, Action> result = new LinkedHashMap<>();
    try (PreparedStatement q =
            statement(
                "SELECT action_id,parent_action_id,session_key,description,created_at,before_state"
                    + " FROM WorkspaceAction WHERE owner_id=? ORDER BY action_id DESC",
                owner);
        ResultSet rs = q.executeQuery()) {
      while (rs.next()) {
        long id = rs.getLong(1);
        result.put(
            id,
            new Action(
                id,
                (Long) rs.getObject(2),
                rs.getString(3),
                rs.getString(4),
                rs.getTimestamp(5).toString(),
                rs.getString(6)));
      }
    }
    return result;
  }

  public Map<String, Object> history() throws SQLException {
    Map<Long, Action> all = actions();
    Map<Long, Integer> undoable = new HashMap<>();
    Long cursor = head;
    int count = 0;
    while (cursor != null) {
      Action action = all.get(cursor);
      if (action == null || !sessionKey.equals(action.key) || action.snapshot == null) break;
      undoable.put(cursor, ++count);
      cursor = action.parent;
    }
    List<Map<String, Object>> entries = new ArrayList<>();
    for (Action action : all.values())
      if (sessionKey.equals(action.key)) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("id", Long.toString(action.id));
        entry.put("description", action.description);
        entry.put("createdAt", action.created);
        entry.put("undoCount", undoable.getOrDefault(action.id, 0));
        entry.put("undoable", undoable.containsKey(action.id));
        entries.add(entry);
      }
    return Map.of(
        "actions",
        entries,
        "revision",
        Long.toString(revision),
        "storageUsed",
        storageUsed(),
        "storageLimit",
        FileRules.QUOTA);
  }

  public int undo(long target, long expectedRevision) throws SQLException {
    if (expectedRevision != revision)
      throw new WorkspaceException(
          409, "The workspace changed. Refresh and review the activity before undoing.");
    Map<Long, Action> all = actions();
    List<Long> suffix = new ArrayList<>();
    Action selected = null;
    Long cursor = head;
    while (cursor != null) {
      Action action = all.get(cursor);
      if (action == null || !sessionKey.equals(action.key) || action.snapshot == null) break;
      suffix.add(cursor);
      if (cursor == target) {
        selected = action;
        break;
      }
      cursor = action.parent;
    }
    if (selected == null)
      throw new WorkspaceException(
          409,
          "This action cannot be undone: another session changed the workspace, or its undo data"
              + " expired.");
    WorkspaceSnapshot restored = JSON.fromJson(selected.snapshot, WorkspaceSnapshot.class);
    restore(restored);
    for (long id : suffix)
      execute("DELETE FROM WorkspaceAction WHERE action_id=? AND owner_id=?", id, owner);
    execute(
        "UPDATE WorkspaceState SET revision=revision+1,head_action_id=? WHERE owner_id=?",
        selected.parent,
        owner);
    revision++;
    head = selected.parent;
    pruneBlobs();
    return suffix.size();
  }

  private void restore(WorkspaceSnapshot state) throws SQLException {
    WorkspaceSnapshot current = snapshot();
    execute("DELETE FROM Document WHERE owner_id=?", owner);
    List<FolderRow> reverse = new ArrayList<>(current.folders);
    Collections.reverse(reverse);
    for (FolderRow row : reverse)
      execute("DELETE FROM Folder WHERE folder_id=? AND owner_id=?", row.folderID, owner);
    for (FolderRow f : state.folders)
      execute(
          "INSERT INTO Folder(folder_id,owner_id,folder_name,parent_folder_id,depth,creation_date)"
              + " VALUES(?,?,?,?,?,?)",
          f.folderID,
          owner,
          f.folderName,
          f.parentFolderID == 0 ? null : f.parentFolderID,
          f.depth,
          Timestamp.valueOf(f.creationDate));
    for (DocumentRow d : state.documents)
      execute(
          "INSERT INTO"
              + " Document(document_id,owner_id,folder_id,document_name,document_type,summary,creation_date,blob_id)"
              + " VALUES(?,?,?,?,?,?,?,?)",
          d.documentID,
          owner,
          d.folderID,
          d.documentName,
          d.documentType,
          d.summary,
          Timestamp.valueOf(d.creationDate),
          d.blobID);
  }

  public void pruneBlobs() throws SQLException {
    Set<String> keep = new HashSet<>();
    for (DocumentRow d : snapshot().documents) if (d.blobID != null) keep.add(d.blobID);
    try (PreparedStatement q =
            statement(
                "SELECT before_state FROM WorkspaceAction WHERE owner_id=? AND before_state IS NOT"
                    + " NULL",
                owner);
        ResultSet rs = q.executeQuery()) {
      while (rs.next())
        for (DocumentRow d : JSON.fromJson(rs.getString(1), WorkspaceSnapshot.class).documents)
          if (d.blobID != null) keep.add(d.blobID);
    }
    List<String> remove = new ArrayList<>();
    try (PreparedStatement q = statement("SELECT blob_id FROM FileBlob WHERE owner_id=?", owner);
        ResultSet rs = q.executeQuery()) {
      while (rs.next()) if (!keep.contains(rs.getString(1))) remove.add(rs.getString(1));
    }
    for (String id : remove)
      execute("DELETE FROM FileBlob WHERE blob_id=? AND owner_id=?", id, owner);
  }

  public void closeSession() throws SQLException {
    execute(
        "UPDATE WorkspaceAction SET before_state=NULL WHERE owner_id=? AND session_key=?",
        owner,
        sessionKey);
    pruneBlobs();
  }

  public Map<String, Object> documentDetails(DocumentRow d) throws SQLException {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("documentID", d.documentID);
    result.put("folderID", d.folderID);
    result.put("documentName", d.documentName);
    result.put("documentType", d.documentType);
    result.put("summary", d.summary);
    result.put("creationDate", d.creationDate);
    result.put("fileName", FileRules.fileName(d.documentName, d.documentType));
    result.put("hasFile", d.blobID != null);
    if (d.blobID != null)
      try (PreparedStatement q =
              statement(
                  "SELECT file_size,media_type,sha256 FROM FileBlob WHERE blob_id=? AND owner_id=?",
                  d.blobID,
                  owner);
          ResultSet rs = q.executeQuery()) {
        if (!rs.next()) throw new WorkspaceException(404, "File content not found.");
        result.put("size", rs.getLong(1));
        result.put("mediaType", rs.getString(2));
        result.put("sha256", rs.getString(3));
        result.put("canPreview", rs.getString(2).startsWith("image/"));
      }
    return result;
  }

  public Map<String, Object> getDocument(int id) throws SQLException {
    return documentDetails(document(snapshot(), id));
  }

  public Map<String, Object> tree() throws SQLException {
    WorkspaceSnapshot state = snapshot();
    Map<Integer, Map<String, Object>> nodes = new LinkedHashMap<>();
    Map<String, Object> root = null;
    for (FolderRow f : state.folders) {
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("folder", f);
      row.put("children", new ArrayList<>());
      row.put("documentList", new ArrayList<>());
      nodes.put(f.folderID, row);
      if (f.depth == 0) root = row;
    }
    for (FolderRow f : state.folders)
      if (f.depth > 0) children(nodes.get(f.parentFolderID), "children").add(nodes.get(f.folderID));
    for (DocumentRow d : state.documents)
      children(nodes.get(d.folderID), "documentList").add(documentDetails(d));
    if (root == null) throw new WorkspaceException(409, "Workspace root is missing.");
    return root;
  }

  @SuppressWarnings("unchecked")
  private List<Object> children(Map<String, Object> node, String key) {
    if (node == null) throw new WorkspaceException(409, "Folder hierarchy is inconsistent.");
    return (List<Object>) node.get(key);
  }

  public void download(int id, boolean preview, javax.servlet.http.HttpServletResponse response)
      throws Exception {
    DocumentRow d = document(snapshot(), id);
    if (d.blobID == null)
      throw new WorkspaceException(404, "This legacy metadata record has no uploaded file.");
    try (PreparedStatement q =
            statement(
                "SELECT file_size,media_type,content FROM FileBlob WHERE blob_id=? AND owner_id=?",
                d.blobID,
                owner);
        ResultSet rs = q.executeQuery()) {
      if (!rs.next()) throw new WorkspaceException(404, "File not found.");
      String mime = rs.getString(2);
      if (preview && !mime.startsWith("image/"))
        throw new WorkspaceException(
            415, "Only raster images support inline preview. Download this file instead.");
      response.setContentType(preview ? mime : "application/octet-stream");
      response.setContentLengthLong(rs.getLong(1));
      String filename =
          java.net.URLEncoder.encode(
                  FileRules.fileName(d.documentName, d.documentType),
                  java.nio.charset.StandardCharsets.UTF_8)
              .replace("+", "%20");
      response.setHeader(
          "Content-Disposition",
          (preview ? "inline" : "attachment")
              + "; filename=\"download\"; filename*=UTF-8''"
              + filename);
      response.setHeader("Content-Security-Policy", "default-src 'none'; sandbox");
      try (InputStream content = rs.getBinaryStream(3)) {
        content.transferTo(response.getOutputStream());
      }
    }
  }
}
