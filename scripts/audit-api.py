#!/usr/bin/env python3
"""Regression audit. ONLY a disposable loopback Tomcat/MySQL installation.
Creates synthetic accounts/files. Exits nonzero for failed expectations.
Optional --mysql-defaults points to an ADMIN client config for this disposable DB.
"""
import argparse, base64, hashlib, http.cookiejar, json, secrets, subprocess, sys
import urllib.request, urllib.error, urllib.parse
from pathlib import Path
p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--url',required=True);p.add_argument('--allow-temporary-data',action='store_true',required=True)
p.add_argument('--mysql-defaults');p.add_argument('--mysql-bin',default='mysql');p.add_argument('--json-output')
a=p.parse_args(); base=a.url.rstrip('/')
if urllib.parse.urlsplit(base).hostname not in ('localhost','127.0.0.1'):p.error('Disposable loopback only')
results=[]
def check(name,fn):
    try:fn(); result=dict(name=name,status='PASS')
    except Exception as e:result=dict(name=name,status='FAIL',detail=str(e))
    results.append(result); print(result['status']+' '+name+(': '+result.get('detail','') if result['status']=='FAIL' else ''),flush=True)
def expect(actual,expected):
    assert actual==expected, f'expected {expected!r}, got {actual!r}'
class Client:
    def __init__(self):
        self.cookies=http.cookiejar.CookieJar();self.opener=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(self.cookies));self.csrf=''
    def req(self,endpoint,fields=None,files=None,method=None,token=None,headers=None):
        data=None; h=dict(headers or {})
        if fields is not None or files is not None:
            boundary='audit'+secrets.token_hex(8); parts=[]
            for k,v in fields or []:parts.append(f'--{boundary}\r\nContent-Disposition: form-data; name="{k}"\r\n\r\n{v}\r\n'.encode())
            for n,b,m in files or []:parts.extend([f'--{boundary}\r\nContent-Disposition: form-data; name="files"; filename="{n}"\r\nContent-Type: {m}\r\n\r\n'.encode(),b,b'\r\n'])
            data=b''.join(parts)+f'--{boundary}--\r\n'.encode();h['Content-Type']='multipart/form-data; boundary='+boundary
            h['X-CSRF-Token']=self.csrf if token is None else token
        r=urllib.request.Request(base+'/'+endpoint,data=data,headers=h,method=method)
        try:
            with self.opener.open(r,timeout=30) as x:return x.status,x.read(),x.headers
        except urllib.error.HTTPError as e:return e.code,e.read(),e.headers
    def ok(self,e,f=None,files=None):
        s,b,h=self.req(e,f,files);assert s==200,f'{e}: status {s}';return b
    def get(self,e):return json.loads(self.ok(e))
    def signup(self,password='AuditPassword_7'):
        self.user='a'+secrets.token_hex(5);self.password=password
        self.ok('CheckSignupCredentials',[('username',self.user),('email',self.user+'@test.invalid'),('password',password),('passwordCheck',password)])
        self.csrf=self.get('SessionToken')['csrfToken']; self.root=self.get('GetTree')['folder']['folderID'];return self
    def login(self,user,password):return self.req('CheckLoginCredentials',[('username',user),('password',password)])[0]
    def create(self,parent,name):
        self.ok('CreateFolder',[('destinationID',parent),('newFolderName',name)])
        return next(f['folder']['folderID'] for f in flat(self.get('GetTree')) if f['folder']['folderName']==name)
    def docs(self):return [d for f in flat(self.get('GetTree')) for d in f['documentList']]
def flat(n):
    yield n
    for child in n['children']:yield from flat(child)
def sql(q):
    r=subprocess.run([a.mysql_bin,'--defaults-file='+str(Path(a.mysql_defaults).resolve()),'--batch','--skip-column-names','tiw_document_manager','-e',q],capture_output=True,text=True)
    assert r.returncode==0,'test database query failed';return r.stdout.strip()
