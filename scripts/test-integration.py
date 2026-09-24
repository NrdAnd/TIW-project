#!/usr/bin/env python3
"""Real-file and undo checks. ONLY use a disposable local v2 MySQL/Tomcat install.
Creates synthetic accounts and data; never point this at the original database.
"""
import argparse, base64, concurrent.futures, hashlib, http.cookiejar, json, secrets
import urllib.error, urllib.parse, urllib.request

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--url", required=True)
parser.add_argument("--allow-temporary-data", action="store_true", required=True)
args = parser.parse_args()
base = args.url.rstrip("/")
if urllib.parse.urlsplit(base).hostname not in ("127.0.0.1", "localhost"):
    parser.error("Use a disposable loopback installation only.")


class Client:
    def __init__(self):
        self.opener = urllib.request.build_opener(
            urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar())
        )
        self.csrf = ""

    def request(self, endpoint, fields=None, files=None, token=None, method=None):
        headers = {}
        data = None
        if fields is not None or files is not None:
            boundary = "tiw" + secrets.token_hex(12)
            parts = []
            for name, value in fields or []:
                parts.append(
                    f'--{boundary}\r\nContent-Disposition: form-data; name="{name}"\r\n\r\n{value}\r\n'.encode()
                )
            for name, content, mime in files or []:
                parts.extend(
                    [
                        f'--{boundary}\r\nContent-Disposition: form-data; name="files"; filename="{name}"\r\nContent-Type: {mime}\r\n\r\n'.encode(),
                        content,
                        b"\r\n",
                    ]
                )
            data = b"".join(parts) + f"--{boundary}--\r\n".encode()
            headers["Content-Type"] = "multipart/form-data; boundary=" + boundary
            headers["X-CSRF-Token"] = self.csrf if token is None else token
        request = urllib.request.Request(
            base + "/" + endpoint, data=data, headers=headers, method=method
        )
        try:
            with self.opener.open(request, timeout=60) as response:
                return response.status, response.read(), response.headers
        except urllib.error.HTTPError as error:
            return error.code, error.read(), error.headers

    def ok(self, endpoint, fields=None, files=None):
        status, data, _ = self.request(endpoint, fields, files)
        assert status == 200, f"{endpoint}: {status} {data[:200]!r}"
        return data

    def get(self, endpoint):
        return json.loads(self.ok(endpoint))

    def bootstrap(self):
        self.csrf = self.get("SessionToken")["csrfToken"]

    def signup(self, username, password):
        self.ok(
            "CheckSignupCredentials",
            [
                ("username", username),
                ("email", username + "@test.invalid"),
                ("password", password),
                ("passwordCheck", password),
            ],
        )
        self.bootstrap()

    def login(self, username, password):
        self.ok(
            "CheckLoginCredentials", [("username", username), ("password", password)]
        )
        self.bootstrap()

    def history(self):
        return self.get("GetVersionHistory")

    def undo(self, index=0):
        state = self.history()
        entry = state["actions"][index]
        return json.loads(
            self.ok(
                "UndoActions",
                [("actionID", entry["id"]), ("revision", state["revision"])],
            )
        )


def flat(branch):
    yield branch
    for child in branch["children"]:
        yield from flat(child)


def folders(client):
    return [n["folder"] for n in flat(client.get("GetTree"))]


def docs(client):
    return [d for n in flat(client.get("GetTree")) for d in n["documentList"]]


def folder(client, name):
    return next(f["folderID"] for f in folders(client) if f["folderName"] == name)


def doc(client, name):
    return next(d for d in docs(client) if d["fileName"] == name)


def create(client, parent, name):
    client.ok("CreateFolder", [("newFolderName", name), ("destinationID", parent)])
    return folder(client, name)


count = 0


def passed(name):
    global count
    count += 1
    print("PASS " + name, flush=True)


a, b, anonymous = Client(), Client(), Client()
suffix = secrets.token_hex(4)
password = secrets.token_hex(10)
user = "fs" + suffix
for endpoint in [
    "GetTree",
    "GetDocument?documentID=1",
    "GetVersionHistory",
    "SessionToken",
    "DownloadFile?documentID=1",
    "PreviewFile?documentID=1",
    "UploadFiles",
    "UndoActions",
    "MoveFolder",
    "RenameItem",
]:
    assert anonymous.request(endpoint)[0] == 401, endpoint
passed("all file and undo routes require a server session")
a.signup(user, password)
b.signup("b" + suffix, password)
root = a.get("GetTree")["folder"]["folderID"]
broot = b.get("GetTree")["folder"]["folderID"]
a1 = create(a, root, "Files A")
a2 = create(a, root, "Files B")
nested = create(a, a1, "Nested")
foreign = create(b, broot, "Other owner")
passed("registration and nested folder creation work with the new schema")
assert (
    a.request(
        "CreateFolder",
        [("newFolderName", "No token"), ("destinationID", root)],
        token="invalid",
    )[0]
    == 403
)
assert not any(f["folderName"] == "No token" for f in folders(a))
passed("CSRF rejection prevents mutation")
for endpoint in [
    "CreateFolder",
    "UploadFiles",
    "MoveDocument",
    "MoveFolder",
    "RenameItem",
    "UndoActions",
    "DeleteDocument",
    "DeleteFolder",
    "Logout",
]:
    assert a.request(endpoint)[0] == 405, endpoint
