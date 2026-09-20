#!/usr/bin/env python3
"""Loopback-only, in-memory preview. No database, authentication or durable storage.
Uploads stay in this process's memory and are lost on restart; use fictional files.
Never deploy this tool. Production bytes/undo live in MySQL behind authenticated servlets.
"""
import argparse, copy, hashlib, json, mimetypes, re, secrets, unicodedata
from datetime import datetime
from email.parser import BytesParser
from email.policy import default
from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path
from urllib.parse import urlsplit, parse_qs, quote

ROOT = Path(__file__).resolve().parents[1] / "src/main/webapp"
MAX_FILE, MAX_REQUEST, QUOTA = 25 * 1024 * 1024, 100 * 1024 * 1024, 250 * 1024 * 1024
TOKEN = secrets.token_hex(32)
FOLDERS = [
    dict(folderID=1, folderName="Homepage", parentFolderID=0, depth=0),
    dict(folderID=2, folderName="University", parentFolderID=1, depth=1),
    dict(folderID=3, folderName="Web Technologies", parentFolderID=2, depth=2),
    dict(folderID=4, folderName="Personal projects", parentFolderID=1, depth=1),
    dict(folderID=5, folderName="Reading room", parentFolderID=1, depth=1),
]
DOCS = []
HISTORY = []
REVISION = 0
NEXT_FOLDER = 6
NEXT_DOC = 1
NEXT_ACTION = 1


def now():
    return datetime.now().strftime("%Y-%m-%d %H:%M:%S")


def media(data):
    if data.startswith(b"\x89PNG\r\n\x1a\n"):
        return "image/png"
    if data.startswith(b"\xff\xd8\xff"):
        return "image/jpeg"
    if data.startswith((b"GIF87a", b"GIF89a")):
        return "image/gif"
    if data[:4] == b"RIFF" and data[8:12] == b"WEBP":
        return "image/webp"
    return "application/octet-stream"


def filename(doc):
    return doc["documentName"] + (
        "." + doc["documentType"] if doc["documentType"] else ""
    )


def public(doc):
    result = {k: v for k, v in doc.items() if not k.startswith("_")}
    result.update(fileName=filename(doc), hasFile="_content" in doc)
    if "_content" in doc:
        result.update(
            size=len(doc["_content"]),
            mediaType=media(doc["_content"]),
            canPreview=media(doc["_content"]).startswith("image/"),
            sha256=hashlib.sha256(doc["_content"]).hexdigest(),
        )
    return result


def state():
    return copy.deepcopy((FOLDERS, DOCS))


def record(description, before):
    global NEXT_ACTION, REVISION
    HISTORY.append(
        dict(
            id=str(NEXT_ACTION),
            description=description,
            createdAt=now(),
            _before=before,
        )
    )
    NEXT_ACTION += 1
    REVISION += 1


def used():
    blobs = {d["_blob"]: len(d["_content"]) for d in DOCS if "_blob" in d}
    for action in HISTORY:
        blobs.update(
            {
                d["_blob"]: len(d["_content"])
                for d in action["_before"][1]
                if "_blob" in d
            }
        )
    return sum(blobs.values())


def tree(folder):
    return dict(
        folder=folder,
        documentList=[public(d) for d in DOCS if d["folderID"] == folder["folderID"]],
        children=[
            tree(f) for f in FOLDERS if f["parentFolderID"] == folder["folderID"]
        ],
    )


def add_file(folder, name, content):
    global NEXT_DOC
    stem, extension = split(name)
    DOCS.append(
        dict(
            documentID=NEXT_DOC,
            documentName=stem,
            documentType=extension,
            folderID=folder,
            summary="",
            creationDate=now(),
            _content=content,
            _blob=secrets.token_hex(12),
        )
    )
    NEXT_DOC += 1


def valid_name(value, limit=200):
    value = unicodedata.normalize("NFC", value.strip())
    if (
        not value
        or value in (".", "..")
        or len(value) > limit
        or value.endswith(".")
        or re.search(r'[\\/:*?"<>|\x00-\x1f\x7f]', value)
    ):
        raise ValueError("Invalid name or path separator.")
    return value


