# Testing

## Java and WAR

```sh
mvn clean verify
```

This compiles the application, runs the Java tests and creates `target/document-manager.war`.

## Browser interface

```sh
npm ci
npx playwright install chromium
python3 scripts/preview.py
```

While the preview is running, use another terminal for `npm test`. The preview serves temporary demonstration data on `127.0.0.1:8765`; restart it before another full browser-suite run.

## Live database

Deploy the WAR to Tomcat with a **disposable MySQL database**, then run:

```sh
python3 scripts/test-integration.py \
  --url http://127.0.0.1:8087/document-manager \
  --allow-temporary-data
```

This suite creates accounts and files. Do not run it against a database containing personal data. The browser-to-database check is available with `TIW_LIVE_URL=http://127.0.0.1:8087/document-manager TIW_ALLOW_TEMPORARY_DATA=1 node scripts/test-ui-live.cjs` and has the same disposable-database requirement.

GitHub Actions runs the Java and preview browser suites on pushes and pull requests.