reads=['SessionToken','GetTree','GetDocument','GetVersionHistory','DownloadFile','PreviewFile']
writes=['CreateFolder','CreateDocument','MoveDocument','MoveFolder','DeleteDocument','DeleteFolder','RenameItem','UploadFiles','UndoActions','Logout']
anon=Client()
for e in reads+writes:
    check('anonymous '+e+' returns 401',lambda e=e:expect(anon.req(e)[0],401))
c=Client().signup(); folder=c.create(c.root,'Audit folder'); other=c.create(c.root,'Destination')
for e in reads:
    check(e+' rejects POST',lambda e=e:expect(c.req(e,[])[0],405))
for e in writes:
    check(e+' rejects GET',lambda e=e:expect(c.req(e)[0],405))
for e in writes:
    check(e+' rejects incorrect CSRF',lambda e=e:expect(c.req(e,[],token='bad')[0],403))
for e in ['CheckLoginCredentials','CheckSignupCredentials']:
    check(e+' rejects GET',lambda e=e:expect(anon.req(e)[0],405))
    check(e+' missing fields returns 400',lambda e=e:expect(anon.req(e,[])[0],400))
check('unknown route returns safe JSON',lambda:expect((lambda r:(r[0],json.loads(r[1])['error']['code']))(anon.req('DoesNotExist')),(404,'NOT_FOUND')))
check('WEB-INF inaccessible',lambda:expect(anon.req('WEB-INF/web.xml')[0],404))
check('API cache and sniffing headers',lambda:expect((lambda r:(r[2]['Cache-Control'],r[2]['X-Content-Type-Options']))(c.req('GetTree')),('no-store','nosniff')))
for value in ['0','-1','NaN','2147483648','']:
    check('invalid documentID '+repr(value),lambda value=value:expect(c.req('GetDocument?documentID='+value)[0],400))
for name in ['../bad','.', '..','bad/name','bad\\name','bad\x00name','x'*101]:
    check('invalid folder name '+repr(name[:20]),lambda name=name:expect(c.req('CreateFolder',[('destinationID',c.root),('newFolderName',name)])[0],400))
check('root cannot be renamed',lambda:expect(c.req('RenameItem',[('itemType','folder'),('itemID',c.root),('name','bad')])[0],400))
check('root cannot be moved',lambda:expect(c.req('MoveFolder',[('folderID',c.root),('destinationID',folder)])[0],400))
check('empty upload rejected',lambda:expect(c.req('UploadFiles',[('destinationID',folder)],[])[0],400))
check('zero-byte file persists and downloads',lambda:(c.ok('UploadFiles',[('destinationID',folder)],[('empty.txt',b'','text/plain')]),expect(c.ok('DownloadFile?documentID='+str(c.docs()[-1]['documentID'])),b'')))
check('metadata document creation',lambda:c.ok('CreateDocument',[('destinationID',folder),('docName','Legacy'),('docFormat','txt'),('docSummary','A summary')]))
legacy=next(d for d in c.docs() if d['documentName']=='Legacy')
check('metadata without blob returns 404 on download',lambda:expect(c.req('DownloadFile?documentID='+str(legacy['documentID']))[0],404))
check('case-only folder rename works',lambda:c.ok('RenameItem',[('itemType','folder'),('itemID',folder),('name','AUDIT FOLDER')]))
check('duplicate folder rejected',lambda:expect(c.req('CreateFolder',[('destinationID',c.root),('newFolderName','audit folder')])[0],409))
check('duplicate multipart fields rejected',lambda:expect(c.req('CreateFolder',[('destinationID',folder),('destinationID',other),('newFolderName','Dup')])[0],400))
check('query plus multipart duplicate fields rejected',lambda:expect(c.req('CreateFolder?destinationID='+str(other),[('destinationID',folder),('newFolderName','Query pollution')])[0],400))
# Isolated login assertions, without logging the synthetic password.
check('wrong password rejected',lambda:expect(Client().login(c.user,'wrong'),401))
check('password case matters',lambda:expect(Client().login(c.user,c.password.lower()),401))
check('password trailing spaces matter',lambda:expect(Client().login(c.user,c.password+' '),401))
for endpoint in ['CheckLoginCredentials','CheckSignupCredentials']:
    check(endpoint+' rejects query/form ambiguity',lambda endpoint=endpoint:expect(Client().req(endpoint+'?username=other',[('username','test'),('password','test')])[0],400))