def split(value):
    value = valid_name(value)
    dot = value.rfind(".")
    stem, extension = (value[:dot], value[dot + 1 :]) if dot > 0 else (value, "")
    if len(stem) > 180 or len(extension) > 20:
        raise ValueError("File name too long.")
    return stem, extension


def subtree(identifier):
    ids = {identifier}
    while True:
        children = {f["folderID"] for f in FOLDERS if f["parentFolderID"] in ids}
        if children <= ids:
            return ids
        ids |= children


for parent, name, text in [
    (2, "Course overview.txt", "Course notes and milestones. Fictional preview file."),
    (
        3,
        "Project brief.txt",
        "Document Manager: real files, folders and cumulative session undo.",
    ),
    (
        3,
        "Interface notes.txt",
        "A calmer workspace, with a familiar structure and clearer hierarchy.",
    ),
    (4, "Ideas for later.txt", "A collection of ideas to revisit."),
    (5, "Design references.txt", "Notes on useful interface patterns."),
]:
    add_file(parent, name, text.encode())
# Three honest, reversible example actions. Selecting the third also reverses the newer two.
before = state()
add_file(
    3, "Campus identity.png", (ROOT / "resources/images/polimi_logo.png").read_bytes()
)
record("Uploaded “Campus identity.png”", before)
before = state()
DOCS[-1]["documentName"] = "Polimi identity"
record("Renamed “Campus identity.png” to “Polimi identity.png”", before)
before = state()
DOCS[-1]["folderID"] = 5
record("Moved file “Polimi identity.png”", before)


