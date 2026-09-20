# Changelog

## 1.1.0 — Real files and cumulative session undo

- Real multi-file uploads, authenticated downloads and raster image previews.
- Rename and move folders/files through buttons or drag and drop.
- Cumulative session undo restores selected and newer actions atomically, including deleted content.
- Owner-scoped transactions, mutation CSRF tokens, POST-only changes, quotas and safe filename rules.
- Politecnico di Milano logo in the workspace header and alongside the access-page brand.
- Explicit v2 database migration, updated API/security/setup documentation and live browser checks.

## Workspace refresh

- Retained the navy/teal identity and the original hierarchical document workflow.
- Redesigned login, registration and workspace with local CSS, responsive layouts, labelled controls and keyboard-visible actions.
- Added local search, real folder/document counts, a details inspector and a newest-first session activity view.
- Added a select-based alternative to drag-and-drop document moves and consistent deletion confirmation.
- Preserved servlet payloads, multipart order, session filters, DAO queries and server validation rules.
- Added visible request failures, timeouts, duplicate-submit prevention and form preservation on failed creation.
- Externalised database credentials; retained a placeholder Tomcat context template.
- Added Maven WAR packaging, Java regression tests, browser checks, CI, a loopback-only fictional preview and documentation.
- Replaced the removed Tomcat `ServletFileUpload` wrapper with `FileUpload` plus an explicit factory, verified against Tomcat 9.0.122.
- Added a clearly labelled, reconstructed MySQL bootstrap for new databases only.

Existing security limitations and historical credential exposure are documented in `docs/security.md`. No deployment, database migration or remote push is performed by this change.