passed("mutations and logout reject GET")
assert (
    a.request(
        "CreateFolder", [("newFolderName", "../escape"), ("destinationID", root)]
    )[0]
    == 400
)
assert a.request("DeleteFolder", [("folderID", root)])[0] == 400
passed("path names and workspace-root deletion are rejected")
content = b"Actual file bytes\x00\xff\n<not markup>\n"
png = base64.b64decode(
    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a2ioAAAAASUVORK5CYII="
)
a.ok(
    "UploadFiles",
    [("destinationID", nested)],
    [("Notes.txt", content, "text/plain"), ("Photo.png", png, "image/png")],
)
notes = doc(a, "Notes.txt")
photo = doc(a, "Photo.png")
assert notes["sha256"] == hashlib.sha256(content).hexdigest()
assert notes["size"] == len(content)
passed("multi-file upload persists bytes, size and digest")
status, download, headers = a.request(
    "DownloadFile?documentID=" + str(notes["documentID"])
)
assert status == 200 and download == content
assert headers["Content-Type"].startswith("application/octet-stream")
assert headers["Content-Disposition"].startswith("attachment;")
assert headers["X-Content-Type-Options"] == "nosniff"
passed("authenticated download returns exact bytes with attachment and nosniff headers")
status, image, headers = a.request("PreviewFile?documentID=" + str(photo["documentID"]))
assert (
    status == 200 and image == png and headers["Content-Type"].startswith("image/png")
), (status, len(image), headers["Content-Type"])
passed("recognised raster images have a protected inline preview")
a.ok(
    "UploadFiles",
    [("destinationID", nested)],
    [("unsafe.svg", b'<svg onload="alert(1)"></svg>', "image/png")],
)
active = doc(a, "unsafe.svg")
assert not active["canPreview"]
assert a.request("PreviewFile?documentID=" + str(active["documentID"]))[0] == 415
assert a.request("DownloadFile?documentID=" + str(active["documentID"]))[2][
    "Content-Disposition"
].startswith("attachment;")
passed("spoofed SVG MIME cannot become inline active content")
for endpoint in ["GetDocument", "DownloadFile", "PreviewFile"]:
    assert b.request(endpoint + "?documentID=" + str(photo["documentID"]))[0] == 404
passed("another owner cannot inspect, preview or download a file")
for endpoint, fields in [
    ("MoveDocument", [("documentID", notes["documentID"]), ("folderID", foreign)]),
    ("UploadFiles", [("destinationID", foreign)]),
    ("DeleteDocument", [("documentID", photo["documentID"])]),
]:
    client = b if endpoint == "DeleteDocument" else a
    assert (
        client.request(
            endpoint,
            fields,
            (
                [("Foreign.txt", b"x", "text/plain")]
                if endpoint == "UploadFiles"
                else None
            ),
        )[0]
        == 404
    )
