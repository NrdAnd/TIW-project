/* Run from the repository with Playwright installed. Disposable loopback only.
TIW_REPO_ROOT=/path/to/repo TIW_LIVE_URL=http://127.0.0.1:8092/document-manager TIW_ALLOW_TEMPORARY_DATA=1 TIW_BROWSER_CHANNEL=chrome node /path/to/audit-browser.cjs
*/
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const {createRequire} = require('node:module');
const {chromium} = createRequire(path.join(process.env.TIW_REPO_ROOT || process.cwd(), 'package.json'))('playwright');
const base=process.env.TIW_LIVE_URL;
if(!base || !['localhost','127.0.0.1'].includes(new URL(base).hostname) || process.env.TIW_ALLOW_TEMPORARY_DATA!=='1')throw Error('Disposable loopback installation only');
const results=[];let browser;
const expectStatus=async r=>{assert.equal(r.status(),200, await r.text());return r.json()};
const idle=page=>page.waitForFunction(()=>document.querySelector('#treeContainer')?.getAttribute('aria-busy')==='false' && !document.querySelector('#EditButton').disabled);
const header=(p,n)=>p.locator('.folder-header').filter({has:p.locator('.folder-name',{hasText:new RegExp('^'+n+'$')})});
async function fixture(){
 const context=await browser.newContext({viewport:{width:1440,height:1040}}), page=await context.newPage();page.setDefaultTimeout(4000);
 const user='b'+Date.now()+Math.floor(Math.random()*1000),password='BrowserPassword_7';
 await expectStatus(await context.request.post(base+'/CheckSignupCredentials',{multipart:{username:user,email:user+'@test.invalid',password,passwordCheck:password}}));
 const cfg=await expectStatus(await context.request.get(base+'/SessionToken'));const tree=await expectStatus(await context.request.get(base+'/GetTree'));
 const post=async(e,fields)=>expectStatus(await context.request.post(base+'/'+e,{headers:{'X-CSRF-Token':cfg.csrfToken},multipart:fields}));
 await post('CreateFolder',{destinationID:String(tree.folder.folderID),newFolderName:'Alpha'});
 await post('CreateFolder',{destinationID:String(tree.folder.folderID),newFolderName:'Beta'});
 let t=await expectStatus(await context.request.get(base+'/GetTree'));const f=t.children[0].folder.folderID;
 await post('UploadFiles',{destinationID:String(f),files:{name:'File.txt',mimeType:'text/plain',buffer:Buffer.from('Browser audit bytes')}});
 await page.goto(base+'/index.html');await page.evaluate(u=>sessionStorage.setItem('utente',u),user);
 return {context,page,user,password,post,root:tree.folder.folderID,folder:f};
}
async function check(name,fn){let f;try{f=await fixture();await fn(f);results.push({name,status:'PASS'});}catch(e){results.push({name,status:'FAIL',detail:e.message.split('\n')[0]});}finally{if(f)await f.context.close();}console.log(JSON.stringify(results.at(-1)));}
async function open(f){await f.page.goto(base+'/homepage.html');await idle(f.page);}
async function mutate(page,endpoint,fn){const r=page.waitForResponse(r=>r.url().endsWith('/'+endpoint)&&r.request().method()==='POST');await fn();assert.equal((await r).status(),200);await idle(page);}
(async()=>{
 browser=await chromium.launch({headless:true,...(process.env.TIW_BROWSER_CHANNEL ? {channel:process.env.TIW_BROWSER_CHANNEL} : {})});
 await check('live navigation, guide, search and cancel buttons',async f=>{
  await open(f);const p=f.page;await p.locator('#guideButton').click();assert.equal(await p.locator('#guideDialog').evaluate(e=>e.open),true);await p.locator('#closeGuide').click();
  await p.locator('#searchInput').fill('no-result');assert.equal(await p.locator('#searchEmpty').isVisible(),true);await p.locator('#overviewButton').click();assert.equal(await p.locator('#searchInput').inputValue(),'');
  await p.locator('#RootButton').click();await p.getByRole('button',{name:'Cancel',exact:true}).click();await p.locator('#UploadButton').click();await p.getByRole('button',{name:'Cancel',exact:true}).click();await p.locator('#activityButton').click();assert.equal(await p.locator('.undo-button').count(),3);
 });
 await check('live nested folder create, rename and button move',async f=>{
  await open(f);const p=f.page;await p.locator('#EditButton').click();let card=header(p,'Alpha').locator('..');await card.getByRole('button',{name:'+ Folder',exact:true}).click();await p.locator('#folderName').fill('Nested');await mutate(p,'CreateFolder',()=>p.getByRole('button',{name:'Create folder',exact:true}).click());
  card=header(p,'Nested').locator('..');await card.getByRole('button',{name:'Rename',exact:true}).click();await p.locator('#renameInput').fill('Renamed');await mutate(p,'RenameItem',()=>p.getByRole('button',{name:'Save name'}).click());
  await header(p,'Renamed').locator('..').getByRole('button',{name:'Move',exact:true}).click();await p.locator('#moveDestination').selectOption({label:'Beta'});await mutate(p,'MoveFolder',()=>p.getByRole('button',{name:'Move here'}).click());assert.match(await header(p,'Beta').locator('..').innerText(),/Renamed/);
 });
 await check('live file button move, cancellation and deletion with undo',async f=>{
  await open(f);const p=f.page;await p.getByRole('button',{name:'Details for File.txt'}).click();await p.getByRole('button',{name:'Move file',exact:true}).click();await p.locator('#moveDestination').selectOption({label:'Beta'});await mutate(p,'MoveDocument',()=>p.getByRole('button',{name:'Move here'}).click());
  await p.getByRole('button',{name:'Details for File.txt'}).click();p.once('dialog',d=>d.dismiss());await p.locator('#rightContainer').getByRole('button',{name:'Delete',exact:true}).click();assert.equal(await p.getByRole('button',{name:'Details for File.txt'}).count(),1);
  p.once('dialog',d=>d.accept());await mutate(p,'DeleteDocument',()=>p.locator('#rightContainer').getByRole('button',{name:'Delete',exact:true}).click());p.once('dialog',d=>d.accept());await mutate(p,'UndoActions',()=>p.locator('.undo-button').first().click());assert.equal(await p.getByRole('button',{name:'Details for File.txt'}).count(),1);
 });
 await check('live recursive folder delete and undo buttons',async f=>{
  await open(f);const p=f.page;await p.locator('#EditButton').click();p.once('dialog',d=>d.accept());await mutate(p,'DeleteFolder',()=>header(p,'Alpha').locator('..').getByRole('button',{name:'Delete',exact:true}).click());assert.equal(await p.getByRole('button',{name:'Details for File.txt'}).count(),0);p.once('dialog',d=>d.accept());await mutate(p,'UndoActions',()=>p.locator('.undo-button').first().click());assert.equal(await p.getByRole('button',{name:'Details for File.txt'}).count(),1);
 });
 await check('double submit makes only one folder',async f=>{
  await open(f);const p=f.page;let calls=0;p.on('request',r=>{if(r.url().endsWith('/CreateFolder'))calls++});await p.locator('#RootButton').click();await p.locator('#folderName').fill('Double');await p.locator('#createFolder').evaluate(e=>{e.requestSubmit();e.requestSubmit()});await p.waitForFunction(()=>document.querySelector('#statusMessage').textContent==='Folder created.');await idle(p);assert.equal(calls,1);
 });
 await check('failed mutation keeps form and reenables submit',async f=>{
  await open(f);const p=f.page;await p.route('**/CreateFolder',r=>r.abort('failed'));await p.locator('#RootButton').click();await p.locator('#folderName').fill('Retained');await p.getByRole('button',{name:'Create folder',exact:true}).click();await p.waitForFunction(()=>document.querySelector('#statusMessage').textContent.includes('server could not be reached'));assert.equal(await p.locator('#folderName').inputValue(),'Retained');assert.equal(await p.getByRole('button',{name:'Create folder',exact:true}).isEnabled(),true);
 });
 await check('stale file detail response cannot replace a newer form',async f=>{
  await open(f);const p=f.page;let release;const gate=new Promise(r=>release=r);await p.route('**/GetDocument?*',async r=>{await gate;await r.continue()});await p.getByRole('button',{name:'Details for File.txt'}).click();await p.locator('#RootButton').click();release();await p.waitForResponse(r=>r.url().includes('/GetDocument?'));await p.waitForTimeout(100);assert.equal(await p.locator('#folderName').isVisible(),true);
 });
 await check('new tab resumes valid server session',async f=>{
  await open(f);const second=await f.context.newPage();await second.goto(base+'/homepage.html');await second.waitForLoadState('networkidle');assert.equal(new URL(second.url()).pathname,new URL(base+'/homepage.html').pathname,'new tab was redirected to login despite valid session');
 });
 await check('SessionToken failure clears loading and offers retry',async f=>{
  const p=f.page;await p.route('**/SessionToken',r=>r.fulfill({status:503,contentType:'application/json',body:JSON.stringify({error:{message:'Temporary test outage'}})}));await p.goto(base+'/homepage.html');await p.waitForFunction(()=>document.querySelector('#statusMessage').textContent.includes('Temporary test outage'));assert.equal(await p.locator('#treeContainer').getAttribute('aria-busy'),'false','workspace remains permanently loading after failed startup');assert.equal(await p.locator('#RetryButton').isVisible(),true);await p.unroute('**/SessionToken');await p.locator('#RetryButton').click();await idle(p);assert.equal(await p.locator('#RetryButton').isVisible(),false);
 });
 await check('workspace buttons are disabled or actionable after tree failure',async f=>{
  const p=f.page;await p.route('**/GetTree',r=>r.fulfill({status:503,body:'Temporary test outage'}));await p.goto(base+'/homepage.html');await p.locator('#treeError').waitFor();assert.equal(await p.locator('#RootButton').isDisabled(),true);await p.unroute('**/GetTree');await p.locator('#RetryButton').click();await idle(p);await p.locator('#RootButton').click();assert.equal(await p.locator('#folderName').isVisible(),true);
 });
 await check('logout works after SessionToken bootstrap request fails once',async f=>{
  const p=f.page;let count=0;await p.route('**/SessionToken',r=>++count===1?r.fulfill({status:503,body:'Temporary test outage'}):r.continue());await p.goto(base+'/homepage.html');await p.locator('#statusMessage').waitFor();await p.locator('#Logout').click();await p.waitForLoadState('networkidle');assert.equal(new URL(p.url()).pathname,new URL(base+'/index.html').pathname,'logout rejected because bootstrap left the CSRF token empty');
 });
 await check('valid JSON with invalid tree shape fails visibly without exception',async f=>{
  const p=f.page,errors=[];p.on('pageerror',e=>errors.push(e.message));await p.route('**/GetTree',r=>r.fulfill({status:200,contentType:'application/json',body:'{}'}));await p.goto(base+'/homepage.html');await p.waitForLoadState('networkidle');assert.equal(await p.locator('#treeContainer').getAttribute('aria-busy'),'false','invalid tree leaves aria-busy=true');assert.deepEqual(errors,[]);assert.equal(await p.locator('#treeError').isVisible(),true);await p.unroute('**/GetTree');await p.locator('#RetryButton').click();await idle(p);
 });
 await check('mobile folder actions and file details remain usable',async f=>{
  await f.page.setViewportSize({width:390,height:844});await open(f);const p=f.page;await p.locator('#EditButton').click();await header(p,'Beta').locator('..').getByRole('button',{name:'Rename',exact:true}).click();await p.locator('#renameInput').fill('Mobile');await mutate(p,'RenameItem',()=>p.getByRole('button',{name:'Save name'}).click());await p.getByRole('button',{name:'Details for File.txt'}).click();await p.locator('.document-details').waitFor();assert.equal(await p.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false);
 });
 await check('empty workspace upload directs to create folder',async f=>{
  let t=await expectStatus(await f.context.request.get(base+'/GetTree'));for(const branch of t.children)await f.post('DeleteFolder',{folderID:String(branch.folder.folderID)});await open(f);await f.page.locator('#UploadButton').click();await f.page.getByRole('button',{name:'Create root folder',exact:true}).click();assert.equal(await f.page.locator('#folderName').isVisible(),true);
 });
 await check('history failure remains visible and retry restores activity',async f=>{
  const p=f.page;await p.route('**/GetVersionHistory',r=>r.fulfill({status:200,contentType:'application/json',body:'{}'}));await p.goto(base+'/homepage.html');await idle(p);await p.locator('#activityButton').click();assert.match(await p.locator('#rightContainer').innerText(),/invalid activity data/);assert.equal(await p.locator('.undo-button').count(),0);await p.unroute('**/GetVersionHistory');await p.locator('#RetryButton').click();await idle(p);assert.equal(await p.locator('.undo-button').count(),3);
 });
 await check('redundant login preserves first tab and its undo history',async f=>{
  await open(f);const before=await expectStatus(await f.context.request.get(base+'/GetVersionHistory'));const response=await f.context.request.post(base+'/CheckLoginCredentials',{multipart:{username:f.user,password:f.password}});assert.equal(response.status(),409);await f.page.reload();await idle(f.page);const after=await expectStatus(await f.context.request.get(base+'/GetVersionHistory'));assert.deepEqual(after.actions,before.actions);
 });
 const summary={passed:results.filter(r=>r.status==='PASS').length,failed:results.filter(r=>r.status==='FAIL').length,results};console.log(JSON.stringify({passed:summary.passed,failed:summary.failed}));if(process.env.TIW_AUDIT_JSON)fs.writeFileSync(process.env.TIW_AUDIT_JSON,JSON.stringify(summary,null,2)+'\n');process.exitCode=summary.failed?1:0;
})().catch(e=>{console.error(e);process.exitCode=1}).finally(async()=>{if(browser)await browser.close()});