class Handler(BaseHTTPRequestHandler):
    def host_allowed(self):
        return self.headers.get("Host", "").split(":")[0] in ("127.0.0.1", "localhost")

    def reply(
        self, status, body, content_type="text/plain; charset=utf-8", headers=None
    ):
        if isinstance(body, str):
            body = body.encode()
        self.send_response(status)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.send_header("X-Content-Type-Options", "nosniff")
        for key, value in (headers or {}).items():
            self.send_header(key, value)
        self.end_headers()
        self.wfile.write(body)

    def data(self, value):
        return self.reply(200, json.dumps(value), "application/json")

    def do_GET(self):
        if not self.host_allowed():
            return self.reply(403, "Local preview host required.")
        url = urlsplit(self.path)
        route = url.path.lstrip("/")
        if route == "SessionToken":
            return self.data(
                dict(
                    csrfToken=TOKEN,
                    maxFileSize=MAX_FILE,
                    maxRequestSize=MAX_REQUEST,
                    storageLimit=QUOTA,
                )
            )
        if route == "GetTree":
            return self.data(tree(FOLDERS[0]))
        if route == "GetVersionHistory":
            return self.data(
                dict(
                    actions=[
                        dict(
                            id=a["id"],
                            description=a["description"],
                            createdAt=a["createdAt"],
                            undoable=True,
                            undoCount=i + 1,
                        )
                        for i, a in enumerate(reversed(HISTORY))
                    ],
                    revision=str(REVISION),
                    storageUsed=used(),
                    storageLimit=QUOTA,
                )
            )
        if route in ("GetDocument", "DownloadFile", "PreviewFile"):
            identifier = parse_qs(url.query).get("documentID", [""])[0]
            doc = next((d for d in DOCS if str(d["documentID"]) == identifier), None)
            if doc is None:
                return self.reply(404, "File not found.")
            if route == "GetDocument":
                return self.data(public(doc))
            if "_content" not in doc:
                return self.reply(404, "No file content.")
            preview = route == "PreviewFile"
            mime = media(doc["_content"])
            if preview and not mime.startswith("image/"):
                return self.reply(415, "Download this file instead.")
            return self.reply(
                200,
                doc["_content"],
                mime if preview else "application/octet-stream",
                {
                    "Content-Disposition": ("inline" if preview else "attachment")
                    + "; filename=\"download\"; filename*=UTF-8''"
                    + quote(filename(doc)),
                    "Content-Security-Policy": "default-src 'none'; sandbox",
                },
            )
        if route == "__preview.js":
            return self.reply(
                200,
                """
if(location.pathname.endsWith('homepage.html'))sessionStorage.setItem('utente','Demo · Preview');
document.addEventListener('DOMContentLoaded',()=>{const e=document.createElement('div');e.textContent='LOCAL PREVIEW · Fictional workspace · Uploads stay in memory and disappear on restart';e.style.cssText='padding:7px 14px;background:#c6f4df;color:#244d3e;text-align:center;font:10px system-ui;letter-spacing:.04em';document.body.prepend(e);});
""",
                "text/javascript",
            )
        route = route or "homepage.html"
        if not (
            route in ("index.html", "signup.html", "homepage.html")
            or route.startswith(("css/", "javascript/", "resources/images/"))
        ):
            return self.reply(404, "Not found")
        path = (ROOT / route).resolve()
        if ROOT not in path.parents or not path.is_file():
            return self.reply(404, "Not found")
        body = path.read_bytes()
        if path.suffix == ".html":
            body = body.replace(
                b"</head>", b'<script src="__preview.js"></script></head>'
            )
        return self.reply(
            200, body, mimetypes.guess_type(str(path))[0] or "application/octet-stream"
        )

    def do_POST(self):
        global FOLDERS, DOCS, NEXT_FOLDER, NEXT_DOC, REVISION, HISTORY
        if not self.host_allowed():
            return self.reply(403, "Local preview host required.")
        endpoint = urlsplit(self.path).path.lstrip("/")
        if endpoint in ("CheckLoginCredentials", "CheckSignupCredentials"):
            return self.reply(
                400, "Preview only: open homepage.html. No accounts are created."
            )
        if self.headers.get("X-CSRF-Token") != TOKEN:
            return self.reply(403, "Preview request token missing.")
        if endpoint == "Logout":
            return self.data(dict(ok=True))
        length = int(self.headers.get("Content-Length", 0))
        if length > MAX_REQUEST + 1024 * 1024:
            return self.reply(413, "Selection too large.")
        body = self.rfile.read(length)
        content = self.headers.get("Content-Type", "")
        message = BytesParser(policy=default).parsebytes(
            ("Content-Type: " + content + "\r\nMIME-Version: 1.0\r\n\r\n").encode()
            + body
        )
        fields = {}
        files = []
        for part in message.iter_parts():
            name = part.get_param("name", header="content-disposition")
            data = part.get_payload(decode=True) or b""
            if part.get_filename() is not None:
                files.append((part.get_filename(), data))
            else:
                fields[name] = data.decode("utf-8")
        before = state()
        try:
            if endpoint == "UndoActions":
                if str(REVISION) != fields["revision"]:
                    return self.reply(409, "Workspace changed. Refresh before undoing.")
                index = next(
                    i for i, a in enumerate(HISTORY) if a["id"] == fields["actionID"]
                )
                count = len(HISTORY) - index
                FOLDERS, DOCS = copy.deepcopy(HISTORY[index]["_before"])
                HISTORY = HISTORY[:index]
                REVISION += 1
                return self.data(dict(undone=count))
            if endpoint == "CreateFolder":
                parent = next(
                    f for f in FOLDERS if f["folderID"] == int(fields["destinationID"])
                )
                name = valid_name(fields["newFolderName"], 100)
                if any(
                    f["parentFolderID"] == parent["folderID"]
                    and f["folderName"].lower() == name.lower()
                    for f in FOLDERS
                ):
                    return self.reply(409, "A folder with that name already exists.")
                FOLDERS.append(
                    dict(
                        folderID=NEXT_FOLDER,
                        folderName=name,
                        parentFolderID=parent["folderID"],
                        depth=parent["depth"] + 1,
                    )
                )
                NEXT_FOLDER += 1
                description = "Created folder “" + name + "”"
            elif endpoint == "UploadFiles":
                parent = next(
                    f
                    for f in FOLDERS
                    if f["folderID"] == int(fields["destinationID"]) and f["depth"] > 0
                )
                if not files or len(files) > 20:
                    raise ValueError("Choose between 1 and 20 files.")
                if (
                    any(len(data) > MAX_FILE for _, data in files)
                    or sum(len(data) for _, data in files) > MAX_REQUEST
                    or used() + sum(len(data) for _, data in files) > QUOTA
                ):
                    return self.reply(413, "Upload/storage limit reached.")
                names = set()
                for name, data in files:
                    valid_name(name)
                    split(name)
                    if name.lower() in names or any(
                        d["folderID"] == parent["folderID"]
                        and filename(d).lower() == name.lower()
                        for d in DOCS
                    ):
                        return self.reply(409, "A file with that name already exists.")
                    names.add(name.lower())
                for name, data in files:
                    add_file(parent["folderID"], name, data)
                description = (
                    "Uploaded “" + files[0][0] + "”"
                    if len(files) == 1
                    else "Uploaded " + str(len(files)) + " files"
                )
            elif endpoint in ("MoveDocument", "DeleteDocument"):
                doc = next(
                    d for d in DOCS if d["documentID"] == int(fields["documentID"])
                )
                if endpoint == "MoveDocument":
                    destination = next(
                        f
                        for f in FOLDERS
                        if f["folderID"] == int(fields["folderID"]) and f["depth"] > 0
                    )
                    if destination["folderID"] == doc["folderID"]:
                        raise ValueError("Choose a different destination.")
                    if any(
                        d["folderID"] == destination["folderID"]
                        and filename(d).lower() == filename(doc).lower()
                        for d in DOCS
                    ):
                        return self.reply(409, "A file with that name already exists.")
                    doc["folderID"] = destination["folderID"]
                    description = "Moved file “" + filename(doc) + "”"
                else:
                    DOCS.remove(doc)
                    description = "Deleted file “" + filename(doc) + "”"
            elif endpoint in ("DeleteFolder", "MoveFolder"):
                folder = next(
                    f
                    for f in FOLDERS
                    if f["folderID"] == int(fields["folderID"]) and f["depth"] > 0
                )
                ids = subtree(folder["folderID"])
                if endpoint == "DeleteFolder":
                    FOLDERS[:] = [f for f in FOLDERS if f["folderID"] not in ids]
                    DOCS[:] = [d for d in DOCS if d["folderID"] not in ids]
                    description = (
                        "Deleted folder “" + folder["folderName"] + "” and its contents"
                    )
                else:
                    destination = next(
                        f
                        for f in FOLDERS
                        if f["folderID"] == int(fields["destinationID"])
                    )
                    if (
                        destination["folderID"] in ids
                        or destination["folderID"] == folder["parentFolderID"]
                    ):
                        raise ValueError("Invalid destination.")
                    if any(
                        f["parentFolderID"] == destination["folderID"]
                        and f["folderName"].lower() == folder["folderName"].lower()
                        for f in FOLDERS
                    ):
                        return self.reply(409, "A folder with that name exists.")
                    delta = destination["depth"] + 1 - folder["depth"]
                    folder["parentFolderID"] = destination["folderID"]
                    for f in FOLDERS:
                        if f["folderID"] in ids:
                            f["depth"] += delta
                    description = "Moved folder “" + folder["folderName"] + "”"
            elif endpoint == "RenameItem":
                name = valid_name(
                    fields["name"], 100 if fields["itemType"] == "folder" else 200
                )
                if fields["itemType"] == "folder":
                    f = next(
                        f
                        for f in FOLDERS
                        if f["folderID"] == int(fields["itemID"]) and f["depth"] > 0
                    )
                    old = f["folderName"]
                    f["folderName"] = name
                elif fields["itemType"] == "document":
                    d = next(
                        d for d in DOCS if d["documentID"] == int(fields["itemID"])
                    )
                    old = filename(d)
                    d["documentName"], d["documentType"] = split(name)
                else:
                    raise ValueError("Unknown item type.")
                description = "Renamed “" + old + "” to “" + name + "”"
            else:
                return self.reply(404, "Unknown preview endpoint.")
            record(description, before)
            return self.data(dict(ok=True))
        except (KeyError, ValueError, StopIteration) as error:
            FOLDERS, DOCS = before
            return self.reply(400, str(error) or "Invalid preview request.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--port", type=int, default=8765)
    args = parser.parse_args()
    print(
        f"In-memory UI preview: http://127.0.0.1:{args.port}/homepage.html", flush=True
    )
    HTTPServer(("127.0.0.1", args.port), Handler).serve_forever()
