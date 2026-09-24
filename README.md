# Document Manager

Document Manager is a Java web application for managing personal folders and files through a responsive browser interface. The backend uses Java Servlets and JDBC, the data is stored in MySQL, and the frontend is built with HTML, CSS, and vanilla JavaScript.

![Document Manager workspace](docs/images/workspace-desktop.png)

## Main features

- Account registration and session-based authentication.
- Per-user folder trees and isolated workspaces.
- Folder and document creation, renaming, moving, searching, and deletion.
- Multiple-file uploads, downloads, and previews for PNG, JPEG, GIF, and WebP images.
- Drag-and-drop file and folder management.
- Session-based undo for recent workspace changes.
- Server-side ownership checks, CSRF protection, and transactional database updates.

Upload limits are 25 MiB per file, 20 files and 100 MiB per request, and 250 MiB per account. Undo history expires after five minutes of inactivity.

## Requirements

- JDK 19 or later
- Maven 3.9 or later
- Apache Tomcat 9
- MySQL 8 or later

Tomcat 10 is not supported because the application uses the `javax.servlet` API.

## Setup

### 1. Create the database

Run the schema on a new MySQL installation:

```sh
mysql -u root -p < database/schema.sql
```

The script creates the `tiw_document_manager` database and all required tables. It does not add users or sample data.

Create a dedicated database account for the application:

```sql
CREATE USER 'document_manager'@'localhost' IDENTIFIED BY 'choose-a-private-password';
GRANT SELECT, INSERT, UPDATE, DELETE ON tiw_document_manager.*
  TO 'document_manager'@'localhost';
```

For an existing installation based on the original schema, back up the database and apply `database/migrations/002-files-and-undo.sql` once instead of importing the complete schema.

### 2. Configure Tomcat

Set `CATALINA_HOME` to the Tomcat installation directory and `CATALINA_BASE` to the directory used by the local Tomcat instance. Then copy the provided context configuration:

```sh
mkdir -p "$CATALINA_BASE/conf/Catalina/localhost"
cp config/document-manager.example.xml \
  "$CATALINA_BASE/conf/Catalina/localhost/document-manager.xml"
```

Edit the copied file and replace the database username and password placeholders. Keep this file outside the repository and do not commit credentials.

The database connection can also be configured through the `TIW_DB_URL`, `TIW_DB_USER`, `TIW_DB_PASSWORD`, and optional `TIW_DB_DRIVER` environment variables. Environment variables take precedence over the Tomcat context parameters.

## Build and run

Build the application and run the automated Java tests:

```sh
mvn clean verify
```

Deploy the generated WAR and start Tomcat:

```sh
cp target/document-manager.war "$CATALINA_BASE/webapps/"
"$CATALINA_HOME/bin/catalina.sh" start
```

Open [http://localhost:8080/document-manager/](http://localhost:8080/document-manager/) and create an account. Registration automatically creates the root folder for the new workspace.

To stop the application:

```sh
"$CATALINA_HOME/bin/catalina.sh" stop
```

The application stores uploaded file contents in MySQL. Set `max_allowed_packet` to at least 64 MiB and provide sufficient database, temporary-file, and backup storage.

## License

This project is licensed under the [Apache License 2.0](LICENSE). Bundled third-party libraries remain subject to their respective licenses.

## Updating an existing installation

Before deploying this version, back up the database and stop the old application.
For a v1 installation, first apply `database/migrations/002-files-and-undo.sql`.
For every existing v2 installation, select your database and apply:

```sh
mysql -u your_database_user -p tiw_document_manager \
  < database/migrations/003-password-hashes.sql
```

Migration 003 widens the password column without deleting accounts. Then build
this version and convert all existing plaintext passwords using the provided CLI.
Set `TIW_DB_URL`, `TIW_DB_USER`, and `TIW_DB_PASSWORD` in your local environment;
do not commit their values or pass the password as a command-line argument.

```sh
mvn clean verify
java -cp 'target/classes:target/document-manager/WEB-INF/lib/*' \
  it.polimi.tiw.tools.MigratePasswords --migrate
```

The CLI is safe to rerun: already converted hashes are left unchanged. A successful
legacy login also upgrades that one record as a fallback, but the CLI should be
run to eliminate plaintext for accounts that do not log in. Preserve exact
passwords, including case and trailing spaces. Deploy the new WAR only after
migration. On Windows, replace `:` with `;` in the Java classpath.

New databases created from `database/schema.sql` already have the widened column
and do not need migration 003. Registration stores salted PBKDF2-HMAC-SHA256 hashes
with 600,000 iterations using the JDK implementation; no password is stored in the
session user object. See the [OWASP password-storage guidance](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html).

Maven's WAR is the tested deployment artifact. Eclipse now uses the same
`document-manager` context root. If an existing Eclipse server still deploys the
old `TIW_Project_2024_RIA` context, remove that stale deployment or update its
context-specific configuration; do not leave two copies sharing the same session
expectations.

## Regression tests

`mvn clean verify` runs Java regression tests. Browser tools are development-only:

```sh
npm ci
npx playwright install chromium
```

Use a **disposable loopback Tomcat/MySQL installation** for the live suites below;
they create synthetic accounts and files. Configure MySQL's `max_allowed_packet`
to at least 64 MiB. They must never target your real database.

```sh
export TIW_LIVE_URL=http://127.0.0.1:8080/document-manager
export TIW_ALLOW_TEMPORARY_DATA=1
python3 scripts/test-integration.py --url "$TIW_LIVE_URL" --allow-temporary-data
python3 scripts/audit-api.py --url "$TIW_LIVE_URL" --allow-temporary-data
node scripts/test-ui-live.cjs
node scripts/audit-browser.cjs
```

To use installed Google Chrome, also set `TIW_BROWSER_CHANNEL=chrome`.
`audit-api.py` accepts `--mysql-defaults /path/to/disposable-client.cnf` to include
password-storage, undo-expiry cleanup, and the full 250 MiB quota tests. The client
file must refer to that disposable database; store it outside the repository.
`audit-transactions.py` takes the same options and installs temporary rejecting
triggers to test rollback; run it alone on the disposable instance. It removes
its triggers in `finally`. After forcibly interrupting it, remove its
`audit_reject_root` and `audit_reject_journal` triggers before rerunning tests.

To exercise old DAO compatibility and password upgrading as well:

```sh
mkdir -p target/legacy-audit
javac -cp 'target/classes:target/document-manager/WEB-INF/lib/*' \
  -d target/legacy-audit scripts/LegacyDaoAudit.java
java -cp 'target/legacy-audit:target/classes:target/document-manager/WEB-INF/lib/*' \
  LegacyDaoAudit /path/to/disposable-context.xml --allow-temporary-data
```

The context XML uses the same `Parameter` entries as
`config/document-manager.example.xml`, with a disposable loopback JDBC URL.

The GitHub Actions `live` job deploys the real WAR on Tomcat 9 with a disposable
MySQL service and runs the live API/browser/transaction/legacy suites. The
separate preview job still checks the simulated UI; it is not a substitute for
the live tests. For the local simulation, start `python3 scripts/preview.py`, then
run `npm test` in another terminal. Its login/signup routes deliberately do not
create accounts.

If a workspace request fails, dependent buttons are disabled and **Retry
workspace** reloads the server session, tree, and activity. A new browser tab
uses the existing server session. Redundant login attempts preserve that session;
use **Sign out** when intentionally changing accounts.
