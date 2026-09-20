# Architecture and functional specification

This specification describes the implemented application. It does not replace the original university assignment, which was not supplied.

## Components

```text
HTML / CSS / vanilla JavaScript
  │ same-origin XMLHttpRequest, multipart FormData, X-CSRF-Token
  ▼
Session filters → WorkspaceController → WorkspaceService → MySQL transaction
                       │                  │
                       │                  ├─ Folder / Document: current metadata
                       │                  ├─ FileBlob: immutable owner-scoped bytes
                       │                  └─ WorkspaceState / WorkspaceAction: undo journal
                       └─ HttpSession: authenticated user, CSRF token, journal session key
```

Login and registration retain the original controllers and `UserDAO`. Workspace endpoints use one connection per request with try-with-resources, prepared statements and an explicit transaction. Locking the authenticated owner's `User` row serialises changes across their sessions. A failure rolls back the whole operation, including a multi-file upload or cumulative undo. Other users have separate locks.

`Checker` remains the authentication filter and protects all workspace routes. `sessionStorage.utente` is only a display/navigation hint. Session and journal tokens are generated server-side. All mutation routes require POST and a valid `X-CSRF-Token`; file fields use the standard Servlet multipart API, with parameter names rather than positional parsing. User names, descriptions and errors render through `textContent`.

## Files and folders

Every account has one hidden root (`Homepage`, depth zero). Its children are visible root folders. The hidden root cannot be renamed, moved or deleted; files must live in visible folders. Maximum depth is 40. Moves reject cycles and duplicate names at the destination; descendant depths update in the same transaction. Names are unique within a parent under the supplied database's collation. Existing files are never overwritten.

`Document` retains the baseline metadata fields and adds a nullable private `blob_id`. `FileBlob` stores immutable bytes, owner, byte count, detected preview MIME type and SHA-256. Legacy records have no blob and stay usable as metadata. Renaming or moving does not duplicate file content. Download checks document ownership and sends an attachment with an encoded filename. Only recognised raster signatures enable image preview; PDFs, HTML, SVG, scripts and other formats remain downloads.

Both buttons and drag/drop call the same backend routes. Files from the operating system may be dropped directly on folder headers or in the upload area. Application folders can be dragged between parents; a destination selector also offers the workspace root. Recursive directory upload from the operating system is not supported. Delete buttons and dropping into the delete target both ask for confirmation.

Search is local to the loaded tree. Matching ancestors remain visible; matching folders include their contents. UI labels remain in English, matching the original baseline. No frontend build, external fonts, analytics or remote UI services are needed.

## Cumulative session undo

Before each successful mutation, a metadata snapshot is journalled with a link to the previous action. Snapshots reference immutable file blobs rather than duplicating bytes. A multi-file upload is one action. Activity lists newest actions first and displays exactly how many newer actions each undo includes.

For example, given `upload → rename → move → delete`, selecting **rename** restores the state immediately before rename. Rename, move and delete are removed from the current activity; the uploaded file reappears with its original name, folder, ID and bytes. The earlier upload can then be undone separately. Undo is atomic and has no redo.

The request includes the activity's workspace revision. A stale revision returns 409 without restoring anything. The server walks backward from the current head and rejects any attempt to cross another session's action or an expired snapshot. A foreign-session barrier remains even after that session logs out; it cannot be bypassed by creating and undoing a new action.

Live snapshots expire after five minutes without a workspace API request. A request extends only still-live snapshots from its own session. Logout or session expiry releases that session's snapshots. Original session timeout remains five minutes. Expired snapshots are also cleared lazily on the next request for the owner; unreferenced blob bytes are collected during upload, undo or session cleanup. If the process stops without session cleanup, expired data is reclaimed on those later operations. Action stubs remain to preserve session barriers; this is not a permanent audit-log product and does not include an archival maintenance job.

## Limits

| Item                        | Limit                                                   |
| --------------------------- | ------------------------------------------------------- |
| File content                | 25 MiB each, 20 files and 100 MiB per selection         |
| Servlet multipart body      | 101 MiB including form overhead; 1 MiB memory threshold |
| Account storage             | 250 MiB including blobs retained for undo               |
| Folder depth / total items  | 40 / 10,000 including the hidden root                   |
| Folder name                 | 100 UTF-16 code units                                   |
| File name                   | 200 total; stem 180, extension 20; extension optional   |
| Legacy summary              | 250 characters                                          |
| Username / password / email | Original limits: 25 / 25 / 30 characters                |

Names are NFC-normalised and trimmed. Separators, path components `.` / `..`, control/format characters, trailing dots and Windows-reserved punctuation are rejected. Names are display metadata, never server filesystem paths. Server checks are authoritative. Database collation may reject additional case/accent-equivalent duplicates.

## API contracts

Routes are relative to the deployed application context. Workspace reads return JSON, except binary download/preview. Mutations return JSON on success; errors are safe plain text with a 4xx/5xx status. IDs are owner-checked before writing. Action IDs and revisions are decimal strings in JSON to avoid JavaScript integer precision loss.

| Endpoint                         | Method | Parameters / result                                                |
| -------------------------------- | ------ | ------------------------------------------------------------------ |
| `CheckLoginCredentials`          | POST   | Legacy username/password form; session and display identity        |
| `CheckSignupCredentials`         | POST   | Legacy username/email/password/passwordCheck form                  |
| `SessionToken`                   | GET    | CSRF token and upload/storage limits                               |
| `GetTree`                        | GET    | Recursive `folder`, `documentList`, `children` tree                |
| `GetDocument`                    | GET    | `documentID`; metadata, hasFile, size, SHA-256, canPreview         |
| `GetVersionHistory`              | GET    | Object: actions, revision, storageUsed, storageLimit               |
| `DownloadFile`, `PreviewFile`    | GET    | `documentID`; owner-scoped original bytes                          |
| `CreateFolder`                   | POST   | newFolderName, destinationID                                       |
| `CreateDocument`                 | POST   | docName, docFormat, docSummary, destinationID; legacy metadata API |
| `UploadFiles`                    | POST   | destinationID, repeated `files` file parts                         |
| `RenameItem`                     | POST   | itemType (`folder` or `document`), itemID, name                    |
| `MoveDocument`                   | POST   | documentID, folderID                                               |
| `MoveFolder`                     | POST   | folderID, destinationID                                            |
| `DeleteDocument`, `DeleteFolder` | POST   | documentID / folderID                                              |
| `UndoActions`                    | POST   | actionID, revision; returns number undone                          |
| `Logout`                         | POST   | Invalidates session and its undo snapshots                         |

All workspace POST routes require the CSRF header. All except Logout require multipart form data; order does not matter. The new UI uses upload instead of creating metadata-only records. Method, activity response and filename rules changed from v1 and require the matching frontend and schema to be deployed together.