check('exact password accepted',lambda:expect(Client().login(c.user,c.password),200))
def authenticated_post():
    x=Client().signup();s=x.req('CheckLoginCredentials',[])[0]
    expect(x.req('GetTree')[0],200)
check('invalid login POST does not destroy an existing session',authenticated_post)
def bad_email():
    x=Client();u='e'+secrets.token_hex(5)
    expect(x.req('CheckSignupCredentials',[('username',u),('email','a b@c.d'),('password','Password_7'),('passwordCheck','Password_7')])[0],400)
check('server rejects whitespace inside email',bad_email)
def depth():
    x=Client().signup();parent=x.root
    for i in range(40):parent=x.create(parent,'Level'+str(i))
    expect(x.req('CreateFolder',[('destinationID',parent),('newFolderName','Too deep')])[0],400)
check('maximum depth 40 enforced',depth)
def boundary():
    x=Client().signup();f=x.create(x.root,'Limits');payload=b'x'*(25*1024*1024)
    x.ok('UploadFiles',[('destinationID',f)],[('max.bin',payload,'application/octet-stream')])
    d=x.docs()[0];expect(d['size'],len(payload));expect(hashlib.sha256(x.ok('DownloadFile?documentID='+str(d['documentID']))).hexdigest(),hashlib.sha256(payload).hexdigest())
check('exact 25 MiB file survives upload and download',boundary)
def files20():
    x=Client().signup();f=x.create(x.root,'Batch')
    x.ok('UploadFiles',[('destinationID',f)],[(str(i)+'.txt',b'x','text/plain') for i in range(20)]);expect(len(x.docs()),20)
check('exact 20-file batch accepted',files20)
if a.mysql_defaults:
    check('password is not stored as plaintext',lambda:expect(sql("SELECT password='AuditPassword_7' FROM User WHERE username='"+c.user+"'"),'0'))
    def expired():
        x=Client().signup();f=x.create(x.root,'Expiry');x.ok('UploadFiles',[('destinationID',f)],[('expiry.txt',b'abc','text/plain')]);d=x.docs()[0];x.ok('DeleteDocument',[('documentID',d['documentID'])])
        sql("UPDATE WorkspaceAction SET expires_at=DATE_SUB(NOW(), INTERVAL 1 SECOND) WHERE owner_id=(SELECT user_id FROM User WHERE username='"+x.user+"')")
        h=x.get('GetVersionHistory');expect(any(e['undoable'] for e in h['actions']),False);expect(h['storageUsed'],0)
    check('expired undo releases deleted bytes on workspace refresh',expired)
    def quota():
        x=Client().signup();f=x.create(x.root,'Quota');block=b'q'*(25*1024*1024)
        for batch in range(3):x.ok('UploadFiles',[('destinationID',f)],[(f'b{batch}-{i}.bin',block,'application/octet-stream') for i in range(4 if batch<2 else 2)])
        expect(x.get('GetVersionHistory')['storageUsed'],250*1024*1024)
        expect(x.req('UploadFiles',[('destinationID',f)],[('overflow.txt',b'x','text/plain')])[0],413);expect(len(x.docs()),10)
    check('100 MiB batch and exact 250 MiB quota; overflow rolls back',quota)
summary=dict(results=results,passed=sum(r['status']=='PASS' for r in results),failed=sum(r['status']=='FAIL' for r in results))
print(json.dumps({k:v for k,v in summary.items() if k!='results'}))
if a.json_output:Path(a.json_output).write_text(json.dumps(summary,indent=2)+'\n')
sys.exit(bool(summary['failed']))
