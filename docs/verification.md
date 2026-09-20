# Verification report

Verified on **20 September 2026**, using JDK 21.0.6, Maven, Tomcat 9.0.122, MySQL 8.0.32 and Chrome via Playwright 1.62.1. All live database operations used a separate temporary MySQL data directory and synthetic accounts. The original application database was not read or modified.

## Results

| Layer                 | Result    | Scope                                                                                        |
| --------------------- | --------- | -------------------------------------------------------------------------------------------- |
| Clean Maven build     | PASS      | 18 production Java sources compiled; v1.1.0 WAR packaged                                     |
| Java regressions      | 14 passed | Session filters/mappings, external configuration, safe names and preview MIME rules          |
| Browser suite         | 21 passed | Real UI with preview data: uploads, downloads, undo, drag/drop, errors and responsive layout |
| Live HTTP/JDBC suite  | 28 passed | Deployed WAR, real MySQL, synthetic users, ownership, CSRF, atomic operations and undo       |
| Live browser smoke    | PASS      | Real browser → WAR → MySQL, with no preview data service                                     |
| Database installation | PASS      | v1 → v2 migration on test database; complete bootstrap in a second empty database            |
| Visual review         | Reviewed  | Workspace, Polimi branding, raster details, login and signup; desktop and mobile             |
| Responsive overflow   | PASS      | Workspace at 320, 390, 768 and 1024 px; desktop captures at 1440 px                          |

## Java checks

Six tests cover session filters and protected mappings. Three cover external database configuration and missing-setting failures. Five cover filename validation, path/header/control-character rejection, Unicode normalisation, length limits, extensionless files and strict raster-signature classification. Servlet multipart parsing is exercised against the actual container in the live tests; no Tomcat-internal parser dependency remains.

## Browser checks

`scripts/test-ui.cjs` exercises the production HTML/CSS/JavaScript with the loopback preview service. It verifies:

- Initial tree, totals, top Polimi logo and cumulative activity counts; nested search and empty results.
- Image preview and exact-byte download; cancelling undo and undoing the third-most-recent action.
- Root/subfolder creation, multiple real files selected through the normal file input, and Unicode names.
- Rename → button move → delete → cumulative restore; external file drops onto folders and the library upload area.
- Internal file and folder dragging, folder moves through the selector, recursive deletion/restore and dropping into trash.
- Oversized uploads blocked before submission, failure feedback with input preservation, stale-undo feedback and activity refresh.
- Guide keyboard support, responsive layouts, session rejection, login via Enter, network errors and signup validation.
- Inaccessible private preview paths and no uncaught JavaScript errors.

The preview suite needs a fresh preview process per run because it consumes the three reversible demonstration actions. Its backend is intentionally fictional; it does not establish JDBC security.

## Live HTTP/JDBC checks

`scripts/test-integration.py` ran against a WAR on `127.0.0.1:8087`, backed by an isolated MySQL process on `127.0.0.1:33079`. The downloaded Tomcat distribution's published SHA-512 checksum was verified. Random test credentials stayed outside the source repository.

The 28 checks cover anonymous rejection; registration and roots; CSRF rejection; GET rejection on mutations; invalid names and hidden-root protection; multi-upload metadata and SHA-256; exact bytes and attachment headers; raster preview; spoofed SVG MIME rejection; cross-owner read/preview/download rejection; source/destination ownership before mutation; duplicate-batch rollback; path traversal/root upload rejection; oversized upload rejection; the exact third-latest undo scenario; removal of undone actions; recursive folder restore with original IDs/bytes; folder move/undo; cycle rejection; Unicode folder rename/undo; stale revisions; reclaimed storage after upload undo; repeated undo farther back through history; malformed fields and file-count limits; another session's undo barrier surviving logout; simultaneous same-owner mutations without lost data; guessed foreign action IDs; and logout preserving committed files while clearing session activity.

## Live browser smoke

`scripts/test-ui-live.cjs` created a synthetic account through the real signup page. It created folders, uploaded text and PNG files, previewed the image, downloaded identical text bytes, renamed a file, dragged it to another folder, deleted the image and undid all three actions by selecting the third activity item. Both files returned to their original names and folder. Logout completed and no uncaught JavaScript errors occurred.

Run only against a disposable loopback installation:

```sh
TIW_LIVE_URL=http://127.0.0.1:8087/document-manager \
TIW_ALLOW_TEMPORARY_DATA=1 node scripts/test-ui-live.cjs
```

Use `TIW_BROWSER_CHANNEL=chrome` if relying on an installed Chrome. The live suites leave synthetic accounts in the disposable database; discard that test database after use.

## Practical limits

- No migration was applied to the original database or existing Eclipse/Tomcat installation.
- No full penetration test, dependency vulnerability audit, sustained load test, all-browser certification or screen-reader audit was performed.
- Per-file and malformed-batch rejection were exercised. Maximum account-quota saturation, 10,000-item saturation, process-crash recovery and real-time five-minute expiry were not stress-tested.
- Authentication retains legacy plaintext-password storage and session-hardening limitations; see [security](security.md).
- The supplied CI workflow is separate from these local checks. Review its run status on the repository's Actions page after each push.
- Screenshots and demo files are fictional. The local preview is in-memory; durable application storage is implemented and verified separately in MySQL.
