import assert from 'node:assert/strict'
import { chromium } from 'playwright'
import { execFileSync } from 'node:child_process'
assert.equal(process.env.HUCE_BROWSER_CI,'true')
const base=process.env.HUCE_TEST_URL
assert.ok(base&&['localhost','127.0.0.1'].includes(new URL(base).hostname))
assert.equal((await fetch(base+'/api/auth/mode').then(r=>r.json())).demo,true)
const compose=(...args)=>execFileSync('docker',['compose','-f','compose.share.yaml',...args],{cwd:new URL('../../',import.meta.url),stdio:'inherit',timeout:90000})
const browser=await chromium.launch({headless:true})
const page=await browser.newPage({viewport:{width:1440,height:900}})
const errors=[]
page.on('pageerror',e=>errors.push(e.message))
async function ready(){
 const end=Date.now()+90000
 while(Date.now()<end){
  try{const r=await fetch(base+'/health',{signal:AbortSignal.timeout(2500)});if(r.ok)return}catch{}
  await new Promise(r=>setTimeout(r,1000))
 }
 throw Error('Backend did not recover')
}
try{
 await page.goto(base)
 await page.locator('.demo-choice').filter({hasText:'Nhân viên đào tạo'}).click()
 await page.locator('.workspace').waitFor()
 await page.locator('#workspace-navigation a[href="#sections"]').click()
 await page.getByText('CSDL.01',{exact:true}).waitFor()
 // Simulated transport failure for existing authenticated browser session.
 const offline=route=>route.abort('failed')
 await page.route('**/api/me',offline)
 await page.reload()
 await page.getByRole('button',{name:'Thử lại kết nối'}).waitFor()
 assert.equal(await page.locator('#login-title').count(),0)
 await page.unroute('**/api/me',offline)
 await page.getByRole('button',{name:'Thử lại kết nối'}).click()
 await page.getByText('CSDL.01',{exact:true}).waitFor()
 await page.route('**/api/catalog',offline)
 await page.reload()
 await page.getByRole('button',{name:'Thử tải lại dữ liệu'}).waitFor()
 await page.unroute('**/api/catalog',offline)
 await page.getByRole('button',{name:'Thử tải lại dữ liệu'}).click()
 await page.getByText('CSDL.01',{exact:true}).waitFor()
 // Real database restart inside disposable Compose project.
 compose('stop','postgres')
 try{
  let failed=false
  try{const r=await fetch(base+'/health',{signal:AbortSignal.timeout(5000)});failed=!r.ok}catch{failed=true}
  assert.equal(failed,true,'Health must not claim readiness while PostgreSQL is stopped')
 }finally{compose('start','postgres')}
 await ready()
 // Restart Java as well: session and domain data live in PostgreSQL.
 compose('restart','app')
 await ready()
 await page.reload()
 await page.getByText('CSDL.01',{exact:true}).waitFor()
 assert.equal(await page.locator('.identity strong').innerText(),'daotao')
 assert.equal(await page.locator('.content .error').count(),0)
 assert.deepEqual(errors,[])
 console.log('PASS: browser transport failure recovery, actual PostgreSQL and Java restart, persisted session and data')
}finally{await browser.close()}
