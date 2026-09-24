package it.polimi.tiw.controllers;

import it.polimi.tiw.beans.User;
import it.polimi.tiw.utils.ApiResponse;
import it.polimi.tiw.workspace.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.sql.SQLException;
import java.util.*;
import javax.servlet.ServletException;
import javax.servlet.annotation.*;
import javax.servlet.http.*;

@WebServlet(
    urlPatterns = {
      "/SessionToken",
      "/GetTree",
      "/GetDocument",
      "/GetVersionHistory",
      "/CreateFolder",
      "/CreateDocument",
      "/MoveDocument",
      "/MoveFolder",
      "/DeleteDocument",
      "/DeleteFolder",
      "/RenameItem",
      "/UploadFiles",
      "/DownloadFile",
      "/PreviewFile",
      "/UndoActions",
      "/Logout"
    })
@MultipartConfig(
    fileSizeThreshold = 1024 * 1024,
    maxFileSize = 25L * 1024 * 1024,
    maxRequestSize = 101L * 1024 * 1024)
public class WorkspaceController extends HttpServlet {
  private static final Set<String> READ =
      Set.of(
          "SessionToken",
          "GetTree",
          "GetDocument",
          "GetVersionHistory",
          "DownloadFile",
          "PreviewFile");
  private static final SecureRandom RANDOM = new SecureRandom();

