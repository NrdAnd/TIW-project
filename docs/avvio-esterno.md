# External startup guide

This procedure starts the application using only Terminal, MySQL and Tomcat. Codex, Eclipse and the JavaScript preview are not required.

## 1. Check the requirements

You need:

- macOS or Linux;
- JDK 19 or newer;
- Maven 3.9 or newer;
- MySQL 8 or newer;
- Tomcat 9.

Check the installed versions:

```sh
java -version
mvn -version
mysql --version
```

## 2. Open the project in Terminal

Replace the path with the location of your project copy:

```sh
cd "/path/to/TIW_Project_2024_RIA"
```

## 3. Start MySQL

If MySQL was installed with the official package, open **System Settings → MySQL → Start MySQL Server**.

Alternatively, start it from Terminal:

```sh
sudo /usr/local/mysql/support-files/mysql.server start
```

Check that it is responding:

```sh
mysqladmin -u root -p ping
```

Enter the administrator password when prompted. Do not put the password directly in the command.

## 4. Prepare the database

For a new installation only, import the schema:

```sh
mysql -u root -p < database/schema.sql
```

If the database already contains data, do not run this command. Follow the migration procedure in [setup.md](setup.md) instead.

## 5. Configure Tomcat outside the repository

Set the path to your Tomcat installation:

```sh
export CATALINA_HOME="/path/to/tomcat-9"
export CATALINA_BASE="$HOME/tomcat-document-manager"
mkdir -p "$CATALINA_BASE"
```

Create the local configuration:

```sh
if [ ! -d "$CATALINA_BASE/conf" ]; then
  cp -R "$CATALINA_HOME/conf" "$CATALINA_BASE/conf"
fi
mkdir -p "$CATALINA_BASE/conf/Catalina/localhost"
cp config/document-manager.example.xml \
  "$CATALINA_BASE/conf/Catalina/localhost/document-manager.xml"
chmod 600 "$CATALINA_BASE/conf/Catalina/localhost/document-manager.xml"
```

Open the copied file and set `dbUrl`, `dbUser` and `dbPassword`. Keep this file inside `CATALINA_BASE`, outside the repository.

## 6. Build and install the application

```sh
mvn clean verify
mkdir -p "$CATALINA_BASE/webapps"
cp target/document-manager.war "$CATALINA_BASE/webapps/"
```

## 7. Start Tomcat

```sh
"$CATALINA_HOME/bin/catalina.sh" start
```

Open the application at:

```text
http://localhost:8080/document-manager/index.html
```

Register an account or sign in with an account already present in the database.

## 8. Stop the application

```sh
"$CATALINA_HOME/bin/catalina.sh" stop
```

When it is no longer needed, you can stop MySQL as well:

```sh
sudo /usr/local/mysql/support-files/mysql.server stop
```

## Common problems

- `mvn: command not found`: install Maven and reopen Terminal.
- `mysql: command not found`: add `/usr/local/mysql/bin` to `PATH`, or use the full path to the MySQL programs.
- `Port 8080 already in use`: stop the other Tomcat instance or change the HTTP port in `CATALINA_BASE/conf/server.xml`.
- `Database configuration is unavailable`: check `document-manager.xml` and restart Tomcat.
- The page does not show a recent change: rebuild the WAR, replace it in `webapps` and restart Tomcat.

Use only a disposable database when testing the application with temporary accounts and files. The live tests are described in [testing.md](testing.md).
