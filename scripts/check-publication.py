#!/usr/bin/env python3
"""Check tracked source for common accidental disclosures before publication.

This is a guardrail, not a substitute for reviewing files or rotating secrets.
It reports paths and categories only; matching values are never printed.
"""

from pathlib import Path
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
files = subprocess.check_output(["git", "ls-files", "-z"], cwd=ROOT).split(b"\0")
findings = []

patterns = {
    "private key": re.compile(rb"-----BEGIN (?:RSA |OPENSSH |EC |DSA )?PRIVATE KEY-----"),
    "credential token": re.compile(
        rb"(?:gh[pousr]_[A-Za-z0-9_]{20,}|github_pat_[A-Za-z0-9_]{20,}|"
        rb"AKIA[0-9A-Z]{16}|sk-[A-Za-z0-9_-]{20,})"
    ),
    "personal home path": re.compile(
        b"(?:/" + b"Users/|/" + b"home/|C:\\\\" + b"Users\\\\)[^\\s\\\"\\x00<>]+"
    ),
    "email address": re.compile(rb"[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}", re.I),
}
safe_email_domains = (b"example.com", b"example.org", b"example.net", b"test.invalid")


def is_example_email(address):
    domain = address.rsplit(b"@", 1)[-1].lower()
    return any(domain == safe or domain.endswith(b"." + safe) for safe in safe_email_domains)


for raw_path in files:
    if not raw_path:
        continue
    name = raw_path.decode("utf-8", errors="replace")
    path = ROOT / name
    if not path.is_file():
        continue
    lower = name.lower()
    basename = path.name.lower()
    if (
        basename in {".ds_store", ".env", "id_rsa", "id_ed25519"}
        or basename.endswith((".pem", ".key", ".p12", ".pfx", ".jks", ".keystore", ".dump", ".backup", ".bak", ".sqlite", ".sqlite3"))
        or (lower.startswith("config/") and (".local." in basename or basename == "document-manager.xml"))
    ):
        findings.append((name, "private file path"))
    data = path.read_bytes()
    if b"\0" in data[:4096]:
        continue
    for kind, pattern in patterns.items():
        matches = pattern.findall(data)
        if kind == "email address":
            matches = [m for m in matches if not is_example_email(m)]
        if matches:
            findings.append((name, kind))

web_xml = ROOT / "src/main/webapp/WEB-INF/web.xml"
if web_xml.is_file():
    root = ET.parse(web_xml).getroot()
    for param in root.iter():
        if param.tag.rsplit("}", 1)[-1] != "context-param":
            continue
        values = {child.tag.rsplit("}", 1)[-1]: (child.text or "").strip() for child in param}
        if values.get("param-name") in {"dbUrl", "dbUser", "dbPassword"} and values.get("param-value"):
            findings.append((str(web_xml.relative_to(ROOT)), "embedded database configuration"))

if findings:
    for name, kind in sorted(set(findings)):
        print(f"{name}: {kind}", file=sys.stderr)
    sys.exit(1)
print(f"Publication check passed for {len(files) - 1} tracked paths.")
