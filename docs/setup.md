# Setup and deployment

## Runtime

- JDK 19 or newer; compilation targets Java 19. The refresh was built using JDK 21.
- Maven 3.9 or newer.
- Apache Tomcat 9, Servlet API 4.0 (`javax.servlet`), tested with 9.0.122. Multipart handling uses the standard Servlet API. Use a patched Tomcat 9 runtime and rerun integration checks when updating it. [Official Tomcat downloads](https://tomcat.apache.org/download-90.cgi).
- MySQL 8 or newer; the DAO uses recursive common table expressions.

Tomcat 10+ uses the `jakarta.servlet` namespace and requires a separate migration. [Official version compatibility](https://tomcat.apache.org/whichversion.html).

## Database

### New installation

Import `database/schema.sql` with your database administration workflow. It creates the complete v2 schema and intentionally fails if `tiw_document_manager` already exists. No accounts or sample passwords are inserted. Registration creates the hidden root folder. The schema was reconstructed from the original Java queries, then extended for real files and undo; it is not an original university DDL dump.

### Upgrade from the supplied v1 schema

1. Back up the database and verify that the backup can be restored. Stop the application and its background requests; all old sessions must end before upgrade.
2. Compare `SHOW CREATE TABLE User`, `Folder` and `Document` with the v1 section of `database/schema.sql`. The migration expects InnoDB, the existing owner relationships, the baseline columns and the document unique index named `uq_document_name`.
3. Select the intended database in your administration tool and execute `database/migrations/002-files-and-undo.sql` **once**. It widens names, scopes file uniqueness to a folder, adds nullable blob references and creates three new tables. Existing metadata stays in place and is labelled metadata-only until real files are separately uploaded.
4. Deploy the new WAR and matching frontend together, then verify login, the existing tree, an upload/download and cumulative undo with a disposable test account.

MySQL DDL auto-commits: the migration is not an all-or-nothing transaction and is not idempotent. If it fails partway, inspect which statements ran and restore the backup or repair the specific partial schema before retrying. Do not blindly rerun it. Rolling back to v1 after users have uploaded data requires restoring the pre-upgrade backup; there is no lossless downgrade script.

If your old database differs from the supplied v1 schema, adapt and review the migration against a clone first. In particular, verify owner/parent foreign keys and uniqueness rules. Do not drop unknown constraints merely to make the SQL run. An existing database with `FileBlob`, `WorkspaceState` and `WorkspaceAction` has already been upgraded; do not run the migration twice.

### Database and upload capacity

The supplied schema uses composite owner foreign keys to prevent cross-account file/folder references. Use an application-specific account with `SELECT`, `INSERT`, `UPDATE` and `DELETE` only on its schema; use a separate administration account for installation/migration.

Files are stored in MySQL `LONGBLOB` columns. Configure `max_allowed_packet` to at least 64 MiB for 25 MiB files and provision space for blobs, metadata snapshots, transaction logs and backups. The per-account quota is 250 MiB including retained undo content; it does not limit total database/journal size. Restrict Tomcat's upload temporary directory and provision disk for concurrent multipart requests. Parts spill to temporary files after 1 MiB and are removed after each request. If a reverse proxy is present, allow the 101 MiB multipart body limit and an appropriate upload timeout without exposing unlimited bodies.

Authentication still uses the original direct password comparison. Do not use passwords reused on other services. See [security findings](security.md) before public deployment.

## External configuration

`WEB-INF/web.xml` deliberately contains empty `dbUrl`, `dbUser` and `dbPassword` values. An unconfigured application fails closed instead of connecting with a bundled account.

Choose one configuration method:

### Tomcat context parameters

Copy `config/document-manager.example.xml` to:

```text
$CATALINA_BASE/conf/Catalina/localhost/document-manager.xml
```

Replace the placeholders in that local file. Keep its permissions restricted to the account that runs Tomcat, and never add it to Git. `override="false"` makes those context values take precedence over the empty application defaults. Set a JDBC URL appropriate for your database and TLS setup; do not blindly disable TLS for remote connections.

The file name determines the context path. The Maven WAR uses `/document-manager`; the original Eclipse deployment uses `/TIW_Project_2024_RIA` and therefore needs a correspondingly named context file.

### Environment variables

The Tomcat process can instead receive:

| Environment variable | Context equivalent                                  |
| -------------------- | --------------------------------------------------- |
| `TIW_DB_URL`         | `dbUrl`                                             |
| `TIW_DB_USER`        | `dbUser`                                            |
| `TIW_DB_PASSWORD`    | `dbPassword`                                        |
| `TIW_DB_DRIVER`      | `dbDriver` (defaults to `com.mysql.cj.jdbc.Driver`) |

Environment values take precedence over context values. Empty environment overrides are treated as missing configuration, not as a request to reuse another password. `.env` files are ignored by Git but are **not automatically loaded** by the application. Do not put credentials on a command line that will be saved in shell history or logs.

## Maven deployment

```sh
mvn clean verify
```

Copy `target/document-manager.war` into the configured Tomcat `webapps` directory and start/restart that local Tomcat instance using your normal workflow. Open:

```text
http://localhost:8080/document-manager/index.html
```

The Maven WAR includes Gson and MySQL Connector/J from declared dependencies. The Servlet API is `provided` by the container. Legacy JAR files remain in the repository for the original Eclipse setup but are excluded from the Maven web source copy, avoiding duplicate libraries.

## Eclipse compatibility

The existing `.project`, `.classpath` and `.settings` files are preserved. Import the original project as an existing Eclipse project, use its Java 19 execution environment, and configure a current Tomcat 9 runtime. Its web content remains `src/main/webapp`; its context root remains `TIW_Project_2024_RIA`.

The Maven build is the reproducible release path. Eclipse launch settings still depend on your local runtime configuration; do not commit machine-specific credentials or absolute server paths.

## Browser checks

```sh
npm ci
npx playwright install chromium
python3 scripts/preview.py
```

With that preview running, use another terminal:

```sh
npm test
```

Optional settings: `TIW_PREVIEW_URL` for another loopback port; `TIW_BROWSER_CHANNEL=chrome` to use an installed Chrome; `TIW_SCREENSHOT_DIR` to save screenshots. The screenshot directory must exist. Start a fresh preview process for each browser-suite run. Tests consume the three demonstration undo actions and create additional fictional folders/files; restart the process to reset them.

## Troubleshooting

- **Database configuration is missing:** supply the external parameters or environment variables to the actual Tomcat process, then restart it.
- **Cannot connect to database:** verify the JDBC URL, database availability and permissions without printing credentials into shared logs.
- **Missing WorkspaceAction/FileBlob tables or blob_id column:** apply the reviewed v2 migration before deploying the new application.
- **401 or redirected 403:** sign in again; browser storage cannot authenticate a request. A mutation 403 without a redirect indicates an invalid/missing CSRF token; reload the workspace.
- **413 on upload:** check file/batch/account limits, proxy limits, Tomcat temporary storage and the database packet setting. Retained undo bytes count towards quota.
- **Undo unavailable / 409:** another session changed the workspace, the displayed revision is stale or undo snapshots expired. Refresh activity; no partial restore was performed.
- **Preview works but Tomcat does not:** the preview substitutes only data and is not evidence of a working database deployment.

## Live integration checks

The optional `scripts/test-integration.py` suite creates two accounts and exercises real servlet/JDBC operations. Run it only against a **disposable local installation** using the supplied new schema, never against the original database:

```sh
python3 scripts/test-integration.py --url http://127.0.0.1:8087/document-manager --allow-temporary-data
```

The loopback restriction and explicit flag help prevent accidental use against another installation. Temporary accounts remain in that disposable database; discard the test database after verification. The suite is separate from the default CI job because it requires a separately configured MySQL and Tomcat instance.

A separate browser-to-database smoke test is available in `scripts/test-ui-live.cjs`; its required opt-in variables are documented in [verification](verification.md).
