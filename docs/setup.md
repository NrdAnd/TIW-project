# Setup and deployment

## Requirements

- JDK 19 or newer and Maven 3.9 or newer
- Apache Tomcat 9 (`javax.servlet`)
- MySQL 8 or newer

Tomcat 10 uses `jakarta.servlet` and cannot run this WAR without code changes.

## Database

For a new installation, run the schema as a MySQL administrator:

```sh
mysql -u root -p < database/schema.sql
```

The script creates `tiw_document_manager` and its tables. It must run against an empty installation; it does not create user accounts or sample data. Registration creates each account's root folder.

Create a database account for the application with access only to this schema:

```sql
CREATE USER 'document_manager'@'localhost' IDENTIFIED BY 'choose-a-private-password';
GRANT SELECT, INSERT, UPDATE, DELETE ON tiw_document_manager.*
  TO 'document_manager'@'localhost';
```

Keep the administrator account separate. Store database credentials outside Git and use a private password in place of the example above.

## Tomcat configuration

Copy `config/document-manager.example.xml` to:

```text
$CATALINA_BASE/conf/Catalina/localhost/document-manager.xml
```

Set `dbUrl`, `dbUser` and `dbPassword` in that local file, and restrict its permissions to the Tomcat user. The default JDBC URL in the template points to `tiw_document_manager` on `localhost:3306`. The deployed context path is `/document-manager`.

Alternatively, set `TIW_DB_URL`, `TIW_DB_USER`, `TIW_DB_PASSWORD` and optionally `TIW_DB_DRIVER` in the Tomcat process environment. Environment values take precedence over context parameters. The application does not load `.env` files automatically. Never put real credentials in Git or the WAR.

## Build and run

```sh
mvn clean verify
```

Copy `target/document-manager.war` to Tomcat's `webapps` directory and start Tomcat. With Tomcat's default port, open `http://localhost:8080/document-manager/index.html`.

For uploads, set MySQL `max_allowed_packet` to at least 64 MiB and allow enough disk space for files, temporary multipart data and backups. The application stores file bytes in MySQL.

## Troubleshooting

- **Database connection error:** confirm MySQL is running and check the JDBC URL, account permissions and Tomcat's effective configuration.
- **Missing database tables:** import `database/schema.sql` into a new database before starting the app. Do not run it over existing data.
- **HTTP 413 during upload:** check Tomcat, proxy and MySQL size limits.
- **HTTP 403 during an action:** reload the page to refresh the session and CSRF token, then sign in if needed.

See [testing](testing.md) for automated checks. The live integration suite creates accounts and files, so use a disposable database for it.
