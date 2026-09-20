# Security scope and known limitations

The file-management extension preserves server-side session authentication and adds controls for uploads, mutations and undo. It is not a penetration test or a production security certification.

## Workspace controls

- The unchanged `Checker` filter maps to all 16 workspace endpoints. The controller additionally requires an authenticated server-side user. Browser storage never authorises access.
- Mutations and logout require POST and a per-session random CSRF token checked before multipart parsing. Read endpoints reject POST. This replaces the old workspace controllers that accepted state-changing GET requests.
- Ownership of source and destination is checked before changes, downloads, previews and undo. Composite owner foreign keys add a second boundary in the supplied schema.
- Each workspace request owns its JDBC connection. Mutations, snapshots and revision updates share a transaction under an owner-row lock. Invalid uploads and failed undo roll back as a unit. Stale or cross-session undo returns 409.
- Files use immutable database blobs and private generated identifiers. User filenames never become filesystem paths. Standard Servlet multipart handling limits body size; temporary parts are deleted after processing.
- File size, batch count, quota, names, folder cycles, depth and item count are checked server-side. Deleted bytes retained for undo count towards quota. Duplicate names never replace existing content.
- Download responses use attachment disposition, encoded filenames, `nosniff` and `no-store`. Inline preview is limited to recognised PNG/JPEG/GIF/WebP signatures and has `Content-Security-Policy: default-src 'none'; sandbox`. Other types, including HTML/SVG/PDF, are download-only. Signature detection is not antivirus scanning or full image validation.
- Dynamic text uses DOM text nodes. Delete and undo confirmations describe their scope; server checks remain authoritative regardless of UI controls.
- The development preview is not included in the WAR, has no production switch and never provides authentication for the real application.

These choices follow the relevant principles in the [OWASP File Upload Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/File_Upload_Cheat_Sheet.html), but accepting arbitrary downloadable file types is a deliberate functional choice. There is no malware scanning, content disarm/reconstruction, archive extraction or server-side document rendering. Treat downloaded files as untrusted.

## Credentials and publication

The original tracked `web.xml` contained database credentials. Current deployment values are empty. `ConnectionHandler` reads environment overrides or an external Tomcat context and fails closed if required settings are missing. Local configuration is ignored by Git and is excluded from source exports.

The release preserves the original sequence of commits while replacing historical database credentials and personal machine paths. Rewritten commits have new hashes. Rotate/revoke the affected account credentials before using that account again. GitHub may retain old commits in cached views or pull-request references, and other clones or forks can retain the old objects. Coordinate cleanup with their owners and GitHub Support where appropriate; never merge or push an old clone into the rewritten branch. `scripts/check-publication.py` checks tracked files for common private paths, credentials and contact details, but cannot prove that all sensitive data has been found.

## Remaining legacy and operational limitations

1. **Authentication.** `UserDAO` still stores and compares passwords directly, with the original 25-character limit. Login/registration keep their legacy request validation and database lifecycle. They need password hashing, an account migration/reset strategy, resource/concurrency review and rate limiting before public deployment. New workspace CSRF validation does not claim to harden these legacy authentication endpoints.
2. **Session hardening.** Login does not explicitly rotate its session ID. The original five-minute inactivity policy remains. Review HTTPS, session cookie `Secure`, `HttpOnly` and `SameSite`, proxy and container settings on the target installation.
3. **Dependencies.** Gson 2.8.6 and MySQL Connector/J 8.0.32 remain for compatibility; original Eclipse JARs remain tracked. Maven packages only declared dependencies. No comprehensive vulnerability audit or dependency upgrade was performed. Assess and update dependencies with regression testing before production exposure.
4. **Capacity and retention.** File quotas do not bound total journal JSON, action-stub storage, request rate, image dimensions or deployment-wide disk usage. Snapshots are metadata copies and can grow with activity. There is no global retention scheduler, backup system, malware scanner or load-testing guarantee. Expiry and lazy cleanup are described in the functional specification. Configure container upload-temp capacity, database packet limits, monitoring and backups.
5. **Scope.** No sharing, permission roles, public links, filesystem mounts or cloud synchronisation are implemented. The account boundary applies to the supplied schema and workspace service; it is not a general operating-system sandbox.

## Reporting

Do not include passwords, session cookies, dumps or private files in public issues. Use a private channel supplied by the maintainer; no security contact or SLA is invented here. Publishing educational source and exposing a running service to the Internet require different reviews. See the [executed checks and their limits](verification.md).
