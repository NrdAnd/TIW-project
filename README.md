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
