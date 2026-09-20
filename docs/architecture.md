# Architecture and API

The browser uses HTML, CSS and vanilla JavaScript. Java Servlets handle sessions and HTTP requests; JDBC writes workspace data to MySQL.

```text
Browser → session filter → workspace controller → workspace service → MySQL
                                      │
                                      └→ session and CSRF token
```

`User` stores accounts. `Folder` and `Document` store the current tree and file metadata. `FileBlob` stores file bytes. `WorkspaceState` and `WorkspaceAction` store the revision and session undo history. File bytes remain in the database when an action can still be undone.

Every account has a hidden root folder. Files belong in visible folders. Moves check the source, destination and resulting tree. Each change runs in a transaction with an owner-level lock, so concurrent sessions cannot silently overwrite one another's changes.

## Undo

The service records a metadata snapshot before each successful change. Choosing an action restores the state before that action and removes all newer actions from the current session's undo history. Restored documents keep their file bytes. Undo does not cross another session's change or an expired snapshot; there is no redo. Activity uses a five-minute inactivity window.

## HTTP routes

All paths are relative to the deployed application context. Workspace POST routes require `X-CSRF-Token`; IDs are checked against the authenticated account.

| Purpose | Endpoints |
| --- | --- |
| Account and session | `CheckLoginCredentials`, `CheckSignupCredentials`, `SessionToken`, `Logout` |
| Browse | `GetTree`, `GetDocument`, `GetVersionHistory` |
| File bytes | `DownloadFile`, `PreviewFile`, `UploadFiles` |
| Edit | `CreateFolder`, `CreateDocument`, `RenameItem`, `MoveDocument`, `MoveFolder`, `DeleteDocument`, `DeleteFolder` |
| Restore | `UndoActions` |

Read routes return JSON except file downloads and previews. Changes use POST and return JSON on success; failures return an HTTP error status. The frontend and WAR must be deployed together because they share these routes and request fields.
