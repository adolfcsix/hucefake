import assert from 'node:assert/strict'
import { chromium } from 'playwright'
import { mkdir } from 'node:fs/promises'

// Only run against an isolated demo database, never a real installation.
assert.equal(process.env.HUCE_BROWSER_CI,'true')
const base=process.env.HUCE_TEST_URL
assert.ok(base && ['localhost','127.0.0.1'].includes(new URL(base).hostname))
const mode=await fetch(base+'/api/auth/mode').then(r=>r.json())
assert.equal(mode.demo,true)
const browser=await chromium.launch({headless:true})
const dir=new URL('../e2e-results/',import.meta.url)
await mkdir(dir,{recursive:true})
const code='QA.UI.01'
const failures=[]
let expectedConflict=0
const row=page=>page.getByRole('row').filter({has:page.getByText(code,{exact:true})})
const dialog=page=>page.getByRole('dialog')
const button=(root,name)=>root.getByRole('button',{name,exact:true})
async function open(role){
 const context=await browser.newContext({viewport:{width:1440,height:900}})
 const page=await context.newPage()
 page.on('pageerror',e=>failures.push(e.message))
 page.on('response',r=>{
  if(!r.url().includes('/api/')||r.status()<400)return
  if(r.status()===401&&r.url().endsWith('/api/me'))return
  if(r.status()===409&&r.request().method()==='PATCH'&&/\/api\/course-sections\/\d+$/.test(r.url())&&expectedConflict>0){expectedConflict--;return}
  failures.push(r.status()+' '+r.url())
 })
 await page.goto(base)
 await page.locator('.demo-choice').filter({hasText:role}).click()
 await page.locator('.workspace').waitFor()
 await page.locator('#workspace-navigation a[href="#sections"]').click()
 await page.getByRole('heading',{name:'Danh sách lớp học phần'}).waitFor()
 return page
}
async function refresh(page){
 await page.reload()
 await page.getByRole('heading',{name:'Danh sách lớp học phần'}).waitFor()
}
async function save(page){
 await button(dialog(page),'Lưu').click()
 await dialog(page).waitFor({state:'hidden'})
}
async function capacity(page,value){
 await row(page).getByText(String(value),{exact:true}).waitFor()
}
try{
 const staff=await open('Nhân viên đào tạo')
 const manager=await open('Quản lý')
 assert.equal(await button(manager,'Thêm lớp học phần').count(),0)
 // Create by real form; no direct API mutation in this test.
 await button(staff,'Thêm lớp học phần').click()
 const form=dialog(staff)
 await form.getByLabel('Mã lớp',{exact:true}).fill(code)
 const subject=form.getByLabel('Học phần',{exact:true})
 const opt=await subject.locator('option').filter({hasText:/CSDL/}).first().getAttribute('value')
 assert.ok(opt)
 await subject.selectOption(opt)
 await form.getByLabel('Sĩ số tối đa').fill('30')
 await save(staff)
 await capacity(staff,30)
 await refresh(manager)
 await capacity(manager,30)
 assert.equal(await button(row(manager),'Sửa').count(),0)

 // Simulate two concurrent staff screens with the same record version.
 const staff2=await open('Nhân viên đào tạo')
 await capacity(staff2,30)
 await button(row(staff),'Sửa').click()
 await dialog(staff).getByLabel('Sĩ số tối đa').fill('35')
 await button(row(staff2),'Sửa').click()
 await dialog(staff2).getByLabel('Sĩ số tối đa').fill('32')
 await save(staff2)
 await capacity(staff2,32)
 expectedConflict=1
 await button(dialog(staff),'Lưu').click()
 await dialog(staff).getByText('Dữ liệu đang lưu · Phiên bản 1',{exact:true}).waitFor()
 assert.equal(await dialog(staff).getByLabel('Sĩ số tối đa').inputValue(),'35')
 assert.equal(await button(dialog(staff),'Lưu').isDisabled(),true)
 assert.equal(expectedConflict,0)
 await button(dialog(staff),'Dùng phiên bản mới và giữ nội dung đang nhập').click()
 await save(staff)
 await capacity(staff,35)

 await button(row(staff),'Phân công').click()
 const teacher=dialog(staff).getByLabel('Giảng viên chính',{exact:true})
 const teacherId=await teacher.locator('option').filter({hasText:/^GIANGVIEN$/}).first().getAttribute('value')
 assert.ok(teacherId)
 await teacher.selectOption(teacherId)
 await save(staff)
 await row(staff).getByText('GIANGVIEN',{exact:true}).waitFor()
 await button(row(staff),'Lớp đối tượng').click()
 const check=dialog(staff).getByRole('checkbox').first()
 await check.check()
 await save(staff)
 // Reload to check the persisted selection, not only optimistic UI state.
 await refresh(staff)
 await row(staff).waitFor()
 await button(row(staff),'Lớp đối tượng').click()
 assert.equal(await dialog(staff).getByRole('checkbox').first().isChecked(),true)
 await button(dialog(staff),'Đóng').last().click()
 await refresh(manager)
 await capacity(manager,35)
 await row(manager).getByText('GIANGVIEN',{exact:true}).waitFor()
 await staff.screenshot({path:new URL('workflow-staff.png',dir).pathname,fullPage:true})

 await button(row(staff),'Hủy lớp').click()
 await button(dialog(staff),'Hủy lớp').click()
 await dialog(staff).waitFor({state:'hidden'})
 await row(staff).getByText('Đã hủy',{exact:true}).waitFor()
 await refresh(manager)
 await row(manager).getByText('Đã hủy',{exact:true}).waitFor()
 await manager.screenshot({path:new URL('workflow-manager.png',dir).pathname,fullPage:true})
 // Search must reflect the same persisted cancelled row.
 await manager.getByPlaceholder('Mã lớp hoặc tên học phần').fill(code)
 await manager.getByText('1 lớp học phần',{exact:true}).waitFor()
 assert.equal(await manager.locator('tbody tr').count(),1)
 assert.deepEqual(failures,[])
 console.log('PASS: Vite proxy to Java, UI create/edit, concurrent version conflict resolution, assignment, group persistence, cancel and manager refresh')
}finally{await browser.close()}
