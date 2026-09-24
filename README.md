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

Upload limits are 25 MiB per file, 20 files and 100 MiB of file content per request, and 250 MiB per account. The login session and the ability to undo recent changes expire after five minutes of inactivity. Uploaded file contents are stored in MySQL.

## Contents

- [Project structure](#project-structure)
- [Requirements](#requirements)
- [First-time setup](#first-time-setup)
- [Build and run](#build-and-run)
- [Updating an existing installation](#updating-an-existing-installation)
- [Optional preview and regression tests](#optional-preview-and-regression-tests)

## Project structure

```text
src/main/java/it/polimi/tiw/
  beans/         User, folder, and document data objects
  controllers/   HTTP endpoints for authentication and workspace operations
  dao/           JDBC access for users, folders, and documents
  filters/       Access checks for authenticated and unauthenticated requests
  workspace/     Workspace transactions, file rules, snapshots, and undo
  utils/         Database configuration, validation, JSON responses, password hashing
  tools/         Offline password migration CLI
src/main/webapp/
  index.html     Login page (default entry point)
  signup.html    Registration page
  homepage.html  Workspace page
  javascript/    Browser logic and asynchronous requests to the backend
  css/           Stylesheets
  resources/     Static images
  WEB-INF/       Web configuration and libraries for the Eclipse setup
src/test/java/   Java regression tests
config/          Example Tomcat context configuration
database/        New-install schema and explicit upgrade migrations
scripts/         In-memory UI preview, integration tests, and audit tools
docs/images/     Screenshots used in documentation
.github/workflows/verify.yml  CI build and regression checks
pom.xml          Maven dependencies, Java version, and WAR packaging
package.json     Development-only browser test dependencies
```

The browser loads HTML, CSS, and JavaScript from Tomcat. JavaScript calls the
Servlet endpoints, which return JSON or file contents. Authentication uses the
DAOs; workspace requests use `WorkspaceService`, which performs JDBC operations
inside transactions. MySQL stores accounts, folder/document metadata, uploaded
bytes, and the undo journal. The HTTP session identifies the signed-in user.

Maven produces `target/document-manager.war`. It resolves the WAR dependencies
from `pom.xml` and excludes the checked-in `WEB-INF/lib` copies from its input.
There is no frontend build step and no Node.js server to start.

## Requirements

| Component | Version / purpose |
| --- | --- |
| JDK | 19 or later for both Maven and Tomcat; CI uses JDK 21 |
| Maven | 3.9 or later to build and run Java tests |
| Apache Tomcat | 9, using the `javax.servlet` API; Tomcat 10+ is incompatible |
| MySQL server and `mysql` client | MySQL 8.0 is the version used by CI |

Python 3, Node.js, npm, and Playwright are only needed for the optional preview
and test scripts. CI uses Node.js 22.

## First-time setup

The commands below use a POSIX shell (macOS/Linux). Run them from the repository
root, the directory containing `pom.xml`, and keep the same terminal for setup,
build, and startup. Replace example paths and credentials with your local values.
On native Windows, adapt shell commands and paths, use `catalina.bat` instead of
`catalina.sh`, and use `;` instead of `:` in Java classpaths.

For a database that already contains an older version of this application, use
[Updating an existing installation](#updating-an-existing-installation) instead
of importing the new-install schema.

### 1. Check Java and configure the local Tomcat paths

Install the required tools and unpack Tomcat 9. For a single local Tomcat instance:

```sh
export JAVA_HOME="/absolute/path/to/jdk"
export PATH="$JAVA_HOME/bin:$PATH"
export CATALINA_HOME="/absolute/path/to/apache-tomcat-9"
export CATALINA_BASE="$CATALINA_HOME"

java -version
mvn -version
"$CATALINA_HOME/bin/catalina.sh" version
```

Check that Maven and Tomcat both report JDK 19 or later. `CATALINA_HOME` contains
the Tomcat installation; `CATALINA_BASE` contains this instance's configuration,
webapps, logs, and temporary files. If you already use a separate instance,
point `CATALINA_BASE` to that fully configured directory instead.

### 2. Create and configure the database

Start MySQL first. Using a database administrator account, run:

```sh
mysql -u root -p < database/schema.sql
```

The script creates `tiw_document_manager` and all required tables, including
file storage and undo support. The database must not already exist. The script
does not insert users or sample data, and must not be followed by migrations
002 or 003 on a fresh installation.

Open an administrator SQL session with `mysql -u root -p`, then run:

```sql
CREATE USER 'document_manager'@'localhost' IDENTIFIED BY 'choose-a-private-password';
GRANT SELECT, INSERT, UPDATE, DELETE ON tiw_document_manager.*
  TO 'document_manager'@'localhost';

SHOW GLOBAL VARIABLES LIKE 'max_allowed_packet';
```

Use your own password. These examples assume MySQL and Tomcat run on the same
machine, with MySQL listening on port 3306. For another host or port, adjust the
JDBC URL and the database account's allowed host accordingly.

Uploads need a MySQL `max_allowed_packet` of at least 64 MiB (67,108,864 bytes).
If the reported value is lower, run the following as the database administrator
before starting the application:

```sql
SET GLOBAL max_allowed_packet = 67108864;
```

That change applies to new database connections. To retain it after a MySQL
restart, set `max_allowed_packet=64M` (or higher) in the MySQL server's `[mysqld]`
configuration. Provide sufficient database, temporary-file, and backup storage.
Exit the SQL client with `exit` before continuing with the shell commands.

### 3. Configure the application's database connection

Copy the provided context configuration into the Tomcat instance:

```sh
mkdir -p "$CATALINA_BASE/conf/Catalina/localhost"
cp config/document-manager.example.xml \
  "$CATALINA_BASE/conf/Catalina/localhost/document-manager.xml"
```

Edit the copied file. Set `dbUser` to `document_manager` and `dbPassword` to the
password chosen above. The default `dbUrl` is
`jdbc:mysql://localhost:3306/tiw_document_manager`; change it if necessary.
Keep `dbDriver` as `com.mysql.cj.jdbc.Driver`. In XML attribute values, escape
special characters such as `&` as `&amp;` and `"` as `&quot;`.

Keep the configured file outside the repository and do not commit credentials.
Its name, `document-manager.xml`, must match the WAR/context name used below.

Alternatively, configure the connection using environment variables instead of
copying the XML file:

| Environment variable | Tomcat context parameter | Value |
| --- | --- | --- |
| `TIW_DB_URL` | `dbUrl` | Full JDBC URL, including the database name |
| `TIW_DB_USER` | `dbUser` | Application database username |
| `TIW_DB_PASSWORD` | `dbPassword` | Application database password (nonempty) |
| `TIW_DB_DRIVER` | `dbDriver` | Optional; defaults to `com.mysql.cj.jdbc.Driver` |

Variables must be exported in the environment of the process that starts Tomcat.
Even an empty environment variable overrides its matching XML parameter, so
remove stale overrides when using the XML configuration. A `.env` file is not
loaded automatically. Restart Tomcat after changing its environment.

## Build and run

From the repository root, build the WAR and run the Java regression tests:

```sh
mvn clean verify
```

A successful build ends with `BUILD SUCCESS` and produces
`target/document-manager.war`. This command does not start Tomcat and does not
run the browser or live database suites. Maven downloads the required Java
libraries; no manual JDBC driver copy is needed for this WAR.

With the target Tomcat instance stopped, deploy the WAR and start it:

```sh
cp target/document-manager.war "$CATALINA_BASE/webapps/"
"$CATALINA_HOME/bin/catalina.sh" start
```

Open [http://localhost:8080/document-manager/](http://localhost:8080/document-manager/)
after deployment completes. Port 8080 is the default; use your configured
Tomcat HTTP port if different. Do not open the HTML files directly from disk.

Create an account on the registration page. Registration also creates the
workspace root folder and signs you in. As a first check, create a folder, upload
a small file, and download it again; this exercises the database as well as the
page loading. There is no preconfigured demo account.

To stop Tomcat and this application:

```sh
"$CATALINA_HOME/bin/catalina.sh" stop
```

### Troubleshooting startup

| Symptom | What to check |
| --- | --- |
| Compilation rejects Java release 19, or Tomcat reports `UnsupportedClassVersionError` | Check `mvn -version` and `catalina.sh version`; both must use JDK 19 or later. |
| Browser cannot connect | Check that Tomcat started and that its configured HTTP port is available. |
| `/document-manager/` returns 404 | Check WAR deployment, context name, and startup errors in `$CATALINA_BASE/logs/`. |
| Login or registration reports that the database is unavailable | Check MySQL, the JDBC URL, credentials, account grants, and any `TIW_DB_*` overrides in Tomcat's environment. |
| Operations fail with missing tables or columns in the server logs | Check that you used the complete fresh schema or applied the required upgrade migrations to the database in the JDBC URL. |
| Small uploads work but larger ones fail | Check the application limits and MySQL `max_allowed_packet`; restart Tomcat after changing the global value so its connections are renewed. |

For live startup output, stop the instance and run
`"$CATALINA_HOME/bin/catalina.sh" run` in the foreground; use Ctrl+C to stop it.

## License

This project is licensed under the [Apache License 2.0](LICENSE). Bundled third-party libraries remain subject to their respective licenses.

## Updating an existing installation

Before deploying this version, back up the database and stop the old application.
Migrations are manual: application startup does not apply them. Use a database
administrator account for schema changes; the restricted application account
created above does not have `ALTER` or `CREATE` privileges.

Choose the upgrade path based on the existing database:

| Existing schema | Required steps |
| --- | --- |
| Original v1 (without `FileBlob`, `WorkspaceState`, and `WorkspaceAction`) | Apply 002 once, then 003, then convert passwords with the CLI below. |
| v2 with file/undo tables but the old password column | Apply 003, then convert passwords with the CLI below. |
| Current schema with the widened password column | No DDL migration is needed; run the CLI if legacy plaintext passwords remain. |
| New database created with the current `database/schema.sql` | Neither migration nor password conversion is needed. |

For v1 only, select the existing database and apply migration 002:

```sh
mysql -u root -p tiw_document_manager < database/migrations/002-files-and-undo.sql
```

Then, for v1 or v2 that still needs the widened password column:

```sh
mysql -u root -p tiw_document_manager < database/migrations/003-password-hashes.sql
```

Replace the administrator username and database name if your installation uses
different values. Do not reapply migration 002: it adds columns and tables that
must not already exist. Migration 003 widens the password column without
converting or deleting existing values.

Build this version and convert the legacy passwords using the provided CLI.
Export `TIW_DB_URL`, `TIW_DB_USER`, and `TIW_DB_PASSWORD` for the existing database
in the terminal running Java, even if Tomcat uses XML configuration: this CLI
reads only those environment variables. The application database account has
the `SELECT` and `UPDATE` privileges needed for this step. Do not commit these
values or pass the password as a command-line argument.

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

## Optional preview and regression tests

`mvn clean verify` runs Java regression tests without requiring a running Tomcat
or MySQL instance. The commands below are additional development checks.
Install Python 3 and Node.js/npm (CI uses Node.js 22), then install the browser
tools from the repository root:

```sh
npm ci
npx playwright install chromium
```

### Live application tests

First build and deploy the application using the setup above, with a dedicated
throwaway database and Tomcat instance. Leave Tomcat running during these tests.
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
`audit-transactions.py` requires `--mysql-defaults` and a database administrator
account able to create and drop triggers; the restricted application account
is not sufficient. Run it alone on the disposable instance:

```sh
python3 scripts/audit-transactions.py --url "$TIW_LIVE_URL" \
  --allow-temporary-data --mysql-defaults /path/to/disposable-client.cnf
```

It installs temporary rejecting triggers to test rollback and removes
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
`config/document-manager.example.xml`. This Java audit specifically requires a
JDBC URL starting with `jdbc:mysql://127.0.0.1:` (including an explicit port).

The GitHub Actions `live` job deploys the real WAR on Tomcat 9 with a disposable
MySQL service and runs the live API/browser/transaction/legacy suites. The
`verify` job also checks the simulated UI; it is not a substitute for the live
tests.

### UI preview without Tomcat or MySQL

For a local simulation with fictional, in-memory data, run:

```sh
python3 scripts/preview.py
```

Open [http://127.0.0.1:8765/homepage.html](http://127.0.0.1:8765/homepage.html).
To test this preview, leave it running and execute `npm test` in another terminal
from the repository root. Stop the preview with Ctrl+C. Preview data is lost when
the process exits; its login/signup routes deliberately do not create accounts.
Use the Tomcat deployment to check real authentication and persistent storage.

## Session and workspace behavior

If a workspace request fails, dependent buttons are disabled and **Retry
workspace** reloads the server session, tree, and activity. A new browser tab
uses the existing server session. Redundant login attempts preserve that session;
use **Sign out** when intentionally changing accounts.