assert doc(a, "Notes.txt")["folderID"] == nested
passed("ownership is checked before moves, uploads and deletion")
old_count = len(docs(a))
old_history = len(a.history()["actions"])
assert (
    a.request(
        "UploadFiles",
        [("destinationID", nested)],
        [("New.txt", b"ok", "text/plain"), ("Notes.txt", b"duplicate", "text/plain")],
    )[0]
    == 409
)
assert len(docs(a)) == old_count and len(a.history()["actions"]) == old_history
passed("conflicting multi-file batches roll back entirely")
assert (
    a.request(
        "UploadFiles",
        [("destinationID", nested)],
        [("../secret.txt", b"x", "text/plain")],
    )[0]
    == 400
)
assert (
    a.request(
        "UploadFiles", [("destinationID", root)], [("Root.txt", b"x", "text/plain")]
    )[0]
    == 400
)
passed("traversal filenames and file uploads into the hidden root are rejected")
assert (
    a.request(
        "UploadFiles",
        [("destinationID", nested)],
        [("Large.bin", b"x" * (25 * 1024 * 1024 + 1), "application/octet-stream")],
    )[0]
    == 413
)
assert len(docs(a)) == old_count
passed("oversized file is rejected without a partial record")
# The exact user example: undo the third most recent action also undoes the next two.
a.ok(
    "RenameItem",
    [
        ("itemType", "document"),
        ("itemID", notes["documentID"]),
        ("name", "Renamed.txt"),
    ],
)
a.ok("MoveDocument", [("folderID", a2), ("documentID", notes["documentID"])])
a.ok("DeleteDocument", [("documentID", photo["documentID"])])
state = a.history()
assert state["actions"][2]["undoCount"] == 3
assert a.undo(2)["undone"] == 3
assert (
    doc(a, "Notes.txt")["folderID"] == nested
    and doc(a, "Photo.png")["documentID"] == photo["documentID"]
)
assert a.ok("DownloadFile?documentID=" + str(photo["documentID"])) == png
passed("third-latest undo restores rename, move and deleted file together")
assert all(
    "Renamed.txt" not in entry["description"] for entry in a.history()["actions"]
)
passed("undone suffix is removed from the visible activity")
a.ok("DeleteFolder", [("folderID", a1)])
assert not any(f["folderID"] == nested for f in folders(a))
a.undo()
assert (
    folder(a, "Nested") == nested
    and a.ok("DownloadFile?documentID=" + str(notes["documentID"])) == content
)
passed("undo recursive folder deletion restores hierarchy, IDs and exact file bytes")
a.ok("MoveFolder", [("folderID", nested), ("destinationID", a2)])
assert next(f for f in folders(a) if f["folderID"] == nested)["parentFolderID"] == a2
a.undo()
assert next(f for f in folders(a) if f["folderID"] == nested)["parentFolderID"] == a1
passed("folder moves and their undo preserve nested contents")
assert a.request("MoveFolder", [("folderID", a1), ("destinationID", nested)])[0] == 400
passed("moving a folder into its descendant is rejected")
a.ok(
    "RenameItem",
    [("itemType", "folder"), ("itemID", nested), ("name", "Ricerche università")],
)
assert folder(a, "Ricerche università") == nested
a.undo()
assert folder(a, "Nested") == nested
passed("Unicode folder renaming and undo preserve identity")
before = a.history()
new = create(a, root, "Newer action")
assert (
    a.request(
        "UndoActions",
        [("actionID", before["actions"][0]["id"]), ("revision", before["revision"])],
    )[0]
    == 409
)
assert folder(a, "Newer action") == new
passed("stale undo revision is rejected without deleting newer work")
# Undoing an upload releases its data once no remaining snapshot needs it.
used = a.history()["storageUsed"]
a.ok(
    "UploadFiles",
    [("destinationID", a2)],
    [("Undo me.bin", b"abcd" * 512, "application/octet-stream")],
)
a.undo()
assert a.history()["storageUsed"] == used
passed("undoing uploads releases unreferenced file storage")
# Undo can continue backwards through the earlier chain after a cumulative restore.
history_before = len(a.history()["actions"])
first = create(a, root, "Undo chain first")
second = create(a, root, "Undo chain second")
third = create(a, root, "Undo chain third")
assert a.undo(1)["undone"] == 2
assert folder(a, "Undo chain first") == first and not any(
    f["folderID"] in (second, third) for f in folders(a)
)
assert a.undo()["undone"] == 1
assert len(a.history()["actions"]) == history_before and not any(
    f["folderID"] == first for f in folders(a)
)
passed("repeated undo continues backwards after a cumulative restore")
# Limit checks use the same production parser and cannot create partial records.
assert a.request(
    "UploadFiles",
    [("destinationID", a2)],
    [(f"Count{i}.txt", b"x", "text/plain") for i in range(21)],
)[0] in (400, 413)
assert (
    a.request(
        "CreateFolder",
        [
            ("newFolderName", "Duplicate fields"),
            ("destinationID", root),
            ("destinationID", foreign),
        ],
    )[0]
    == 400
)
assert a.request("CreateFolder", [("newFolderName", "Missing destination")])[0] == 400
assert len(a.history()["actions"]) == history_before
passed("too many files, duplicate form fields and missing IDs leave activity unchanged")
# A second logged-in session of the SAME account creates a conflict barrier.
other = Client()
other.login(user, password)
external = create(other, root, "Other session edit")
assert not a.history()["actions"][0]["undoable"]
blocked = a.history()
assert (
    a.request(
        "UndoActions",
        [("actionID", blocked["actions"][0]["id"]), ("revision", blocked["revision"])],
    )[0]
    == 409
)
local = create(a, root, "After external edit")
a.undo()
assert folder(a, "Other session edit") == external
other.ok("Logout", [])
assert not a.history()["actions"][0]["undoable"]
passed("cross-session barriers survive logout and never undo another session changes")
# Simultaneous operations from two sessions serialize without losing data.
other = Client()
other.login(user, password)
with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
    ids = list(
        pool.map(
            lambda pair: create(pair[0], root, pair[1]),
            [(a, "Concurrent A"), (other, "Concurrent B")],
        )
    )
assert len(set(ids)) == 2 and folder(a, "Concurrent A") and folder(a, "Concurrent B")
passed("concurrent sessions serialize mutations without lost folders")
other.ok("Logout", [])
assert (
    b.request(
        "UndoActions",
        [
            ("actionID", a.history()["actions"][0]["id"]),
            ("revision", b.history()["revision"]),
        ],
    )[0]
    == 409
)
passed("another owner cannot undo an action by guessing its ID")
a.ok("DeleteDocument", [("documentID", active["documentID"])])
saved_photo_id = photo["documentID"]
a.ok("Logout", [])
assert a.request("GetTree")[0] == 401
fresh = Client()
fresh.login(user, password)
assert fresh.history()["actions"] == []
assert fresh.ok("DownloadFile?documentID=" + str(saved_photo_id)) == png
passed("logout clears session undo but preserves committed current files")
fresh.ok("Logout", [])
b.ok("Logout", [])
print(
    f"\n{count} live integration checks passed. Synthetic accounts remain in the disposable database."
)
