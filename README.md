# Document Manager

A personal file workspace built for **Tecnologie Informatiche per il Web — Politecnico di Milano, 2024**. Java Servlets, JDBC and vanilla JavaScript, with a contemporary interface that retains the original folder tree, document inspector and session workflow.

![Document Manager — fictional preview workspace](docs/images/workspace-desktop.png)

## Features

- Upload real photos, PDFs, office documents, archives and other files; download their original bytes.
- Preview PNG, JPEG, GIF and WebP images inside the authenticated workspace.
- Create, rename, move and delete nested folders and files. Folder operations include their descendants.
- Use ordinary buttons and destination selectors, or drag files/folders to move them. Drop files from your computer onto a folder to upload, or onto the upload zone to choose a destination.
- **Undo cumulatively from Session activity.** Selecting the third-most-recent action undoes that action and the two newer ones together, restoring file contents, names and locations. There is no redo.
- Search the loaded tree, inspect file details and storage usage, and work on desktop or mobile.
- Keep existing metadata-only documents readable; they are marked as legacy metadata and do not invent downloadable content.

Undo is available during the active session, with a five-minute inactivity expiry. It cannot cross a change made by another session. Files removed from the visible tree still count towards storage while retained for undo. Limits: **25 MiB per file, 20 files / 100 MiB per upload, 250 MiB per account**, folder depth 40 and 10,000 items including the hidden root. The interface abbreviates these binary units as MB.

This is an application-managed file library backed by MySQL, not direct access to the server's operating-system filesystem. It does not execute files, unpack archives, synchronise local directories or provide an antivirus service.

## Try the interface

Requires Python 3.9+:

```sh
python3 scripts/preview.py
```

Open [the local preview](http://127.0.0.1:8765/homepage.html). The real interface uses **fictional data held in memory** and a visible preview banner. You can upload and download real files to try the flow, but everything disappears when the preview process restarts. Preview has no real accounts or access control; it binds only to `127.0.0.1` and must never be deployed. The Java WAR does not contain this tool.

## Build and deploy

Requires **JDK 19+** (tested with JDK 21), **Maven 3.9+**, **Tomcat 9** (tested with 9.0.122) and **MySQL 8+**.

```sh
mvn clean verify
```

The result is `target/document-manager.war`. Configure database credentials outside Git and follow the [setup guide](docs/setup.md).

**Database setup is required:** new installations use `database/schema.sql`. Installations using the previous supplied schema must back up their database, stop the old application and apply `database/migrations/002-files-and-undo.sql` once. Do not run the bootstrap over an existing database. An older custom schema needs the compatibility review described in the setup guide. No migration runs automatically.

## Verification

```sh
mvn clean verify
npm ci
npx playwright install chromium
# Start scripts/preview.py in another terminal, with fresh preview data.
npm test
```

The Java suite tests filters, configuration and upload-name/content rules. The browser suite exercises the actual UI against the local preview. A separate opt-in HTTP suite tests the deployed WAR with a disposable database:

```sh
python3 scripts/test-integration.py --url http://127.0.0.1:8087/document-manager --allow-temporary-data
```

That command creates test accounts and files: **use a disposable database only**. See the [verification report](docs/verification.md) for executed checks and limits. GitHub Actions runs Java and browser checks; it does not provision the live database suite.

## Documentation

- [Setup, database migration and deployment](docs/setup.md)
- [Functional specification and API contracts](docs/architecture.md)
- [Security controls and remaining limitations](docs/security.md)
- [Verification report](docs/verification.md)

One main README provides the entry point; the focused guides contain the operational details. The original assignment specification was not supplied; the functional document describes this implementation and is not presented as an official university brief.

## Publication

The release preserves the original sequence of commits with rewritten hashes. Historical database credentials and personal machine paths were replaced; current deployment credentials are kept outside Git. Run `python3 scripts/check-publication.py` before publishing changes; CI runs the same check. Rotate the formerly exposed database credential before using that account again. Old clones, forks and cached commit views require separate cleanup; do not merge or push the old history into this repository.

The legacy login still stores passwords directly; this remains an educational project and requires authentication hardening before an Internet-facing deployment. See [security](docs/security.md). No licence was invented: confirm the original authors' and course material's rights before adding one. Politecnico marks identify the course context and do not imply endorsement.
