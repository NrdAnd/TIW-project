#!/usr/bin/env python3
"""Fault-injection checks: disposable loopback database ONLY. Do not use real data.
Uses the audit-api.py Client implementation without running its test cases.
"""
import argparse, ast, json, secrets, subprocess, sys
from pathlib import Path
p=argparse.ArgumentParser(description=__doc__);p.add_argument('--url',required=True);p.add_argument('--mysql-defaults',required=True);p.add_argument('--mysql-bin',default='mysql');p.add_argument('--allow-temporary-data',action='store_true',required=True);args=p.parse_args()
# Reuse only imports and named helper/class definitions from the sibling audit file.
module=ast.parse(Path(__file__).with_name('audit-api.py').read_text());selected=[n for n in module.body if isinstance(n,(ast.Import,ast.ImportFrom,ast.FunctionDef,ast.ClassDef))];ns={'a':args,'base':args.url.rstrip('/'),'results':[]};exec(compile(ast.Module(body=selected,type_ignores=[]),'audit-api helpers','exec'),ns)
from urllib.parse import urlsplit
if urlsplit(args.url).hostname not in ('localhost','127.0.0.1'):p.error('Loopback only')
Client,sql,check,expect=[ns[k] for k in ('Client','sql','check','expect')]
x=Client().signup();folder=x.create(x.root,'Atomicity')
def registration_rollback():
    u='rb'+secrets.token_hex(5)
    sql("CREATE TRIGGER audit_reject_root BEFORE INSERT ON Folder FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Synthetic test failure'")
    try:
        c=Client();expect(c.req('CheckSignupCredentials',[('username',u),('email',u+'@test.invalid'),('password','Synthetic_7'),('passwordCheck','Synthetic_7')])[0],500)
        expect(sql("SELECT COUNT(*) FROM User WHERE username='"+u+"'"),'0');expect(c.req('GetTree')[0],401)
    finally:sql('DROP TRIGGER audit_reject_root')
check('registration rolls back user when root creation fails',registration_rollback)
def mutation_rollback():
    before=x.get('GetVersionHistory');u=x.user
    sql("CREATE TRIGGER audit_reject_journal BEFORE INSERT ON WorkspaceAction FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Synthetic journal failure'")
    try:
        expect(x.req('CreateFolder',[('destinationID',folder),('newFolderName','Rollback me')])[0],500)
        expect(sql("SELECT COUNT(*) FROM Folder WHERE owner_id=(SELECT user_id FROM User WHERE username='"+u+"') AND folder_name='Rollback me'"),'0');expect(x.get('GetVersionHistory')['revision'],before['revision'])
        expect(x.req('UploadFiles',[('destinationID',folder)],[('Rollback.txt',b'rollback bytes','text/plain')])[0],500)
        expect(x.get('GetVersionHistory')['storageUsed'],before['storageUsed']);expect(x.docs(),[])
    finally:sql('DROP TRIGGER audit_reject_journal')
check('folder, file bytes and journal roll back together on SQL failure',mutation_rollback)
def expired_undo():
    h=x.get('GetVersionHistory');sql("UPDATE WorkspaceAction SET expires_at=DATE_SUB(NOW(), INTERVAL 1 SECOND) WHERE owner_id=(SELECT user_id FROM User WHERE username='"+x.user+"')")
    expect(x.req('UndoActions',[('actionID',h['actions'][0]['id']),('revision',h['revision'])])[0],409)
check('expired snapshots cannot be undone',expired_undo)
# Keep expiry test deterministic with direct DB timestamp manipulation; no five-minute sleep.
print(json.dumps(ns['results'],indent=2));sys.exit(any(r['status']=='FAIL' for r in ns['results']))
