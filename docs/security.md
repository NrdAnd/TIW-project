# Security

## Access and files

- Workspace requests require a server-side session. Reads and changes check file and folder ownership.
- Changes and logout require POST with a session CSRF token. Database changes use transactions so failed uploads and undo operations roll back together.
- Uploaded filenames are validated and never used as server filesystem paths. The Servlet container limits request size; per-file and account quotas are checked by the application.
- Downloads use attachment headers and `nosniff`. Inline previews are restricted to recognised PNG, JPEG, GIF and WebP signatures.
- Database credentials come from an external Tomcat context or process environment. They are not included in the WAR.

## Deployment limits

Account passwords are stored and compared as plain text in the `User` table. Do not reuse those passwords on other services. Password hashing, a migration for existing accounts, login rate limiting and session-ID rotation are needed before exposing the service to the Internet.

The application does not scan files for malware. Storage quotas do not cap all database journal, temporary-file or backup usage. Provide restricted Tomcat temporary storage, database backups and appropriate network access controls. Treat downloaded files as untrusted.

The local preview in `scripts/preview.py` uses temporary demonstration data and has no authentication. It is for development only.
