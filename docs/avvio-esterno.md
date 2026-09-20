# Avvio esterno al progetto

Questa procedura avvia l'applicazione usando soltanto Terminale, MySQL e Tomcat. Codex, Eclipse e la preview JavaScript non sono necessari.

## 1. Controllare i prerequisiti

Servono:

- macOS o Linux;
- JDK 19 o più recente;
- Maven 3.9 o più recente;
- MySQL 8 o più recente;
- Tomcat 9.

Controlla le versioni:

```sh
java -version
mvn -version
mysql --version
```

## 2. Aprire il progetto nel Terminale

Sostituisci il percorso con quello della tua copia del progetto:

```sh
cd "/percorso/del/progetto/TIW_Project_2024_RIA"
```

## 3. Avviare MySQL

Se MySQL è stato installato con il pacchetto ufficiale, puoi avviarlo da **Impostazioni di Sistema → MySQL → Start MySQL Server**.

In alternativa, da Terminale:

```sh
sudo /usr/local/mysql/support-files/mysql.server start
```

Verifica che risponda:

```sh
mysqladmin -u root -p ping
```

Inserisci la password amministrativa quando richiesto. Non scrivere la password direttamente nel comando.

## 4. Preparare il database

Solo per una nuova installazione, importa lo schema:

```sh
mysql -u root -p < database/schema.sql
```

Se il database esiste già e contiene dati, non eseguire questo comando. Segui invece la procedura di migrazione descritta in [setup.md](setup.md).

## 5. Configurare Tomcat fuori dal repository

Imposta il percorso dell'installazione Tomcat:

```sh
export CATALINA_HOME="/percorso/tomcat-9"
export CATALINA_BASE="$HOME/tomcat-document-manager"
mkdir -p "$CATALINA_BASE"
```

Prepara la configurazione locale:

```sh
if [ ! -d "$CATALINA_BASE/conf" ]; then
  cp -R "$CATALINA_HOME/conf" "$CATALINA_BASE/conf"
fi
mkdir -p "$CATALINA_BASE/conf/Catalina/localhost"
cp config/document-manager.example.xml \
  "$CATALINA_BASE/conf/Catalina/localhost/document-manager.xml"
chmod 600 "$CATALINA_BASE/conf/Catalina/localhost/document-manager.xml"
```

Apri il file appena copiato e imposta `dbUrl`, `dbUser` e `dbPassword`. Il file deve restare dentro `CATALINA_BASE`, fuori dal repository.

## 6. Compilare e installare l'applicazione

```sh
mvn clean verify
mkdir -p "$CATALINA_BASE/webapps"
cp target/document-manager.war "$CATALINA_BASE/webapps/"
```

## 7. Avviare Tomcat

```sh
"$CATALINA_HOME/bin/catalina.sh" start
```

Apri il browser su:

```text
http://localhost:8080/document-manager/index.html
```

Registra un account oppure accedi con un account già presente nel database.

## 8. Fermare l'applicazione

```sh
"$CATALINA_HOME/bin/catalina.sh" stop
```

Quando non serve più, puoi fermare anche MySQL:

```sh
sudo /usr/local/mysql/support-files/mysql.server stop
```

## Problemi comuni

- `mvn: command not found`: installa Maven e riapri il Terminale.
- `mysql: command not found`: aggiungi `/usr/local/mysql/bin` al `PATH` oppure usa il percorso completo dei programmi MySQL.
- `Port 8080 already in use`: ferma l'altro Tomcat oppure cambia la porta HTTP in `CATALINA_BASE/conf/server.xml`.
- `Database configuration is unavailable`: controlla il file `document-manager.xml` e riavvia Tomcat.
- La pagina non si aggiorna dopo una modifica: ricompila il WAR, sostituiscilo in `webapps` e riavvia Tomcat.

Per verificare l'applicazione con account e file temporanei usa esclusivamente un database di prova; i test live sono descritti in [testing.md](testing.md).