  public static String token(HttpSession session, String name) {
    synchronized (session) {
      String token = (String) session.getAttribute(name);
      if (token == null) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        token = HexFormat.of().formatHex(bytes);
        session.setAttribute(name, token);
      }
      return token;
    }
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    process(req, resp);
  }

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    process(req, resp);
  }

  private int id(HttpServletRequest req, String key) {
    try {
      int id = Integer.parseInt(req.getParameter(key));
      if (id <= 0) throw new NumberFormatException();
      return id;
    } catch (Exception e) {
      throw new WorkspaceException(400, "Invalid " + key + ".");
    }
  }

  private long number(HttpServletRequest req, String key) {
    try {
      long value = Long.parseLong(req.getParameter(key));
      if (value < 0) throw new NumberFormatException();
      return value;
    } catch (Exception e) {
      throw new WorkspaceException(400, "Invalid " + key + ".");
    }
  }

  private void process(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    req.setCharacterEncoding("UTF-8");
    resp.setCharacterEncoding("UTF-8");
    resp.setHeader("Cache-Control", "no-store");
    resp.setHeader("X-Content-Type-Options", "nosniff");
    Collection<Part> parts = null;
    try {
      HttpSession session = req.getSession(false);
      User user = session == null ? null : (User) session.getAttribute("utente");
      if (user == null) throw new WorkspaceException(401, "Please sign in again.");
      String endpoint = req.getServletPath().substring(1);
      boolean read = READ.contains(endpoint);
      if ((read && !req.getMethod().equals("GET")) || (!read && !req.getMethod().equals("POST"))) {
        resp.setHeader("Allow", read ? "GET" : "POST");
        throw new WorkspaceException(405, "This endpoint does not support that method.");
      }
      String csrf = token(session, "workspaceCsrf"), key = token(session, "workspaceSession");
      if (endpoint.equals("SessionToken")) {
        json(resp, Map.of("csrfToken", csrf, "user", user.getUsername(),
            "maxFileSize", FileRules.MAX_FILE, "maxRequestSize", FileRules.MAX_REQUEST,
            "storageLimit", FileRules.QUOTA));
        return;
      }
      if (!read) {
        if (req.getQueryString() != null && !req.getQueryString().isEmpty())
          throw new WorkspaceException(400, "Query parameters are not allowed on mutation endpoints.");
        String supplied = req.getHeader("X-CSRF-Token");
        if (supplied == null
            || !MessageDigest.isEqual(
                csrf.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8)))
          throw new WorkspaceException(
              403, "Request token missing or invalid. Refresh the workspace.");
        if (endpoint.equals("Logout")) {
          session.invalidate();
          json(resp, Map.of("ok", true));
          return;
        }
        if (req.getContentType() == null
            || !req.getContentType().toLowerCase(Locale.ROOT).startsWith("multipart/form-data"))
          throw new WorkspaceException(400, "Use multipart form data.");
        parts = req.getParts();
        Set<String> fields = new HashSet<>();
        int fileCount = 0;
        for (Part part : parts) {
          if (part.getSubmittedFileName() != null) {
            if (!endpoint.equals("UploadFiles") || !part.getName().equals("files"))
              throw new WorkspaceException(400, "Unexpected file field.");
            fileCount++;
          } else if (part.getSize() > 2048 || !fields.add(part.getName()))
            throw new WorkspaceException(400, "Invalid or duplicate form fields.");
        }
        if (fileCount > 20 || parts.size() > 25)
          throw new WorkspaceException(400, "Too many files or form fields.");
      }
      try (WorkspaceService service =
          new WorkspaceService(getServletContext(), user.getUserID(), key)) {
        service.lock();
        Object result = Map.of("ok", true);
        switch (endpoint) {
          case "GetTree":
            result = service.tree();
            break;
          case "GetDocument":
            result = service.getDocument(id(req, "documentID"));
            break;
          case "GetVersionHistory":
            result = service.history();
            break;
          case "CreateFolder":
            service.createFolder(id(req, "destinationID"), req.getParameter("newFolderName"));
            break;
          case "CreateDocument":
            service.createMetadata(
                id(req, "destinationID"),
                req.getParameter("docName"),
                req.getParameter("docFormat"),
                req.getParameter("docSummary"));
            break;
          case "UploadFiles":
            List<Part> files = new ArrayList<>();
            for (Part part : parts) if (part.getSubmittedFileName() != null) files.add(part);
            service.upload(id(req, "destinationID"), files);
            break;
          case "MoveDocument":
            service.moveDocument(id(req, "documentID"), id(req, "folderID"));
            break;
          case "MoveFolder":
            service.moveFolder(id(req, "folderID"), id(req, "destinationID"));
            break;
          case "RenameItem":
            service.rename(
                Objects.toString(req.getParameter("itemType"), ""),
                id(req, "itemID"),
                req.getParameter("name"));
            break;
          case "DeleteDocument":
            service.deleteDocument(id(req, "documentID"));
            break;
          case "DeleteFolder":
            service.deleteFolder(id(req, "folderID"));
            break;
          case "UndoActions":
            result =
                Map.of("undone", service.undo(number(req, "actionID"), number(req, "revision")));
            break;
          case "DownloadFile":
          case "PreviewFile":
            service.download(id(req, "documentID"), endpoint.equals("PreviewFile"), resp);
            service.commit();
            return;
          default:
            throw new WorkspaceException(404, "Unknown endpoint.");
        }
        service.commit();
        json(resp, result);
      }
    } catch (WorkspaceException e) {
      error(resp, e.status(), e.getMessage());
    } catch (IllegalStateException e) {
      error(resp, 413, "Upload too large: 25 MB per file and 100 MB per selection.");
    } catch (javax.servlet.UnavailableException e) {
      error(resp, 503, "Database configuration is unavailable. Contact the administrator.");
    } catch (ServletException e) {
      error(resp, 400, "Malformed multipart request.");
    } catch (SQLException e) {
      if ("23000".equals(e.getSQLState()))
        error(resp, 409, "That name already exists, or the destination is no longer available.");
      else {
        getServletContext()
            .log("Workspace database operation failed (SQL state " + e.getSQLState() + ").");
        error(resp, 500, "The operation could not be completed. No partial changes were saved.");
      }
    } catch (Exception e) {
      getServletContext().log("Workspace operation failed: " + e.getClass().getSimpleName());
      error(resp, 500, "The operation could not be completed. Please try again.");
    } finally {
      if (parts != null)
        for (Part part : parts)
          try {
            part.delete();
          } catch (IOException ignored) {
          }
    }
  }

  private void json(HttpServletResponse resp, Object value) throws IOException {
    ApiResponse.ok(resp, value);
  }

  private void error(HttpServletResponse resp, int status, String message) throws IOException {
    String code;
    switch (status) {
      case 400:
        code = "INVALID_REQUEST";
        break;
      case 401:
        code = "AUTHENTICATION_REQUIRED";
        break;
      case 403:
        code = "INVALID_REQUEST_TOKEN";
        break;
      case 404:
        code = "NOT_FOUND";
        break;
      case 405:
        code = "METHOD_NOT_ALLOWED";
        break;
      case 409:
        code = "WORKSPACE_CONFLICT";
        break;
      case 413:
        code = "UPLOAD_TOO_LARGE";
        break;
      case 415:
        code = "UNSUPPORTED_FILE_TYPE";
        break;
      case 503:
        code = "DATABASE_UNAVAILABLE";
        break;
      default:
        code = "WORKSPACE_ERROR";
        break;
    }
    ApiResponse.error(resp, status, code, message);
  }
}
