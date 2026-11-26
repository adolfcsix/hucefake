import assert from 'node:assert/strict'
import { chromium } from 'playwright'
import { mkdir,readFile } from 'node:fs/promises'
assert.equal(process.env.HUCE_BROWSER_CI,'true')
const base=process.env.HUCE_TEST_URL
assert.ok(base&&['localhost','127.0.0.1'].includes(new URL(base).hostname))
assert.equal((await fetch(base+'/api/auth/mode').then(r=>r.json())).demo,true)
const browser=await chromium.launch({headless:true})
const failures=[]
const dir=new URL('../e2e-results/',import.meta.url)
await mkdir(dir,{recursive:true})
const button=(root,name)=>root.getByRole('button',{name,exact:true})
const dialog=p=>p.getByRole('dialog')
async function nav(p,key){
 await p.locator('#workspace-navigation a[href="#'+key+'"]').click()
}
async function open(role,key){
 const c=await browser.newContext({viewport:{width:1440,height:900},acceptDownloads:true})
 const p=await c.newPage()
 p.on('pageerror',e=>failures.push(e.message))
 p.on('response',r=>{if(r.url().includes('/api/')&&r.status()>=400&&!(r.status()===401&&r.url().endsWith('/api/me')))failures.push(r.status()+' '+r.url())})
 await p.goto(base)
 await p.locator('.demo-choice').filter({hasText:role}).click()
 await p.locator('.workspace').waitFor()
 await nav(p,key)
 return p
}
async function choose(select,pattern){
 const option=select.locator('option').filter({hasText:pattern}).first()
 await option.waitFor({state:'attached'})
 const id=await option.getAttribute('value')
 assert.ok(id)
 await select.selectOption(id)
}
async function enabled(b,timeout=30000){
 const start=Date.now()
 while(Date.now()-start<timeout){
  if(await b.count()===1&&await b.isEnabled())return
  await new Promise(r=>setTimeout(r,200))
 }
 throw new Error('Button did not become enabled: '+await b.count())
}
async function mutate(page,btn,method,path,status){
 const reply=page.waitForResponse(r=>r.request().method()===method&&new URL(r.url()).pathname===path)
 await btn.click()
 const response=await reply
 assert.equal(response.status(),status,await response.text())
}
async function readDownload(page,name){
 const pending=page.waitForEvent('download')
 await button(page,name).click()
 const download=await pending
 assert.equal(await download.failure(),null)
 return readFile(await download.path(),'utf8')
}
try{
 const staff=await open('Nhân viên đào tạo','planning')
 await staff.getByRole('heading',{name:'Lịch định kỳ'}).waitFor()
 await choose(staff.getByLabel('Buổi chưa xếp'),/^CSDL\.01/)
 await button(staff,'Xếp buổi học').click()
 const form=dialog(staff)
 await choose(form.getByLabel('Phòng',{exact:true}),/^M301/)
 await form.getByLabel('Thứ',{exact:true}).selectOption('1')
 await form.getByLabel(/Tiết bắt đầu/).selectOption('1')
 await form.getByText('Khung đã chọn không có xung đột.',{exact:true}).waitFor()
 await mutate(staff,button(form,'Lưu lịch'),'POST','/api/schedule-patterns',201)
 await form.waitFor({state:'hidden'})
 await staff.locator('.schedule-card').filter({hasText:'CSDL.01'}).waitFor()
 await nav(staff,'automatic')
 await staff.getByLabel('Kích thước quần thể').fill('24')
 await staff.getByLabel('Số thế hệ tối đa').fill('80')
 await staff.getByLabel('Xác suất đột biến').fill('0.12')
 await staff.getByLabel('Hạt giống').fill('2026')
 await enabled(button(staff,'Chạy xếp lịch'))
 await mutate(staff,button(staff,'Chạy xếp lịch'),'POST','/api/schedule-runs',202)
 const apply=button(staff,'Áp dụng phương án')
 await enabled(apply,120000)
 assert.equal(await staff.locator('.metrics').filter({hasText:'Vi phạm cứng'}).locator('div').filter({hasText:'Vi phạm cứng'}).locator('strong').innerText(),'0')
 const applyResponse=staff.waitForResponse(r=>r.request().method()==='POST'&&/\/api\/schedule-runs\/\d+\/apply$/.test(new URL(r.url()).pathname))
 await apply.click()
 assert.equal((await applyResponse).status(),200)
 await staff.getByText('Đã áp dụng',{exact:true}).first().waitFor()
 await nav(staff,'approvals')
 const submit=button(staff,'Gửi duyệt thời khóa biểu')
 await enabled(submit)
 await submit.click()
 await staff.getByRole('row').filter({hasText:'Chờ duyệt'}).waitFor()
 const manager=await open('Quản lý','approvals')
 await button(manager.getByRole('row').filter({hasText:'Chờ duyệt'}),'Xem phương án').click()
 await button(manager,'Trả lại').click()
 await dialog(manager).getByLabel('Lý do trả lại').fill('Kiểm thử trả lại để xác minh quy trình')
 await button(dialog(manager),'Trả lại').click()
 await dialog(manager).waitFor({state:'hidden'})
 await manager.getByText('Kiểm thử trả lại để xác minh quy trình',{exact:true}).waitFor()
 await staff.reload()
 await enabled(button(staff,'Gửi duyệt thời khóa biểu'))
 await button(staff,'Gửi duyệt thời khóa biểu').click()
 await staff.getByRole('row').filter({hasText:'Chờ duyệt'}).waitFor()
 await manager.reload()
 await button(manager.getByRole('row').filter({hasText:'Chờ duyệt'}),'Xem phương án').click()
 await button(manager,'Duyệt và công bố').click()
 await manager.getByRole('row').filter({hasText:'Đã công bố'}).waitFor()
 await manager.screenshot({path:new URL('published-approval.png',dir).pathname,fullPage:true})
 const lecturer=await open('Giảng viên','schedule')
 const student=await open('Sinh viên','schedule')
 await nav(staff,'schedule');await nav(manager,'schedule')
 for(const p of [staff,manager,lecturer,student]){
  await p.getByLabel('Phạm vi').selectOption('semester')
  await p.getByRole('row').filter({has:p.getByText('CSDL.01',{exact:true})}).first().waitFor()
  assert.equal(await p.locator('.content .error').count(),0)
 }
 for(const p of [lecturer,student]){
  assert.equal(await p.getByText('MANG.01',{exact:true}).count(),0)
  const csv=await readDownload(p,'Xuất CSV')
  assert.ok(csv.includes('CSDL.01')&&csv.includes('KTPM.01'))
  assert.ok(!csv.includes('MANG.01'))
  const ics=await readDownload(p,'Tải lịch .ics')
  assert.ok(ics.includes('BEGIN:VCALENDAR')&&ics.includes('BEGIN:VEVENT'))
  assert.ok(ics.includes('CSDL.01')&&!ics.includes('MANG.01'))
 }
 await student.screenshot({path:new URL('published-student.png',dir).pathname,fullPage:true})
 assert.deepEqual(failures,[])
 console.log('PASS: manual scheduling, GA apply, approval return/resubmit/publish, four-role browser readback and scoped CSV/ICS downloads')
}finally{await browser.close()}
